package com.tech.n.ai.batch.graph.job;

import com.tech.n.ai.batch.graph.config.GraphExtractionModelConfig;
import com.tech.n.ai.batch.graph.extract.ExtractedGraph;
import com.tech.n.ai.batch.graph.extract.GraphExtractor;
import com.tech.n.ai.batch.graph.extract.GraphTokenUsageRecorder;
import com.tech.n.ai.batch.graph.extract.GraphTypeWhitelist;
import com.tech.n.ai.batch.graph.report.GraphBuildReport;
import com.tech.n.ai.batch.graph.report.GraphBuildReportWriter;
import com.tech.n.ai.batch.graph.write.GraphWriter;
import com.tech.n.ai.domain.mongodb.document.EmergingTechDocument;
import com.tech.n.ai.domain.mongodb.enums.GraphNodeType;
import com.tech.n.ai.domain.mongodb.enums.PostStatus;
import com.tech.n.ai.domain.mongodb.key.GraphKeys;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PUBLISHED 문서를 훑어 그래프 컬렉션을 채운다.
 *
 * emerging_techs는 읽기만 한다. 쓰는 곳은 그래프 노드·엣지 컬렉션 둘뿐이다.
 * 문서 하나가 실패해도 잡을 멈추지 않는다. 수백 건을 도는 실행이 문서 하나 때문에 통째로
 * 날아가면 그때까지 쓴 API 비용이 사라지기 때문이다. 실패는 그 문서 행에 사유로 남는다.
 */
@Slf4j
@Component
public class GraphBuildTasklet implements Tasklet {

    private static final String INPUT_TEXT_TITLE_SUMMARY = "title-summary";
    private static final String INPUT_TEXT_EMBEDDING = "embedding-text";
    private static final String SELECT_SPREAD = "spread";
    private static final String STATUS_FIELD = "status";
    private static final String PUBLISHED_AT_FIELD = "published_at";
    private static final String EXTERNAL_ID_FIELD = "external_id";
    private static final String EMBEDDING_VECTOR_FIELD = "embedding_vector";
    private static final int PROGRESS_LOG_INTERVAL = 25;
    private static final double TOKENS_PER_PRICE_UNIT = 1_000_000.0;

    private final MongoTemplate mongoTemplate;
    private final GraphExtractor graphExtractor;
    private final GraphWriter graphWriter;
    private final GraphTokenUsageRecorder tokenUsageRecorder;
    private final GraphBuildReportWriter reportWriter;
    private final int documentLimit;
    private final String selectMode;
    private final String inputTextKind;
    private final String modelName;
    private final double inputPricePer1mUsd;
    private final double outputPricePer1mUsd;

