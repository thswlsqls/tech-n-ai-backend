package com.tech.n.ai.domain.mongodb.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EmergingTech 데이터 집계 서비스
 */
@Service
@RequiredArgsConstructor
public class EmergingTechAggregationService {

    private final MongoTemplate mongoTemplate;

    private static final String COLLECTION = "emerging_techs";

    /**
     * 그룹별 도큐먼트 수 집계
     *
     * <p>countTotal() 별도 쿼리 없이 그룹 결과의 count 합산으로 totalCount를 계산할 수 있으나,
     * 그룹 필드가 null인 도큐먼트가 집계에서 누락될 수 있으므로 호출 측에서 판단한다.
     *
     * @param groupField 그룹 기준 필드 (provider, source_type, update_type)
     * @param startDate 조회 시작일 (nullable)
     * @param endDate 조회 종료일 (nullable)
     * @return 그룹별 집계 결과
     */
    public List<GroupCountResult> countByGroup(String groupField, LocalDateTime startDate, LocalDateTime endDate) {
        Criteria criteria = buildDateCriteria(startDate, endDate);

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group(groupField).count().as("count"),
            Aggregation.sort(Sort.Direction.DESC, "count")
        );

        return mongoTemplate.aggregate(aggregation, COLLECTION, GroupCountResult.class)
            .getMappedResults();
    }

    /**
     * 서버사이드 텍스트 빈도 집계 (MongoDB Aggregation Pipeline)
     *
     * <p>전체 도큐먼트를 Java로 가져오지 않고 MongoDB 내에서 토큰화 + 빈도 집계를 수행한다.
     * $project + $split + $unwind + $group 파이프라인으로 네트워크 전송량을 최소화하고
     * MongoDB의 분산 처리를 활용한다.
     *
     * @param provider provider 필터 (nullable)
     * @param startDate 조회 시작일 (nullable)
     * @param endDate 조회 종료일 (nullable)
     * @param stopWords 불용어 목록 (외부 설정에서 주입)
     * @param topN 상위 키워드 개수
     * @return 단어별 빈도 집계 결과
     */
    public List<WordFrequencyResult> aggregateWordFrequency(
            String provider, String updateType, String sourceType,
            LocalDateTime startDate, LocalDateTime endDate,
            List<String> stopWords, int topN) {

        Criteria criteria = applyOptionalFilters(
            buildDateCriteria(startDate, endDate), provider, updateType, sourceType);

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            // title과 summary를 결합하여 단어 배열로 분리
            Aggregation.project()
                .and(context -> new org.bson.Document("$split",
                    List.of(new org.bson.Document("$toLower",
                        new org.bson.Document("$concat",
                            List.of(
                                new org.bson.Document("$ifNull", List.of("$title", "")),
                                " ",
                                new org.bson.Document("$ifNull", List.of("$summary", ""))
                            )
                        )
                    ), " ")
                )).as("words"),
            Aggregation.unwind("words"),
            // 불용어, 빈 문자열, 2글자 미만, 숫자만으로 구성된 토큰 제외
            Aggregation.match(new Criteria().andOperator(
                Criteria.where("words").nin(stopWords),
                Criteria.where("words").regex("^[a-z][a-z0-9._-]+$")
            )),
            Aggregation.group("words").count().as("count"),
            Aggregation.sort(Sort.Direction.DESC, "count"),
            Aggregation.limit(topN)
        );

        return mongoTemplate.aggregate(aggregation, COLLECTION, WordFrequencyResult.class)
            .getMappedResults();
    }

    /**
     * 기간 내 전체 도큐먼트 수 조회
     *
     * @param provider provider 필터 (nullable)
     * @param startDate 조회 시작일 (nullable)
     * @param endDate 조회 종료일 (nullable)
     * @return 도큐먼트 수
     */
    public long countDocuments(String provider, String updateType, String sourceType,
                               LocalDateTime startDate, LocalDateTime endDate) {
        Criteria criteria = applyOptionalFilters(
            buildDateCriteria(startDate, endDate), provider, updateType, sourceType);
        Query query = new Query(criteria);
        return mongoTemplate.count(query, COLLECTION);
    }

    /**
     * provider / update_type / source_type 선택적 필터를 Criteria에 적용한다.
     * 값이 null이거나 빈 문자열이면 해당 필터는 건너뛴다.
     */
    private Criteria applyOptionalFilters(Criteria criteria, String provider, String updateType, String sourceType) {
        if (provider != null && !provider.isBlank()) {
            criteria = criteria.and("provider").is(provider);
        }
        if (updateType != null && !updateType.isBlank()) {
            criteria = criteria.and("update_type").is(updateType);
        }
        if (sourceType != null && !sourceType.isBlank()) {
            criteria = criteria.and("source_type").is(sourceType);
        }
        return criteria;
    }

    /**
     * 날짜 범위 Criteria 생성
     *
     * <p>published_at이 null인 도큐먼트도 포함하기 위해 $or 조건을 사용한다.
     * 데이터 수집 과정에서 published_at이 설정되지 않은 도큐먼트가 존재할 수 있으며,
     * 이를 통계에서 누락하지 않기 위함이다.
     */
    private Criteria buildDateCriteria(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null && endDate == null) {
            return new Criteria();
        }

        Criteria dateCriteria;
        if (startDate != null && endDate != null) {
            dateCriteria = Criteria.where("published_at").gte(startDate).lte(endDate);
        } else if (startDate != null) {
            dateCriteria = Criteria.where("published_at").gte(startDate);
        } else {
            dateCriteria = Criteria.where("published_at").lte(endDate);
        }

        // published_at이 null인 도큐먼트도 포함
        return new Criteria().orOperator(
            dateCriteria,
            Criteria.where("published_at").is(null)
        );
    }
}
