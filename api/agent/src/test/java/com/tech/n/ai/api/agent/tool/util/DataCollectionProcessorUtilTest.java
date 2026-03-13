package com.tech.n.ai.api.agent.tool.util;

import com.tech.n.ai.domain.mongodb.enums.EmergingTechType;
import com.tech.n.ai.domain.mongodb.enums.TechProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DataCollectionProcessorUtil 단위 테스트
 */
@DisplayName("DataCollectionProcessorUtil 단위 테스트")
class DataCollectionProcessorUtilTest {

    // ========== classifyUpdateType 테스트 ==========

    @Nested
    @DisplayName("classifyUpdateType")
    class ClassifyUpdateType {

        @Test
        @DisplayName("API 키워드 포함 시 API_UPDATE")
        void classifyUpdateType_API() {
            assertThat(DataCollectionProcessorUtil.classifyUpdateType("New API endpoint", null))
                .isEqualTo(EmergingTechType.API_UPDATE);
        }

        @Test
        @DisplayName("카테고리에 api 포함 시 API_UPDATE")
        void classifyUpdateType_API_카테고리() {
            assertThat(DataCollectionProcessorUtil.classifyUpdateType("Some Update", "api changes"))
                .isEqualTo(EmergingTechType.API_UPDATE);
        }

        @Test
        @DisplayName("release 키워드 포함 시 MODEL_RELEASE")
        void classifyUpdateType_release() {
            assertThat(DataCollectionProcessorUtil.classifyUpdateType("GPT-5 release notes", null))
                .isEqualTo(EmergingTechType.MODEL_RELEASE);
        }

        @Test
        @DisplayName("introducing 키워드 포함 시 MODEL_RELEASE")
        void classifyUpdateType_introducing() {
            assertThat(DataCollectionProcessorUtil.classifyUpdateType("Introducing Claude 4", null))
                .isEqualTo(EmergingTechType.MODEL_RELEASE);
        }

        @Test
        @DisplayName("model 키워드 포함 시 MODEL_RELEASE")
        void classifyUpdateType_model() {
            assertThat(DataCollectionProcessorUtil.classifyUpdateType("New model available", null))
                .isEqualTo(EmergingTechType.MODEL_RELEASE);
        }

        @Test
        @DisplayName("launch 키워드 포함 시 PRODUCT_LAUNCH")
        void classifyUpdateType_launch() {
            assertThat(DataCollectionProcessorUtil.classifyUpdateType("Product launch today", null))
                .isEqualTo(EmergingTechType.PRODUCT_LAUNCH);
        }

        @Test
        @DisplayName("platform 키워드 포함 시 PLATFORM_UPDATE")
        void classifyUpdateType_platform() {
            assertThat(DataCollectionProcessorUtil.classifyUpdateType("Platform improvements", null))
                .isEqualTo(EmergingTechType.PLATFORM_UPDATE);
        }

        @Test
        @DisplayName("update 키워드 포함 시 PLATFORM_UPDATE")
        void classifyUpdateType_update() {
            assertThat(DataCollectionProcessorUtil.classifyUpdateType("System update deployed", null))
                .isEqualTo(EmergingTechType.PLATFORM_UPDATE);
        }

        @Test
        @DisplayName("매칭 없으면 BLOG_POST")
        void classifyUpdateType_기본() {
            assertThat(DataCollectionProcessorUtil.classifyUpdateType("Thoughts on AI", null))
                .isEqualTo(EmergingTechType.BLOG_POST);
        }

        @Test
        @DisplayName("null 카테고리 처리")
        void classifyUpdateType_null카테고리() {
            assertThat(DataCollectionProcessorUtil.classifyUpdateType("General news", null))
                .isEqualTo(EmergingTechType.BLOG_POST);
        }

        @Test
        @DisplayName("API 키워드가 title과 category 모두에 있으면 API_UPDATE (우선순위)")
        void classifyUpdateType_API우선순위() {
            assertThat(DataCollectionProcessorUtil.classifyUpdateType("API release for new model", "api"))
                .isEqualTo(EmergingTechType.API_UPDATE);
        }
    }

    // ========== resolveRssProvider 테스트 ==========

    @Nested
    @DisplayName("resolveRssProvider")
    class ResolveRssProvider {

        @Test
        @DisplayName("openai.com URL → OPENAI")
        void resolveRssProvider_openai() {
            assertThat(DataCollectionProcessorUtil.resolveRssProvider("https://openai.com/blog/rss"))
                .isEqualTo(TechProvider.OPENAI);
        }

        @Test
        @DisplayName("blog.google URL → GOOGLE")
        void resolveRssProvider_google() {
            assertThat(DataCollectionProcessorUtil.resolveRssProvider("https://blog.google/ai/feed"))
                .isEqualTo(TechProvider.GOOGLE);
        }

