package com.tech.n.ai.batch.eval.scoring;

import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import org.bson.Document;

import java.util.Optional;

/**
 * 검색 결과에서 external_id를 꺼낸다.
 *
 * SearchResult.metadata()에는 MongoDB 원본 Document가 그대로 들어 있다.
 * Document가 아니거나 필드가 비어 있으면 빈 값을 돌려준다.
 */
public final class ExternalIdExtractor {

    private static final String FIELD_EXTERNAL_ID = "external_id";

    private ExternalIdExtractor() {
    }

    public static Optional<String> from(SearchResult result) {
        if (result == null || !(result.metadata() instanceof Document doc)) {
            return Optional.empty();
        }
        return Optional.ofNullable(doc.getString(FIELD_EXTERNAL_ID));
    }
}