    public GraphBuildTasklet(MongoTemplate mongoTemplate,
                             GraphExtractor graphExtractor,
                             GraphWriter graphWriter,
                             GraphTokenUsageRecorder tokenUsageRecorder,
                             GraphBuildReportWriter reportWriter,
                             @Value("${graph.build.document-limit:20}") int documentLimit,
                             @Value("${graph.build.select:recent}") String selectMode,
                             @Value("${graph.build.input-text:embedding-text}") String inputTextKind,
                             @Value("${graph.build.model-name:gpt-4o-mini}") String modelName,
                             @Value("${graph.build.input-price-per-1m-usd}") double inputPricePer1mUsd,
                             @Value("${graph.build.output-price-per-1m-usd}") double outputPricePer1mUsd) {
        this.mongoTemplate = mongoTemplate;
        this.graphExtractor = graphExtractor;
        this.graphWriter = graphWriter;
        this.tokenUsageRecorder = tokenUsageRecorder;
        this.reportWriter = reportWriter;
        this.documentLimit = documentLimit;
        this.selectMode = selectMode;
        this.inputTextKind = inputTextKind;
        this.modelName = modelName;
        this.inputPricePer1mUsd = inputPricePer1mUsd;
        this.outputPricePer1mUsd = outputPricePer1mUsd;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDateTime executedAt = LocalDateTime.now();

        // 지난 측정값을 옮겨 적지 않고 실행 시점에 다시 센다. 수집 배치가 계속 도는 코퍼스라 값이 변한다.
        long publishedDocumentCount = mongoTemplate.count(publishedQuery(), EmergingTechDocument.class);
        List<EmergingTechDocument> documents = selectDocuments();
        log.info("그래프 구축 시작: published={}건, 이번 실행 대상={}건, limit={}, select={}",
            publishedDocumentCount, documents.size(), documentLimit, selectMode);

        GraphTokenUsageRecorder.Snapshot runStart = tokenUsageRecorder.snapshot();
        List<GraphBuildReport.DocumentRow> rows = new ArrayList<>();
        Map<String, Integer> rejectedNodeTypes = new HashMap<>();
        Map<String, Integer> rejectedRelationTypes = new HashMap<>();
        int failedCount = 0;
        int noExtractionCount = 0;

        for (int i = 0; i < documents.size(); i++) {
            EmergingTechDocument document = documents.get(i);
            GraphTokenUsageRecorder.Snapshot before = tokenUsageRecorder.snapshot();
            String inputText = inputTextOf(document);

            List<String> nodeLabels = new ArrayList<>();
            List<String> edgeLabels = List.of();
            Map<String, Integer> documentRejectedNodes = Map.of();
            Map<String, Integer> documentRejectedRelations = Map.of();
            String failureReason = null;

            try {
                writeProviderNode(document).ifPresent(nodeLabels::add);

                Optional<GraphDocument> extracted = graphExtractor.extract(inputText);
                if (extracted.isEmpty()) {
                    // 실패가 아니다. 모델이 뽑을 게 없다고 판단한 문서다.
                    noExtractionCount++;
                } else {
                    ExtractedGraph graph = GraphTypeWhitelist.filter(extracted.get());
                    documentRejectedNodes = graph.rejectedNodeTypes();
                    documentRejectedRelations = graph.rejectedRelationTypes();
                    nodeLabels.addAll(writeNodes(graph, document.getExternalId()));
                    edgeLabels = writeEdges(graph, document.getExternalId());
                }
            } catch (RuntimeException e) {
                failedCount++;
                failureReason = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("문서 처리 실패: externalId={}, message={}", document.getExternalId(), e.getMessage());
            }

            merge(rejectedNodeTypes, documentRejectedNodes);
            merge(rejectedRelationTypes, documentRejectedRelations);

            GraphTokenUsageRecorder.Snapshot spent = tokenUsageRecorder.snapshot().minus(before);
            rows.add(new GraphBuildReport.DocumentRow(
                document.getExternalId(),
                document.getProvider(),
                document.getUpdateType(),
                inputText,
                spent.inputTokens(),
                spent.outputTokens(),
                nodeLabels.size(),
                edgeLabels.size(),
                nodeLabels,
                edgeLabels,
                documentRejectedNodes,
                documentRejectedRelations,
                failureReason));

            logProgress(i + 1, documents.size(), tokenUsageRecorder.snapshot().minus(runStart));
        }

        GraphTokenUsageRecorder.Snapshot totalSpent = tokenUsageRecorder.snapshot().minus(runStart);
        GraphBuildReport report = new GraphBuildReport(
            GraphBuildReport.SCHEMA_VERSION,
            executedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            new GraphBuildReport.Config(
                modelName,
                GraphExtractionModelConfig.EXTRACTION_TEMPERATURE,
                GraphExtractor.allowedNodes(),
                GraphExtractor.allowedRelationships(),
                inputTextKind,
                inputPricePer1mUsd,
                outputPricePer1mUsd),
            new GraphBuildReport.Corpus(
                publishedDocumentCount,
                documentLimit,
                documents.size(),
                failedCount,
                noExtractionCount),
            usage(totalSpent, documents.size(), publishedDocumentCount),
            rejectedNodeTypes,
            rejectedRelationTypes,
            new GraphBuildReport.GraphSize(graphWriter.countNodes(), graphWriter.countEdges()),
            rows);

        reportWriter.write(report, executedAt);
        return RepeatStatus.FINISHED;
    }

    private Query publishedQuery() {
        return new Query(Criteria.where(STATUS_FIELD).is(PostStatus.PUBLISHED.name()));
    }