        @Test
        @DisplayName("알 수 없는 URL → null")
        void resolveRssProvider_미지원() {
            assertThat(DataCollectionProcessorUtil.resolveRssProvider("https://example.com/feed"))
                .isNull();
        }
    }

    // ========== parsePublishedAt 테스트 ==========

    @Nested
    @DisplayName("parsePublishedAt")
    class ParsePublishedAt {

        @Test
        @DisplayName("정상 ISO 날짜 파싱")
        void parsePublishedAt_정상() {
            LocalDateTime result = DataCollectionProcessorUtil.parsePublishedAt("2026-03-13T10:30:00");
            assertThat(result).isEqualTo(LocalDateTime.of(2026, 3, 13, 10, 30, 0));
        }

        @Test
        @DisplayName("null 입력 시 현재 시각 반환")
        void parsePublishedAt_null() {
            LocalDateTime result = DataCollectionProcessorUtil.parsePublishedAt(null);
            assertThat(result).isNotNull();
            assertThat(result).isAfter(LocalDateTime.now().minusMinutes(1));
        }

        @Test
        @DisplayName("빈 문자열 시 현재 시각 반환")
        void parsePublishedAt_빈값() {
            LocalDateTime result = DataCollectionProcessorUtil.parsePublishedAt("");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("잘못된 형식 시 현재 시각 반환")
        void parsePublishedAt_잘못된형식() {
            LocalDateTime result = DataCollectionProcessorUtil.parsePublishedAt("not-a-date");
            assertThat(result).isNotNull();
            assertThat(result).isAfter(LocalDateTime.now().minusMinutes(1));
        }
    }

    // ========== truncateHtml 테스트 ==========

    @Nested
    @DisplayName("truncateHtml")
    class TruncateHtml {

        @Test
        @DisplayName("HTML 태그 제거")
        void truncateHtml_태그제거() {
            String result = DataCollectionProcessorUtil.truncateHtml(
                "<p>Hello <b>World</b></p>", 500);
            assertThat(result).isEqualTo("Hello World");
        }

        @Test
        @DisplayName("null 입력 시 빈 문자열")
        void truncateHtml_null() {
            assertThat(DataCollectionProcessorUtil.truncateHtml(null, 500)).isEqualTo("");
        }

        @Test
        @DisplayName("maxLength 초과 시 절단 + '...'")
        void truncateHtml_절단() {
            String input = "A".repeat(600);
            String result = DataCollectionProcessorUtil.truncateHtml(input, 500);
            assertThat(result).hasSize(503); // 500 + "..."
            assertThat(result).endsWith("...");
        }

        @Test
        @DisplayName("maxLength 이하 시 그대로")
        void truncateHtml_미초과() {
            String result = DataCollectionProcessorUtil.truncateHtml("짧은 텍스트", 500);
            assertThat(result).isEqualTo("짧은 텍스트");
        }
    }

    // ========== generateHash 테스트 ==========

    @Nested
    @DisplayName("generateHash")
    class GenerateHash {

        @Test
        @DisplayName("동일 입력 → 동일 해시")
        void generateHash_결정적() {
            String hash1 = DataCollectionProcessorUtil.generateHash("https://example.com/page");
            String hash2 = DataCollectionProcessorUtil.generateHash("https://example.com/page");
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("다른 입력 → 다른 해시")
        void generateHash_다른입력() {
            String hash1 = DataCollectionProcessorUtil.generateHash("input1");
            String hash2 = DataCollectionProcessorUtil.generateHash("input2");
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("해시 길이 16자")
        void generateHash_길이() {
            String hash = DataCollectionProcessorUtil.generateHash("test");
            assertThat(hash).hasSize(16);
        }
    }

    // ========== extractTags 테스트 ==========

    @Nested
    @DisplayName("extractTags")
    class ExtractTags {

        @Test
        @DisplayName("콤마 구분 문자열 파싱")
        void extractTags_정상() {
            List<String> tags = DataCollectionProcessorUtil.extractTags("AI, Machine Learning, NLP");
            assertThat(tags).containsExactly("AI", "Machine Learning", "NLP");
        }

        @Test
        @DisplayName("null 입력 → 빈 리스트")
        void extractTags_null() {
            assertThat(DataCollectionProcessorUtil.extractTags(null)).isEmpty();
        }

        @Test
        @DisplayName("빈 문자열 → 빈 리스트")
        void extractTags_빈값() {
            assertThat(DataCollectionProcessorUtil.extractTags("")).isEmpty();
        }

        @Test
        @DisplayName("공백만 있는 항목 제외")
        void extractTags_공백제외() {
            List<String> tags = DataCollectionProcessorUtil.extractTags("AI,  , NLP");
            assertThat(tags).containsExactly("AI", "NLP");
        }

        @Test
        @DisplayName("단일 태그")
        void extractTags_단일() {
            List<String> tags = DataCollectionProcessorUtil.extractTags("AI");
            assertThat(tags).containsExactly("AI");
        }
    }
}
