package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.WebSearchDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WebSearchServiceImpl 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSearchServiceImpl 단위 테스트")
class WebSearchServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    private WebSearchServiceImpl webSearchService;

    @BeforeEach
    void setUp() {
        webSearchService = new WebSearchServiceImpl(restTemplate);
        ReflectionTestUtils.setField(webSearchService, "enabled", true);
        ReflectionTestUtils.setField(webSearchService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(webSearchService, "searchEngineId", "test-cx");
        ReflectionTestUtils.setField(webSearchService, "defaultMaxResults", 5);
    }

    // ========== search 테스트 ==========

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("정상 검색 - 결과 반환")
        @SuppressWarnings("unchecked")
        void search_정상() {
            // Given
            Map<String, Object> item = Map.of(
                "title", "AI 뉴스",
                "link", "https://example.com/ai",
                "snippet", "최신 AI 동향",
                "displayLink", "example.com"
            );
            Map<String, Object> response = Map.of("items", List.of(item));
            when(restTemplate.getForObject(anyString(), any(Class.class), any(), any(), any(), any()))
                .thenReturn(response);

            // When
            List<WebSearchDocument> results = webSearchService.search("AI 최신 뉴스");

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).title()).isEqualTo("AI 뉴스");
            assertThat(results.get(0).url()).isEqualTo("https://example.com/ai");
            assertThat(results.get(0).snippet()).isEqualTo("최신 AI 동향");
            assertThat(results.get(0).source()).isEqualTo("example.com");
        }

        @Test
        @DisplayName("비활성화 시 빈 리스트 반환")
        void search_비활성화() {
            // Given
            ReflectionTestUtils.setField(webSearchService, "enabled", false);

            // When
            List<WebSearchDocument> results = webSearchService.search("AI 뉴스");

            // Then
            assertThat(results).isEmpty();
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("API 키 미설정 시 빈 리스트 반환")
        void search_API키미설정() {
            // Given
            ReflectionTestUtils.setField(webSearchService, "apiKey", "");

            // When
            List<WebSearchDocument> results = webSearchService.search("AI 뉴스");

            // Then
            assertThat(results).isEmpty();
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("검색엔진 ID 미설정 시 빈 리스트 반환")
        void search_검색엔진ID미설정() {
            // Given
            ReflectionTestUtils.setField(webSearchService, "searchEngineId", "");

            // When
            List<WebSearchDocument> results = webSearchService.search("AI 뉴스");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("API 응답에 items 없으면 빈 리스트")
        @SuppressWarnings("unchecked")
        void search_items없음() {
            // Given
            Map<String, Object> response = Map.of("searchInformation", Map.of("totalResults", "0"));
            when(restTemplate.getForObject(anyString(), any(Class.class), any(), any(), any(), any()))
                .thenReturn(response);

            // When
            List<WebSearchDocument> results = webSearchService.search("없는 정보");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("API 응답 null이면 빈 리스트")
        @SuppressWarnings("unchecked")
        void search_null응답() {
            // Given
            when(restTemplate.getForObject(anyString(), any(Class.class), any(), any(), any(), any()))
                .thenReturn(null);

            // When
            List<WebSearchDocument> results = webSearchService.search("테스트");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("RestTemplate 예외 시 빈 리스트")
        @SuppressWarnings("unchecked")
        void search_예외() {
            // Given
            when(restTemplate.getForObject(anyString(), any(Class.class), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("API 호출 실패"));

            // When
            List<WebSearchDocument> results = webSearchService.search("AI");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("기본 maxResults로 검색")
        @SuppressWarnings("unchecked")
        void search_기본maxResults() {
            // Given
            Map<String, Object> response = Map.of("items", List.of());
            when(restTemplate.getForObject(anyString(), any(Class.class), any(), any(), any(), eq(5)))
                .thenReturn(response);

            // When
            webSearchService.search("AI");

            // Then
            verify(restTemplate).getForObject(anyString(), any(Class.class), any(), any(), eq("AI"), eq(5));
        }
    }
}
