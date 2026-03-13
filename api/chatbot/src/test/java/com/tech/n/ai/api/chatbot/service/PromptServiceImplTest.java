package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.api.chatbot.service.dto.WebSearchDocument;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PromptServiceImpl 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PromptServiceImpl 단위 테스트")
class PromptServiceImplTest {

    @Mock
    private TokenService tokenService;

    private PromptServiceImpl promptService;

    @BeforeEach
    void setUp() {
        promptService = new PromptServiceImpl(tokenService);
        ReflectionTestUtils.setField(promptService, "maxContextTokens", 3000);
    }

    // ========== buildPrompt 테스트 ==========

    @Nested
    @DisplayName("buildPrompt")
    class BuildPrompt {

        @Test
        @DisplayName("정상 프롬프트 생성 - 검색 결과 포함")
        void buildPrompt_정상() {
            // Given
            List<SearchResult> results = List.of(
                SearchResult.builder()
                    .documentId("doc1")
                    .text("AI 트렌드 내용")
                    .score(0.9)
                    .collectionType("EMERGING_TECH")
                    .build()
            );
            when(tokenService.truncateResults(anyList(), anyInt())).thenReturn(results);

            // When
            String prompt = promptService.buildPrompt("AI 트렌드", results);

            // Then
            assertThat(prompt).contains("AI 트렌드");
            assertThat(prompt).contains("AI 트렌드 내용");
            assertThat(prompt).contains("[문서 1]");
            verify(tokenService).validateInputTokens(prompt);
        }

        @Test
        @DisplayName("메타데이터 포함 프롬프트 생성")
        void buildPrompt_메타데이터포함() {
            // Given
            Document metadata = new Document()
                .append("title", "OpenAI GPT-5")
                .append("provider", "OPENAI")
                .append("published_at", "2026-03-13")
                .append("url", "https://openai.com/blog/gpt5");

            List<SearchResult> results = List.of(
                SearchResult.builder()
                    .documentId("doc1")
                    .text("GPT-5 내용")
                    .score(0.9)
                    .metadata(metadata)
                    .build()
            );
            when(tokenService.truncateResults(anyList(), anyInt())).thenReturn(results);

            // When
            String prompt = promptService.buildPrompt("GPT-5", results);

            // Then
            assertThat(prompt).contains("OpenAI GPT-5");
            assertThat(prompt).contains("OPENAI");
            assertThat(prompt).contains("2026-03-13");
            assertThat(prompt).contains("https://openai.com/blog/gpt5");
        }

        @Test
        @DisplayName("빈 검색 결과")
        void buildPrompt_빈결과() {
            // Given
            when(tokenService.truncateResults(anyList(), anyInt())).thenReturn(Collections.emptyList());

            // When
            String prompt = promptService.buildPrompt("질문", Collections.emptyList());

            // Then
            assertThat(prompt).contains("질문");
            assertThat(prompt).doesNotContain("[문서 1]");
        }

        @Test
        @DisplayName("토큰 검증 호출 확인")
        void buildPrompt_토큰검증() {
            // Given
            when(tokenService.truncateResults(anyList(), anyInt())).thenReturn(Collections.emptyList());

            // When
            promptService.buildPrompt("질문", Collections.emptyList());

            // Then
            verify(tokenService).validateInputTokens(anyString());
        }
    }

    // ========== buildWebSearchPrompt 테스트 ==========

    @Nested
    @DisplayName("buildWebSearchPrompt")
    class BuildWebSearchPrompt {

        @Test
        @DisplayName("정상 웹 검색 프롬프트 생성")
        void buildWebSearchPrompt_정상() {
            // Given
            List<WebSearchDocument> webResults = List.of(
                new WebSearchDocument("AI 뉴스 제목", "https://example.com", "AI 뉴스 요약", "example.com"),
                new WebSearchDocument("기술 동향", "https://tech.com", "기술 동향 요약", "tech.com")
            );

            // When
            String prompt = promptService.buildWebSearchPrompt("AI 최신 뉴스", webResults);

            // Then
            assertThat(prompt).contains("AI 최신 뉴스");
            assertThat(prompt).contains("[결과 1] AI 뉴스 제목");
            assertThat(prompt).contains("[결과 2] 기술 동향");
            assertThat(prompt).contains("https://example.com");
            assertThat(prompt).contains("AI 뉴스 요약");
            verify(tokenService).validateInputTokens(prompt);
        }

        @Test
        @DisplayName("빈 웹 검색 결과")
        void buildWebSearchPrompt_빈결과() {
            // When
            String prompt = promptService.buildWebSearchPrompt("질문", Collections.emptyList());

            // Then
            assertThat(prompt).contains("질문");
            assertThat(prompt).doesNotContain("[결과 1]");
        }
    }
}
