package com.tech.n.ai.batch.eval.scoring;

import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExternalIdExtractor 단위 테스트
 */
@DisplayName("ExternalIdExtractor 단위 테스트")
class ExternalIdExtractorTest {

    @Nested
    @DisplayName("from - external_id 추출")
    class From {

        @Test
        @DisplayName("metadata가 Document이고 external_id가 있으면 값을 돌려준다")
        void documentWithExternalId_returnsValue() {
            // Given
            Document metadata = new Document("external_id", "openai/openai-python@v1.2.3");
            SearchResult result = SearchResult.builder().metadata(metadata).build();

            // When
            var externalId = ExternalIdExtractor.from(result);

            // Then
            assertThat(externalId).contains("openai/openai-python@v1.2.3");
        }

        @Test
        @DisplayName("Document이지만 external_id가 없으면 빈 값")
        void documentWithoutExternalId_returnsEmpty() {
            // Given
            SearchResult result = SearchResult.builder()
                .metadata(new Document("title", "제목만 있는 문서"))
                .build();

            // When
            var externalId = ExternalIdExtractor.from(result);

            // Then
            assertThat(externalId).isEmpty();
        }

        @Test
        @DisplayName("metadata가 Document가 아니면 빈 값")
        void nonDocumentMetadata_returnsEmpty() {
            // Given
            SearchResult result = SearchResult.builder().metadata("문자열 메타데이터").build();

            // When
            var externalId = ExternalIdExtractor.from(result);

            // Then
            assertThat(externalId).isEmpty();
        }

        @Test
        @DisplayName("metadata가 null이면 빈 값")
        void nullMetadata_returnsEmpty() {
            // Given
            SearchResult result = SearchResult.builder().documentId("doc1").build();

            // When
            var externalId = ExternalIdExtractor.from(result);

            // Then
            assertThat(externalId).isEmpty();
        }
    }
}
