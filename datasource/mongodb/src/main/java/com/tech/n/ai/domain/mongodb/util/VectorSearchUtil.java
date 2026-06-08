package com.tech.n.ai.domain.mongodb.util;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB Atlas Vector Search 유틸리티
 * 
 * $vectorSearch aggregation pipeline 생성을 위한 유틸리티 클래스.
 * 실제 실행은 api/chatbot 모듈에서 MongoTemplate을 사용하여 수행합니다.
 * 
 * 공식 문서: https://www.mongodb.com/docs/atlas/atlas-vector-search/vector-search-stage/
 */
public final class VectorSearchUtil {
    
    // Bookmark 컬렉션
    public static final String COLLECTION_BOOKMARKS = "bookmarks";
    public static final String INDEX_BOOKMARKS = "vector_index_bookmarks";

    // Emerging Tech 컬렉션
    public static final String COLLECTION_EMERGING_TECHS = "emerging_techs";
    public static final String INDEX_EMERGING_TECHS = "vector_index_emerging_techs";
    
    private VectorSearchUtil() {
        // 유틸리티 클래스
    }
    
    /**
     * $vectorSearch aggregation stage 생성
     * 
     * 공식 문서: https://www.mongodb.com/docs/atlas/atlas-vector-search/vector-search-stage/
     * 
     * @param indexName Vector Search Index 이름
     * @param path 벡터 필드 경로 (예: "embedding_vector") - 필수
     * @param queryVector 쿼리 벡터 (1536 dimensions)
     * @param numCandidates 검색 후보 수 (기본값: 100, limit의 10~20배 권장)
     * @param limit 검색 결과 수 제한
     * @param filter 필터 조건 (선택사항, MQL 연산자 사용)
     * @param exact ENN 검색 여부 (기본값: false, ANN 사용)
     * @return $vectorSearch stage Document
     */
    public static Document createVectorSearchStage(
            String indexName,
            String path,
            List<Float> queryVector,
            int numCandidates,
            int limit,
            Document filter,
            boolean exact) {
        
        Document vectorSearchParams = new Document()
            .append("index", indexName)
            .append("path", path)
            .append("queryVector", queryVector)
            .append("limit", limit);
        
        // exact=true일 경우 numCandidates 무시
        if (!exact) {
            vectorSearchParams.append("numCandidates", numCandidates);
        }
        
        // filter가 있으면 추가
        if (filter != null && !filter.isEmpty()) {
            vectorSearchParams.append("filter", filter);
        }
        
        // exact 옵션 추가 (기본값 false이면 생략 가능하지만 명시적으로 추가)
        if (exact) {
            vectorSearchParams.append("exact", true);
        }
        
        return new Document("$vectorSearch", vectorSearchParams);
    }
    
    /**
     * $vectorSearch stage를 VectorSearchOptions로 생성
     * 
     * @param queryVector 쿼리 벡터
     * @param options 검색 옵션
     * @return $vectorSearch stage Document
     */
    public static Document createVectorSearchStage(List<Float> queryVector, VectorSearchOptions options) {
        return createVectorSearchStage(
            options.getIndexName(),
            options.getPath(),
            queryVector,
            options.getNumCandidates(),
            options.getLimit(),
            options.getFilter(),
            options.isExact()
        );
    }
    
    /**
     * Vector Search 결과 프로젝션 stage 생성
     * $meta: "vectorSearchScore"를 포함하여 유사도 점수 추출
     * 
     * @param fields 프로젝션할 필드 목록 (null이면 기본 필드만)
     * @return $project stage Document
     */
    public static Document createProjectionStage(List<String> fields) {
        Document projection = new Document()
            .append("_id", 1)
            .append("score", new Document("$meta", "vectorSearchScore"))
            .append("embedding_text", 1);
        
        if (fields != null) {
            for (String field : fields) {
                projection.append(field, 1);
            }
        }
        
        return new Document("$project", projection);
    }
    
    /**
     * 기본 프로젝션 stage 생성 (모든 필드 + score)
     * 
     * @return $addFields stage Document
     */
    public static Document createScoreAddFieldsStage() {
        return new Document("$addFields", 
            new Document("score", new Document("$meta", "vectorSearchScore")));
    }
    
