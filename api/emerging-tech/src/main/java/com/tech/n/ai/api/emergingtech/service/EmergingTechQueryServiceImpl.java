package com.tech.n.ai.api.emergingtech.service;

import com.tech.n.ai.common.exception.exception.ResourceNotFoundException;
import com.tech.n.ai.domain.mongodb.document.EmergingTechDocument;
import com.tech.n.ai.domain.mongodb.repository.EmergingTechRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Emerging Tech 조회 서비스 구현체
 * - 동적 Criteria 쿼리로 필터 조합 처리
 */
@Service
@RequiredArgsConstructor
public class EmergingTechQueryServiceImpl implements EmergingTechQueryService {

    private final EmergingTechRepository emergingTechRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public Page<EmergingTechDocument> findEmergingTechs(
            String provider, String updateType, String status,
            String sourceType, String startDate, String endDate,
            Pageable pageable) {
        Criteria criteria = buildFilterCriteria(provider, updateType, status, sourceType, startDate, endDate);
        Query query = new Query(criteria).with(pageable);

        List<EmergingTechDocument> content = mongoTemplate.find(query, EmergingTechDocument.class);
        return PageableExecutionUtils.getPage(content, pageable,
                () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), EmergingTechDocument.class));
    }

    @Override
    public EmergingTechDocument findEmergingTechById(String id) {
        String notFoundMessage = "Emerging Tech를 찾을 수 없습니다: " + id;
        ObjectId objectId;
        try {
            objectId = new ObjectId(id);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException(notFoundMessage);
        }

        return emergingTechRepository.findById(objectId)
                .orElseThrow(() -> new ResourceNotFoundException(notFoundMessage));
    }

    @Override
    public Page<EmergingTechDocument> searchEmergingTech(String query, Pageable pageable) {
        return emergingTechRepository.findByTitleContainingIgnoreCase(query, pageable);
    }

    /**
     * 필터 조건을 동적으로 조합
     */
    private Criteria buildFilterCriteria(String provider, String updateType, String status,
                                          String sourceType, String startDate, String endDate) {
        Criteria criteria = new Criteria();

        if (provider != null) {
            criteria = criteria.and("provider").is(provider);
        }
        if (updateType != null) {
            criteria = criteria.and("update_type").is(updateType);
        }
        if (status != null) {
            criteria = criteria.and("status").is(status);
        }
        if (sourceType != null) {
            criteria = criteria.and("source_type").is(sourceType);
        }

        // published_at 기간 필터
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("published_at");
            if (startDate != null) {
                LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
                dateCriteria = dateCriteria.gte(start);
            }
            if (endDate != null) {
                LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
                dateCriteria = dateCriteria.lte(end);
            }
            criteria = criteria.andOperator(dateCriteria);
        }

        return criteria;
    }
}
