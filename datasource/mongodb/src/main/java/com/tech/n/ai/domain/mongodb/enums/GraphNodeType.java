package com.tech.n.ai.domain.mongodb.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * 지식 그래프의 노드 타입
 *
 * label이 정본 표기다. 추출 모델에 allowedNodes로 넘기는 문자열이자 노드 키의 앞부분이 된다.
 * 모델이 돌려주는 타입 이름은 대소문자와 공백이 흔들리므로 fromLabel로 맞춰 본다.
 */
public enum GraphNodeType {

    COMPANY("Company"),
    MODEL("Model"),
    TECHNOLOGY("Technology"),
    RELEASE("Release"),
    CAPABILITY("Capability");

    private final String label;

    GraphNodeType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 대소문자와 공백을 무시하고 라벨을 찾는다. 목록에 없으면 빈 Optional을 준다.
     */
    public static Optional<GraphNodeType> fromLabel(String rawLabel) {
        if (rawLabel == null) {
            return Optional.empty();
        }
        String normalized = rawLabel.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        for (GraphNodeType type : values()) {
            if (type.label.toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
