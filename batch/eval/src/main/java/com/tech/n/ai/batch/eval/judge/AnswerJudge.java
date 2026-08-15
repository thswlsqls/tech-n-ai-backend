package com.tech.n.ai.batch.eval.judge;

import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 답변 한 건을 판정 모델에게 0/1로 채점하게 한다.
 *
 * 호출이 실패하거나 응답을 읽지 못해도 예외를 밖으로 던지지 않는다. 잡이 중간에 죽으면
 * 이미 쓴 돈이 리포트로 남지 않기 때문이다. 그런 건은 parsed=false로 돌려주고 축의 분모에서 뺀다.
 * 재시도도 하지 않는다. 실패한 호출을 다시 부르면 비용이 조용히 두 배가 된다.
 */
@Slf4j
@Component
public class AnswerJudge {

    /** 채점 축 */
    public enum Axis {
        GROUNDEDNESS,
        ANSWER_RELEVANCE
    }

    private static final String GROUNDEDNESS_INSTRUCTION = """
        아래 근거 문서만을 사실 출처로 본다.
        답변의 사실 주장이 전부 문서로 뒷받침되면 1, 문서에 없는 사실을 하나라도 말하면 0.
        답변이 "문서에 없다", "확인할 수 없다", "제공되지 않았다"처럼 정보가 없다고 밝힌 부분은
        사실 주장으로 세지 않는다. 그런 문장만 있는 답변은 1이다.""";

    private static final String ANSWER_RELEVANCE_INSTRUCTION = """
        답변이 질문이 물은 것에 답했으면 1, 다른 내용이거나 답을 피했으면 0.
        사실 정확성은 보지 않는다.""";

    private static final String OUTPUT_FORMAT_INSTRUCTION =
        "결과는 {\"score\": 0 또는 1, \"reason\": \"'-다'로 끝나는 한 문장\"} 형식의 JSON 한 줄만 쓴다. 다른 말은 쓰지 않는다.";

    private static final int RESPONSE_EXCERPT_LENGTH = 200;

    // Jackson 3의 ObjectMapper는 불변이라 JsonMapper.builder()로 만든다
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final ChatModel judgeChatModel;

    public AnswerJudge(@Qualifier("judgeChatModel") ChatModel judgeChatModel) {
        this.judgeChatModel = judgeChatModel;
    }

    public JudgeVerdict judge(Axis axis, String question, String answer, List<SearchResult> evidence) {
        String prompt = buildPrompt(axis, question, answer, evidence);
        try {
            return parse(judgeChatModel.chat(prompt));
        } catch (Exception e) {
            log.warn("판정 모델 호출 실패: axis={}, message={}", axis, e.getMessage());
            return new JudgeVerdict(null, "판정 모델 호출 실패: " + e.getMessage(), false);
        }
    }

    private String buildPrompt(Axis axis, String question, String answer, List<SearchResult> evidence) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(axis == Axis.GROUNDEDNESS ? GROUNDEDNESS_INSTRUCTION : ANSWER_RELEVANCE_INSTRUCTION);
        prompt.append("\n\n");
        prompt.append(OUTPUT_FORMAT_INSTRUCTION).append("\n\n");
        prompt.append("질문: ").append(question).append("\n\n");
        prompt.append("답변:\n").append(answer).append("\n\n");
        prompt.append("근거 문서:\n");
        prompt.append(renderEvidence(evidence));
        return prompt.toString();
    }

    /**
     * 운영 답변 생성이 쓰는 PromptServiceImpl.buildPrompt와 같은 모양으로 근거 문서를 적는다.
     */
    private String renderEvidence(List<SearchResult> evidence) {
        StringBuilder rendered = new StringBuilder();
        for (int i = 0; i < evidence.size(); i++) {
            SearchResult result = evidence.get(i);
            rendered.append(String.format("[문서 %d]", i + 1));

            if (result.metadata() instanceof Document doc) {
                String title = doc.getString("title");
                String provider = doc.getString("provider");
                Object publishedAt = doc.get("published_at");
                String url = doc.getString("url");

                if (title != null) rendered.append(" ").append(title);
                if (provider != null) rendered.append(" (").append(provider).append(")");
                if (publishedAt != null) rendered.append(" [").append(publishedAt).append("]");
                if (url != null) rendered.append(" URL: ").append(url);
            }
            rendered.append("\n");
            rendered.append(result.text()).append("\n\n");
        }
        return rendered.toString();
    }

    /**
     * 판정 응답을 읽는다. 모델 없이도 확인할 수 있게 따로 뺐다.
     */
    static JudgeVerdict parse(String response) {
        if (response == null || response.isBlank()) {
            return parseFailed("판정 응답이 비어 있다", response);
        }

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(stripCodeFence(response.trim()));
        } catch (Exception e) {
            return parseFailed("판정 응답을 JSON으로 읽지 못했다", response);
        }

        JsonNode scoreNode = root.get("score");
        if (scoreNode == null || !scoreNode.isNumber()) {
            return parseFailed("판정 응답에 score가 없다", response);
        }
        int score = scoreNode.intValue();
        if (score != 0 && score != 1) {
            return parseFailed("score가 0도 1도 아니다", response);
        }

        JsonNode reasonNode = root.get("reason");
        String reason = reasonNode == null ? "" : reasonNode.asString("");
        return new JudgeVerdict(score, reason, true);
    }

    /**
     * 코드 펜스로 감싸 오는 모델이 있어 앞뒤 ```를 걷어낸다.
     */
    private static String stripCodeFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstLineEnd = text.indexOf('\n');
        String body = firstLineEnd < 0 ? "" : text.substring(firstLineEnd + 1);
        int fenceStart = body.lastIndexOf("```");
        return (fenceStart < 0 ? body : body.substring(0, fenceStart)).trim();
    }

    private static JudgeVerdict parseFailed(String reason, String response) {
        String excerpt = response == null ? "" : response.substring(0, Math.min(response.length(), RESPONSE_EXCERPT_LENGTH));
        return new JudgeVerdict(null, reason + ": " + excerpt, false);
    }
}
