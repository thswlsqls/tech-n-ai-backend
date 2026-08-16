package com.tech.n.ai.batch.graph.extract;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 추출 모델이 쓴 토큰을 실행 내내 누적한다.
 *
 * langchain4j의 LLMGraphTransformer는 호출당 토큰 사용량을 돌려주지 않는다. 그래서 모델에
 * 리스너로 붙여 전체 누적치를 들고 있다가, 문서를 처리하기 직전과 직후의 스냅샷 차이로
 * 그 문서가 쓴 몫을 계산한다.
 *
 * 누적은 AtomicLong이라 여러 스레드가 더해도 값이 깨지지 않는다. 다만 스냅샷 차이로 문서별
 * 몫을 가르는 방식은 한 번에 문서 하나만 처리한다는 전제 위에서만 맞는다. 이 배치는 단일 스텝
 * Tasklet이 문서를 순서대로 도는 구조라 그 전제가 성립한다.
 */
@Component
public class GraphTokenUsageRecorder implements ChatModelListener {

    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    private final AtomicLong callCount = new AtomicLong();

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        callCount.incrementAndGet();

        TokenUsage usage = responseContext.chatResponse().metadata().tokenUsage();
        if (usage == null) {
            // 모델이 사용량을 안 돌려줄 때가 있다. 호출 수만 세고 토큰은 0으로 둔다.
            return;
        }
        inputTokens.addAndGet(orZero(usage.inputTokenCount()));
        outputTokens.addAndGet(orZero(usage.outputTokenCount()));
    }

    public Snapshot snapshot() {
        return new Snapshot(inputTokens.get(), outputTokens.get(), callCount.get());
    }

    private static long orZero(Integer count) {
        return count == null ? 0L : count;
    }

    /**
     * 어느 시점까지의 누적값. 두 스냅샷의 차이가 그 사이에 쓴 몫이다.
     */
    public record Snapshot(long inputTokens, long outputTokens, long callCount) {

        public Snapshot minus(Snapshot earlier) {
            return new Snapshot(
                inputTokens - earlier.inputTokens,
                outputTokens - earlier.outputTokens,
                callCount - earlier.callCount);
        }
    }
}
