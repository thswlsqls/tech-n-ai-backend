package com.tech.n.ai.batch.graph.extract;

import com.tech.n.ai.domain.mongodb.enums.GraphNodeType;
import com.tech.n.ai.domain.mongodb.enums.GraphRelationType;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.transformer.graph.LLMGraphTransformer;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 문서 하나에서 엔티티와 관계를 뽑는다.
 *
 * LLMGraphTransformer를 만드는 유일한 자리다. 허용 타입 목록은 enum에서 만들어 넘기므로
 * 타입을 늘리거나 줄일 때 손댈 곳이 enum 한 군데뿐이다.
 */
@Component
public class GraphExtractor {

    /** 응답 JSON이 깨졌을 때 다시 물어볼 횟수 */
    private static final int MAX_ATTEMPTS = 2;

    /**
     * 프롬프트에 붙는 few-shot 예시. LLMGraphTransformer가 생성자에서 이 값을 필수로 요구하고
     * (없으면 "examples cannot be null"로 죽는다), 프롬프트 템플릿이 항상 끼워 넣는다.
     *
     * 다섯 노드 타입을 모두 한 번씩 쓴다.
     * 세 번째 예시는 "고객사가 제품을 쓴다"는 문장이다. 표본 20건을 사람이 대조했을 때 잘못된
     * 관계 7건 중 4건이 전부 이 문장 모양이었다 — 받을 타입이 없어 SUPPORTS로 밀려 들어갔고
     * 방향까지 뒤집혔다. 그래서 USES를 넣고, 회사가 출발점이라는 것까지 예시로 보여준다.
     */
    private static final String EXAMPLES = """
        Text: OpenAI released GPT-4o, a multimodal model that supports vision and function calling. \
        It succeeds GPT-4 Turbo.
        Output: [{"head": "OpenAI", "head_type": "Company", "relation": "RELEASED", "tail": "GPT-4o", \
        "tail_type": "Model"}, {"head": "GPT-4o", "head_type": "Model", "relation": "SUPPORTS", \
        "tail": "vision", "tail_type": "Capability"}, {"head": "GPT-4o", "head_type": "Model", \
        "relation": "SUCCEEDS", "tail": "GPT-4 Turbo", "tail_type": "Model"}]

        Text: Anthropic published Claude Agent SDK 0.4.0, which depends on the Messages API \
        and adds streaming tool use.
        Output: [{"head": "Anthropic", "head_type": "Company", "relation": "RELEASED", \
        "tail": "Claude Agent SDK 0.4.0", "tail_type": "Release"}, {"head": "Claude Agent SDK 0.4.0", \
        "head_type": "Release", "relation": "DEPENDS_ON", "tail": "Messages API", \
        "tail_type": "Technology"}, {"head": "Claude Agent SDK 0.4.0", "head_type": "Release", \
        "relation": "SUPPORTS", "tail": "streaming tool use", "tail_type": "Capability"}]

        Text: Zapier uses ChatGPT Work to automate reporting across its marketing team.
        Output: [{"head": "Zapier", "head_type": "Company", "relation": "USES", \
        "tail": "ChatGPT Work", "tail_type": "Technology"}, {"head": "ChatGPT Work", \
        "head_type": "Technology", "relation": "SUPPORTS", "tail": "reporting", \
        "tail_type": "Capability"}]
        """;

    private final LLMGraphTransformer transformer;

    public GraphExtractor(@Qualifier("graphExtractionChatModel") ChatModel chatModel) {
        this.transformer = LLMGraphTransformer.builder()
            .model(chatModel)
            .allowedNodes(allowedNodes())
            .allowedRelationships(allowedRelationships())
            .examples(EXAMPLES)
            .maxAttempts(MAX_ATTEMPTS)
            .build();
    }

    /**
     * 뽑은 게 없으면 빈 Optional을 준다. transform()은 응답 JSON이 비거나 노드가 하나도 없으면
     * null을 돌려주는데, 문서마다 null 검사를 흩뿌리지 않으려고 여기서 감싼다.
     */
    public Optional<GraphDocument> extract(String text) {
        return Optional.ofNullable(transformer.transform(Document.from(text)));
    }

    public static List<String> allowedNodes() {
        return Arrays.stream(GraphNodeType.values()).map(GraphNodeType::label).toList();
    }

    public static List<String> allowedRelationships() {
        return Arrays.stream(GraphRelationType.values()).map(GraphRelationType::label).toList();
    }
}
