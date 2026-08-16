package com.tech.n.ai.domain.mongodb.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * 지식 그래프의 관계 타입
 *
 * 이름 자체가 정본 라벨이다. 추출 모델에 allowedRelationships로 넘기고 엣지 키 가운데에 넣는다.
 * 모델이 돌려주는 타입 이름은 대소문자와 공백이 흔들리므로 fromLabel로 맞춰 본다.
 */
public enum GraphRelationType {

    RELEASED,
    /** 버전·모델의 후속 관계. SDK 릴리스 문서에서 주로 나온다 */
    SUCCEEDS,
    SUPPORTS,
    /** 회사가 제품·기술을 쓴다. 이게 없을 때 고객사 사례 문장이 SUPPORTS로 방향까지 뒤집혀 들어갔다 */
    USES,
    DEPENDS_ON;

    public String label() {
        return name();
    }

    /**
     * 대소문자와 공백을 무시하고 라벨을 찾는다. 목록에 없으면 빈 Optional을 준다.
     */
    public static Optional<GraphRelationType> fromLabel(String rawLabel) {
        if (rawLabel == null) {
            return Optional.empty();
        }
        String normalized = rawLabel.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        for (GraphRelationType type : values()) {
            if (type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