    /**
     * 유사도 점수 필터링 stage 생성
     * 
     * @param minScore 최소 유사도 점수
     * @return $match stage Document
     */
    public static Document createScoreFilterStage(double minScore) {
        return new Document("$match", 
            new Document("score", new Document("$gte", minScore)));
    }
    
    /**
     * 기본 필터(예: status, user_id)를 기존 options.filter와 $and로 병합하고,
     * 인덱스명이 비어 있으면 기본 인덱스를 채워 VectorSearchOptions를 다시 만든다.
     *
     * @param options 원본 검색 옵션
     * @param baseFilter 항상 적용할 기본 필터
     * @param defaultIndex options에 인덱스명이 없을 때 사용할 기본 인덱스
     * @return filter와 indexName이 채워진 VectorSearchOptions
     */
    private static VectorSearchOptions withBaseFilter(
            VectorSearchOptions options, Document baseFilter, String defaultIndex) {

        Document combinedFilter;
        if (options.getFilter() != null && !options.getFilter().isEmpty()) {
            combinedFilter = new Document("$and", List.of(baseFilter, options.getFilter()));
        } else {
            combinedFilter = baseFilter;
        }

        return VectorSearchOptions.builder()
            .indexName(options.getIndexName() != null ? options.getIndexName() : defaultIndex)
            .path(options.getPath())
            .numCandidates(options.getNumCandidates())
            .limit(options.getLimit())
            .minScore(options.getMinScore())
            .filter(combinedFilter)
            .exact(options.isExact())
            .build();
    }

    /**
     * Emerging Tech 컬렉션 Vector Search 파이프라인 생성
     * status: "PUBLISHED" pre-filter 기본 적용
     *
     * @param queryVector 쿼리 벡터
     * @param options 검색 옵션
     * @return aggregation pipeline
     */
    public static List<Document> createEmergingTechSearchPipeline(
            List<Float> queryVector,
            VectorSearchOptions options) {

        List<Document> pipeline = new ArrayList<>();

        // status: "PUBLISHED" pre-filter를 기존 filter와 병합하여 옵션 재구성
        VectorSearchOptions emergingTechOptions = withBaseFilter(
            options, new Document("status", "PUBLISHED"), INDEX_EMERGING_TECHS);

        // 1. $vectorSearch stage
        pipeline.add(createVectorSearchStage(queryVector, emergingTechOptions));

        // 2. $addFields stage (score 추가)
        pipeline.add(createScoreAddFieldsStage());

        // 3. $match stage (minScore 필터링)
        if (options.getMinScore() > 0) {
            pipeline.add(createScoreFilterStage(options.getMinScore()));
        }

        return pipeline;
    }

    // === Score Fusion 메서드 ===

    /**
     * Score Fusion용 vectorScore 추출 stage
     * 기존 createScoreAddFieldsStage()의 "score" 대신 "vectorScore" 사용
     */
    public static Document createVectorScoreStage() {
        return new Document("$addFields",
            new Document("vectorScore", new Document("$meta", "vectorSearchScore")));
    }

    /**
     * Score Fusion용 vectorScore 필터링 stage
     */
    public static Document createVectorScoreFilterStage(double minScore) {
        return new Document("$match",
            new Document("vectorScore", new Document("$gte", minScore)));
    }

    /**
     * Recency Score 계산 stage (Exponential Decay)
     * recencyScore = e^(-λ × daysSincePublished)
     * published_at이 null이면 기본값 0.5
     *
     * 사용 연산자:
     * - $dateDiff (MongoDB 5.0+): https://www.mongodb.com/docs/manual/reference/operator/aggregation/dateDiff/
     * - $exp (MongoDB 4.2+): https://www.mongodb.com/docs/manual/reference/operator/aggregation/exp/
     *
     * @param decayLambda Exponential Decay 계수 (기본값: 1.0/365.0)
     */
    public static Document createRecencyScoreStage(double decayLambda) {
        Document rawDateDiff = new Document("$dateDiff", new Document()
            .append("startDate", "$published_at")
            .append("endDate", "$$NOW")
            .append("unit", "day"));

        // 미래 날짜(published_at > now)로 인한 음수값 방지 → recencyScore가 항상 [0, 1] 범위 유지
        Document dateDiff = new Document("$max", List.of(0, rawDateDiff));

        Document exponentialDecay = new Document("$exp",
            new Document("$multiply", List.of(-decayLambda, dateDiff)));

        Document recencyScore = new Document("$cond", new Document()
            .append("if", new Document("$ifNull", List.of("$published_at", false)))
            .append("then", exponentialDecay)
            .append("else", 0.5));

        return new Document("$addFields", new Document("recencyScore", recencyScore));
    }

