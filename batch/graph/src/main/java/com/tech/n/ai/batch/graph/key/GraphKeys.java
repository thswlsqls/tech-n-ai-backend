package com.tech.n.ai.batch.graph.key;

import com.tech.n.ai.domain.mongodb.enums.GraphNodeType;
import com.tech.n.ai.domain.mongodb.enums.GraphRelationType;

import java.util.Locale;
import java.util.Optional;

/**
 * 노드·엣지의 키를 만든다.
 *
 * 키는 upsert가 같은 대상을 다시 찾는 유일한 수단이다. 같은 회사·모델이 문서마다 다른 표기로
 * 나와도 같은 키가 나와야 재실행에서 노드가 늘지 않는다.
 * Spring에 기대지 않는 static 메서드로 둔다.
 */
public final class GraphKeys {

    private static final String TYPE_DELIMITER = "|";
    private static final String EDGE_DELIMITER = "->";

    private GraphKeys() {
    }

    /**
     * 앞뒤 공백을 없애고, 가운데 연속 공백을 한 칸으로 줄이고, 소문자로 바꾼다.
     * 남는 게 없으면 빈 Optional을 준다. 이름이 없는 노드는 키를 만들 수 없다.
     */
    public static Optional<String> normalizeName(String rawName) {
        if (rawName == null) {
            return Optional.empty();
        }
        String normalized = rawName.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
    }

    /** 예: Model|gpt-4o */
    public static Optional<String> nodeKey(GraphNodeType type, String rawName) {
        return normalizeName(rawName).map(name -> type.label() + TYPE_DELIMITER + name);
    }

    /**
     * 예: Company|openai-&gt;RELEASED-&gt;Model|gpt-4o
     * 방향이 뜻을 바꾸므로 출발·도착을 정렬해 접지 않는다.
     */
    public static String edgeKey(String sourceKey, GraphRelationType type, String targetKey) {
        return sourceKey + EDGE_DELIMITER + type.label() + EDGE_DELIMITER + targetKey;
    }
}