    /**
     * 이번 실행에서 돌 문서를 고른다.
     *
     * 기본(recent)은 최신순 앞에서 N건이다. 그런데 최신순으로만 자르면 표본이 한쪽으로 쏠린다 —
     * 20건을 뽑았더니 OPENAI가 16건, BLOG_POST가 15건이었다. spread는 같은 정렬을 유지한 채
     * (provider, update_type) 묶음을 돌아가며 한 건씩 골라 타입이 섞이게 한다.
     * limit이 0이면 전량이라 고를 것이 없고 두 방식이 같다.
     */
    private List<EmergingTechDocument> selectDocuments() {
        List<EmergingTechDocument> found = mongoTemplate.find(selectQuery(), EmergingTechDocument.class);
        if (documentLimit <= 0 || !SELECT_SPREAD.equals(selectMode)) {
            return found;
        }
        return spread(found);
    }

    /**
     * 최신순으로 훑는다. published_at이 같으면 external_id로 순서를 고정해, 구간을 나눠 돌려도
     * 같은 문서가 두 번 잡히거나 빠지지 않는다. 임베딩 벡터는 쓰지 않으므로 읽어오지 않는다.
     * spread는 전량을 읽어 코드에서 골라야 하므로 limit을 걸지 않는다.
     */
    private Query selectQuery() {
        Query query = publishedQuery()
            .with(Sort.by(Sort.Order.desc(PUBLISHED_AT_FIELD), Sort.Order.asc(EXTERNAL_ID_FIELD)));
        query.fields().exclude(EMBEDDING_VECTOR_FIELD);
        if (documentLimit > 0 && !SELECT_SPREAD.equals(selectMode)) {
            query.limit(documentLimit);
        }
        return query;
    }

    /**
     * (provider, update_type) 묶음을 라운드로빈으로 한 건씩 돌아 limit을 채운다.
     * LinkedHashMap이라 묶음 순서도 묶음 안 순서도 정렬된 원본 그대로다. 코퍼스가 같으면
     * 같은 표본이 다시 나오므로 결과를 재현해 볼 수 있다.
     */
    private List<EmergingTechDocument> spread(List<EmergingTechDocument> documents) {
        Map<String, List<EmergingTechDocument>> groups = new LinkedHashMap<>();
        for (EmergingTechDocument document : documents) {
            String groupKey = document.getProvider() + "|" + document.getUpdateType();
            groups.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(document);
        }

        List<EmergingTechDocument> picked = new ArrayList<>();
        for (int round = 0; picked.size() < documentLimit; round++) {
            boolean pickedInRound = false;
            for (List<EmergingTechDocument> group : groups.values()) {
                if (round >= group.size()) {
                    continue;
                }
                picked.add(group.get(round));
                pickedInRound = true;
                if (picked.size() == documentLimit) {
                    break;
                }
            }
            if (!pickedInRound) {
                break;
            }
        }
        return picked;
    }

    /**
     * 기본값은 embedding_text다. 같은 문서 20건을 두 방식으로 돌려 비교한 결과가
     * 03-graph-schema.md의 "결정 3"에 있다 — embedding_text 앞에 붙는 provider 이름 덕에
     * 모델이 주어를 회사로 잡아서, 추출 결과 없음이 60%에서 45%로 줄고 잘못된 관계도 적었다.
     *
     * 모르는 값은 조용히 한쪽으로 흘려보내지 않고 멈춘다. 오타 하나로 기각한 방식으로 돌면
     * 리포트만 보고는 알아채기 어렵다.
     */
    private String inputTextOf(EmergingTechDocument document) {
        if (INPUT_TEXT_EMBEDDING.equals(inputTextKind)) {
            return nullToEmpty(document.getEmbeddingText());
        }
        if (INPUT_TEXT_TITLE_SUMMARY.equals(inputTextKind)) {
            return String.join("\n\n",
                nullToEmpty(document.getTitle()), nullToEmpty(document.getSummary())).strip();
        }
        throw new IllegalArgumentException("graph.build.input-text는 %s 또는 %s여야 한다: %s"
            .formatted(INPUT_TEXT_EMBEDDING, INPUT_TEXT_TITLE_SUMMARY, inputTextKind));
    }