    /**
     * Score Fusion stage (가중 결합)
     * combinedScore = vectorScore × vectorWeight + recencyScore × recencyWeight
     *
     * @param vectorWeight 벡터 유사도 가중치
     * @param recencyWeight 최신성 가중치
     */
    public static Document createScoreFusionStage(double vectorWeight, double recencyWeight) {
        Document combinedScore = new Document("$add", List.of(
            new Document("$multiply", List.of("$vectorScore", vectorWeight)),
            new Document("$multiply", List.of("$recencyScore", recencyWeight))
        ));

        return new Document("$addFields", new Document("combinedScore", combinedScore));
    }

    /**
     * Score Fusion 결과 정렬 stage
     */
    public static Document createFusionSortStage() {
        return new Document("$sort", new Document("combinedScore", -1));
    }

    /**
     * 결과 수 제한 stage
     */
    public static Document createLimitStage(int limit) {
        return new Document("$limit", limit);
    }

    /**
     * Emerging Tech 컬렉션 Score Fusion 파이프라인 생성
     * 기존 createEmergingTechSearchPipeline()을 확장하여 Score Fusion stage 추가
     *
     * 파이프라인 구조:
     * $vectorSearch → $addFields(vectorScore) → $match(minScore)
     * → $addFields(recencyScore) → $addFields(combinedScore) → $sort → $limit
     *
     * @param queryVector 쿼리 벡터
     * @param options 검색 옵션 (enableScoreFusion=true 필요)
     * @return aggregation pipeline
     */
    public static List<Document> createEmergingTechSearchPipelineWithFusion(
            List<Float> queryVector,
            VectorSearchOptions options) {

        List<Document> pipeline = new ArrayList<>();

        // status: "PUBLISHED" pre-filter를 기존 filter와 병합하여 옵션 재구성
        VectorSearchOptions fusionOptions = withBaseFilter(
            options, new Document("status", "PUBLISHED"), INDEX_EMERGING_TECHS);

        // 1. $vectorSearch stage
        pipeline.add(createVectorSearchStage(queryVector, fusionOptions));

        // 2. vectorScore 추출 (Score Fusion용 필드명)
        pipeline.add(createVectorScoreStage());

        // 3. 최소 점수 필터링
        if (options.getMinScore() > 0) {
            pipeline.add(createVectorScoreFilterStage(options.getMinScore()));
        }

        // 4. recencyScore 계산
        pipeline.add(createRecencyScoreStage(options.getRecencyDecayLambda()));

        // 5. combinedScore 계산
        pipeline.add(createScoreFusionStage(options.getVectorWeight(), options.getRecencyWeight()));

        // 6. combinedScore 기준 정렬
        pipeline.add(createFusionSortStage());

        // 7. 최종 결과 수 제한
        pipeline.add(createLimitStage(options.getLimit()));

        return pipeline;
    }

    /**
     * Bookmark 컬렉션 Vector Search 파이프라인 생성 (userId 필터 포함)
     *
     * @param queryVector 쿼리 벡터
     * @param userId 사용자 ID (필터링용)
     * @param options 검색 옵션
     * @return aggregation pipeline
     */
    public static List<Document> createBookmarkSearchPipeline(
            List<Float> queryVector,
            String userId,
            VectorSearchOptions options) {
        
        List<Document> pipeline = new ArrayList<>();

        // userId 필터를 기존 filter와 병합하여 옵션 재구성
        VectorSearchOptions bookmarkOptions = withBaseFilter(
            options, new Document("user_id", userId), INDEX_BOOKMARKS);

        // 1. $vectorSearch stage
        pipeline.add(createVectorSearchStage(queryVector, bookmarkOptions));
        
        // 2. $addFields stage (score 추가)
        pipeline.add(createScoreAddFieldsStage());
        
        // 3. $match stage (minScore 필터링)
        if (options.getMinScore() > 0) {
            pipeline.add(createScoreFilterStage(options.getMinScore()));
        }
        
        return pipeline;
    }
}
