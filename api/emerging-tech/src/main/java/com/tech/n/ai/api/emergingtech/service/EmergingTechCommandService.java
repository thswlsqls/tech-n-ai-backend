package com.tech.n.ai.api.emergingtech.service;

import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechCreateRequest;
import com.tech.n.ai.domain.mongodb.document.EmergingTechDocument;
import com.tech.n.ai.domain.mongodb.enums.PostStatus;

import java.util.List;

/**
 * Emerging Tech 명령 서비스
 */
public interface EmergingTechCommandService {

    /**
     * 저장 결과 (신규/중복 구분)
     */
    record SaveResult(EmergingTechDocument document, boolean isNew) {}

    /**
     * 단건 저장 (중복 시 기존 문서 반환)
     */
    SaveResult saveEmergingTech(EmergingTechCreateRequest request);

    /**
     * 다건 저장. 중복이면 저장하지 않고 같은 문서를 isNew = false 로 돌려준다 —
     * DB 에 이미 있던 문서이거나, 같은 요청 안 앞 자리가 만든 문서다. 결과는 요청과 같은 순서다
     */
    List<SaveResult> saveEmergingTechAll(List<EmergingTechCreateRequest> requests);

    /**
     * 상태 변경
     */
    EmergingTechDocument updateStatus(String id, PostStatus status);
}
