package com.tech.n.ai.domain.mongodb.repository;

import com.tech.n.ai.domain.mongodb.document.EmergingTechDocument;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Emerging Tech Repository
 * - 필터 조합 쿼리는 MongoTemplate 동적 Criteria로 처리
 */
@Repository
public interface EmergingTechRepository extends MongoRepository<EmergingTechDocument, ObjectId> {

    /**
     * 외부 ID로 조회 (중복 체크용)
     */
    Optional<EmergingTechDocument> findByExternalId(String externalId);

    /**
     * URL로 조회 (중복 체크용)
     */
    Optional<EmergingTechDocument> findByUrl(String url);

    /**
     * 외부 ID 목록으로 조회 (다건 중복 체크용)
     */
    List<EmergingTechDocument> findByExternalIdIn(Collection<String> externalIds);

    /**
     * URL 목록으로 조회 (다건 중복 체크용)
     */
    List<EmergingTechDocument> findByUrlIn(Collection<String> urls);

    /**
     * 제목 검색 (대소문자 무시)
     */
    Page<EmergingTechDocument> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
