package com.tech.n.ai.batch.eval.report;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 실행 당시 설정 스냅샷을 만든다.
 *
 * 검색 기준선 잡과 답변 품질 잡이 같은 값을 써야 두 리포트를 나란히 놓고 비교할 수 있다.
 * 잡마다 따로 만들면 키 이름이나 기본값이 조금씩 갈라진다.
 */
@Component
public class EvalConfigSnapshotFactory {

    private static final String COLLECTION_EMERGING_TECHS = "emerging_techs";
    private static final String TOKEN_ESTIMATION = "OpenAiTokenCountEstimator (추정치, 실측 아님)";

    private final MongoTemplate mongoTemplate;

    @Value("${chatbot.rag.max-search-results:5}")
    private int maxSearchResults;

    @Value("${chatbot.rag.min-similarity-score:0.7}")
    private double minSimilarityScore;

    @Value("${chatbot.rag.recency-months:6}")
    private int recencyMonths;

    @Value("${chatbot.reranking.enabled:false}")
    private boolean rerankingEnabled;

    @Value("${langchain4j.open-ai.chat-model.temperature:0.7}")
    private double chatModelTemperature;

    @Value("${langchain4j.open-ai.embedding-model.model-name:text-embedding-3-small}")
    private String embeddingModelName;

    @Value("${langchain4j.open-ai.embedding-model.dimensions:1536}")
    private int embeddingDimensions;

    public EvalConfigSnapshotFactory(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public EvalReport.Config create(boolean generationModelCalled) {
        return new EvalReport.Config(
            maxSearchResults,
            minSimilarityScore,
            recencyMonths,
            true,
            rerankingEnabled,
            chatModelTemperature,
            embeddingModelName,
            embeddingDimensions,
            publishedDocumentCount(),
            TOKEN_ESTIMATION,
            generationModelCalled
        );
    }

    private long publishedDocumentCount() {
        Query query = new Query(Criteria.where("status").is("PUBLISHED"));
        return mongoTemplate.count(query, Document.class, COLLECTION_EMERGING_TECHS);
    }
}
