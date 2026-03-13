package com.tech.n.ai.domain.aurora.service.history;

import com.tech.n.ai.domain.aurora.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.hibernate7.Hibernate7Module;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HistoryServiceImpl
 */
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private static final ObjectMapper objectMapper = createObjectMapper();

    private final List<HistoryEntityFactory> historyEntityFactories;

    private static ObjectMapper createObjectMapper() {
        // Jackson 3: ObjectMapper is immutable, use JsonMapper.builder()
        // Jackson 3 defaults: WRITE_DATES_AS_TIMESTAMPS=false (ISO-8601)
        return JsonMapper.builder()
                .addModule(new Hibernate7Module())
                // null 필드도 항상 포함하여 히스토리 복구 시 null 값도 복원 가능하도록 함
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.ALWAYS))
                .build();
    }

    @Override
    public void saveHistory(BaseEntity entity, OperationType operationType, Object beforeData, Object afterData) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }

        String beforeJson = serializeToJson(beforeData);
        String afterJson = serializeToJson(afterData);
        Long changedBy = getCurrentUserId();
        LocalDateTime changedAt = LocalDateTime.now();

        HistoryEntityFactory factory = findFactory(entity);
        factory.createAndSave(entity, operationType, beforeJson, afterJson, changedBy, changedAt);
    }

    private HistoryEntityFactory findFactory(BaseEntity entity) {
        return historyEntityFactories.stream()
                .filter(factory -> factory.supports(entity))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported entity type: " + entity.getClass().getName()));
    }

    private String serializeToJson(Object data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    private Long getCurrentUserId() {
        // TODO: SecurityContext에서 현재 사용자 ID 추출
        // 현재는 null 반환
        return null;
    }
}