    /**
     * 문서의 provider로 Company 노드를 만든다. 추출 결과와 상관없이 늘 만든다 — provider는 이미
     * TechProvider enum 값이라 회사 이름을 모델에게 물어볼 이유가 없다. 키를 만드는 규칙이
     * 추출 노드와 같아서("OPENAI" → Company|openai) 같은 노드로 자연히 합쳐진다.
     *
     * 엣지는 만들지 않는다. provider가 OpenAI인 문서가 다른 회사 모델을 다루기도 해서,
     * "이 문서의 회사가 이걸 냈다"고 코드가 단정하면 그게 곧 우리가 세고 있는 잘못된 관계가 된다.
     */
    private Optional<String> writeProviderNode(EmergingTechDocument document) {
        String provider = document.getProvider();
        if (provider == null || provider.isBlank()) {
            return Optional.empty();
        }
        Optional<String> nodeKey = GraphKeys.nodeKey(GraphNodeType.COMPANY, provider);
        nodeKey.ifPresent(key ->
            graphWriter.upsertNode(key, GraphNodeType.COMPANY, provider, document.getExternalId()));
        return nodeKey.map(key -> key + " (" + provider + ")");
    }

    private List<String> writeNodes(ExtractedGraph graph, String externalId) {
        List<String> labels = new ArrayList<>();
        for (ExtractedGraph.Node node : graph.nodes()) {
            Optional<String> nodeKey = GraphKeys.nodeKey(node.type(), node.name());
            if (nodeKey.isEmpty()) {
                continue;
            }
            graphWriter.upsertNode(nodeKey.get(), node.type(), node.name(), externalId);
            labels.add(nodeKey.get() + " (" + node.name() + ")");
        }
        return labels;
    }

    private List<String> writeEdges(ExtractedGraph graph, String externalId) {
        List<String> labels = new ArrayList<>();
        for (ExtractedGraph.Edge edge : graph.edges()) {
            Optional<String> sourceKey = GraphKeys.nodeKey(edge.sourceType(), edge.sourceName());
            Optional<String> targetKey = GraphKeys.nodeKey(edge.targetType(), edge.targetName());
            if (sourceKey.isEmpty() || targetKey.isEmpty()) {
                continue;
            }
            String edgeKey = GraphKeys.edgeKey(sourceKey.get(), edge.type(), targetKey.get());
            graphWriter.upsertEdge(edgeKey, edge.type(), sourceKey.get(), targetKey.get(), externalId);
            labels.add(sourceKey.get() + " -" + edge.type().label() + "-> " + targetKey.get());
        }
        return labels;
    }

    private GraphBuildReport.Usage usage(GraphTokenUsageRecorder.Snapshot spent,
                                         int processedCount,
                                         long publishedDocumentCount) {
        double totalCostUsd =
            spent.inputTokens() / TOKENS_PER_PRICE_UNIT * inputPricePer1mUsd
                + spent.outputTokens() / TOKENS_PER_PRICE_UNIT * outputPricePer1mUsd;
        Double costPerDocumentUsd = processedCount == 0 ? null : totalCostUsd / processedCount;
        Double fullCorpusCostUsd =
            costPerDocumentUsd == null ? null : costPerDocumentUsd * publishedDocumentCount;

        return new GraphBuildReport.Usage(
            spent.inputTokens(), spent.outputTokens(), spent.callCount(),
            totalCostUsd, costPerDocumentUsd, fullCorpusCostUsd);
    }

    private void logProgress(int processed, int total, GraphTokenUsageRecorder.Snapshot spent) {
        if (processed % PROGRESS_LOG_INTERVAL != 0 && processed != total) {
            return;
        }
        double costUsd = spent.inputTokens() / TOKENS_PER_PRICE_UNIT * inputPricePer1mUsd
            + spent.outputTokens() / TOKENS_PER_PRICE_UNIT * outputPricePer1mUsd;
        log.info("진행 {}/{}: 입력 {}토큰, 출력 {}토큰, 누적 ${}",
            processed, total, spent.inputTokens(), spent.outputTokens(), costUsd);
    }

    private void merge(Map<String, Integer> total, Map<String, Integer> counts) {
        counts.forEach((type, count) -> total.merge(type, count, Integer::sum));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
