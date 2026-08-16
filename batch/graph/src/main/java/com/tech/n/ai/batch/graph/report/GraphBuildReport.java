package com.tech.n.ai.batch.graph.report;

import java.util.List;
import java.util.Map;

/**
 * 그래프 구축 실행 리포트
 *
 * 키 집합은 고정이다. 값이 없어도 키를 빼지 않고 null·0·빈 배열을 넣는다.
 * 표본 실행과 전건 실행을 나란히 놓고 비교할 수 있어야 하기 때문이다.
 */
public record GraphBuildReport(
    String schemaVersion,
    String executedAt,
    Config config,
    Corpus corpus,
    Usage usage,
    Map<String, Integer> rejectedNodeTypes,
    Map<String, Integer> rejectedRelationTypes,
    GraphSize graphSize,
    List<DocumentRow> documents
) {

    public static final String SCHEMA_VERSION = "1";

    /**
     * 실행 당시 설정 스냅샷. 어떤 모델로 어떤 타입만 허용해 뽑았는지가 결과를 읽는 전제다
     */
    public record Config(
        String modelName,
        double temperature,
        List<String> allowedNodes,
        List<String> allowedRelationships,
        String inputText,
        double inputPricePer1mUsd,
        double outputPricePer1mUsd
    ) {}

    /**
     * 코퍼스 규모와 이번에 실제로 돈 건수.
     * publishedDocumentCount는 지난 측정값을 옮겨 적지 않고 실행 시점에 다시 센 값이다
     */
    public record Corpus(
        long publishedDocumentCount,
        int documentLimit,
        int processedCount,
        int failedCount,
        int noExtractionCount
    ) {}

    /**
     * 토큰과 금액. 문서를 하나도 안 돌면 문서당 금액과 전건 환산액은 null이다
     */
    public record Usage(
        long inputTokens,
        long outputTokens,
        long llmCallCount,
        double totalCostUsd,
        Double costPerDocumentUsd,
        Double fullCorpusCostUsd
    ) {}

    /** 실행이 끝난 뒤 컬렉션에 남은 수. 두 번 돌렸을 때 이 값이 같아야 멱등이다 */
    public record GraphSize(
        long nodeCount,
        long edgeCount
    ) {}

    /**
     * 문서 한 건의 처리 기록.
     * nodes·edges는 사람이 읽는 표기이고("Model|gpt-4o (GPT-4o)"), 마크다운 덤프도 이 값을 쓴다.
     * 실패하지 않았으면 failureReason이 null이다
     */
    public record DocumentRow(
        String externalId,
        String provider,
        String updateType,
        String inputText,
        long inputTokens,
        long outputTokens,
        int nodeCount,
        int edgeCount,
        List<String> nodes,
        List<String> edges,
        Map<String, Integer> rejectedNodeTypes,
        Map<String, Integer> rejectedRelationTypes,
        String failureReason
    ) {}
}
