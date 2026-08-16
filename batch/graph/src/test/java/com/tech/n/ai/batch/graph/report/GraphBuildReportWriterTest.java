package com.tech.n.ai.batch.graph.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphBuildReportWriter 단위 테스트
 *
 * 임시 디렉터리에만 파일을 쓰므로 외부 연결이 없다.
 */
@DisplayName("GraphBuildReportWriter 단위 테스트")
class GraphBuildReportWriterTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final LocalDateTime EXECUTED_AT = LocalDateTime.of(2026, 8, 16, 13, 0, 0);

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("write - 파일 생성")
    class FileCreation {

        @Test
        @DisplayName("JSON과 마크다운이 같은 타임스탬프로 한 쌍 생긴다")
        void writesJsonAndMarkdownPair() {
            // Given
            GraphBuildReportWriter writer = new GraphBuildReportWriter(
                tempDir.resolve("graph-reports").toString());

            // When
            GraphBuildReportWriter.Written written = writer.write(report(), EXECUTED_AT);

            // Then
            assertThat(written.json()).exists();
            assertThat(written.markdown()).exists();
            assertThat(written.json().getFileName()).hasToString("graph-build-20260816130000.json");
            assertThat(written.markdown().getFileName()).hasToString("graph-sample-20260816130000.md");
        }
    }

    @Nested
    @DisplayName("write - JSON 키 집합")
    class JsonKeySet {

        @Test
        @DisplayName("최상위 키 집합이 고정이다")
        void topLevelKeysAreFixed() throws Exception {
            // Given
            GraphBuildReportWriter writer = new GraphBuildReportWriter(tempDir.toString());

            // When
            JsonNode root = readJson(writer.write(report(), EXECUTED_AT).json());

            // Then
            assertThat(root.propertyNames()).containsExactlyInAnyOrder(
                "schemaVersion", "executedAt", "config", "corpus", "usage",
                "rejectedNodeTypes", "rejectedRelationTypes", "graphSize", "documents");
        }

        @Test
        @DisplayName("값이 없어도 키를 빼지 않고 null·0·빈 값으로 남긴다")
        void keepsKeysWithEmptyValues() throws Exception {
            // Given: 문서를 하나도 안 돈 실행이라 문서당 금액과 전건 환산액이 null이다
            GraphBuildReportWriter writer = new GraphBuildReportWriter(tempDir.toString());

            // When
            JsonNode root = readJson(writer.write(emptyRunReport(), EXECUTED_AT).json());

            // Then
            JsonNode usage = root.get("usage");
            assertThat(usage.has("costPerDocumentUsd")).isTrue();
            assertThat(usage.get("costPerDocumentUsd").isNull()).isTrue();
            assertThat(usage.has("fullCorpusCostUsd")).isTrue();
            assertThat(usage.get("fullCorpusCostUsd").isNull()).isTrue();
            assertThat(root.get("documents")).isEmpty();
            assertThat(root.get("rejectedNodeTypes")).isEmpty();
        }

        @Test
        @DisplayName("표본 검사에 필요한 수치를 config·corpus·usage에 담는다")
        void carriesSampleReviewNumbers() throws Exception {
            // Given
            GraphBuildReportWriter writer = new GraphBuildReportWriter(tempDir.toString());

            // When
            JsonNode root = readJson(writer.write(report(), EXECUTED_AT).json());

            // Then
            assertThat(root.get("config").get("modelName").asString()).isEqualTo("gpt-4o-mini");
            assertThat(root.get("config").get("inputText").asString()).isEqualTo("title-summary");
            assertThat(root.get("corpus").get("publishedDocumentCount").asLong()).isEqualTo(615L);
            assertThat(root.get("usage").get("fullCorpusCostUsd").asDouble()).isEqualTo(6.15);
            assertThat(root.get("documents").get(0).get("inputTokens").asLong()).isEqualTo(900L);
        }
    }

    @Nested
    @DisplayName("write - 마크다운 덤프")
    class MarkdownDump {

        @Test
        @DisplayName("문서마다 원문과 뽑힌 노드·엣지를 나란히 적는다")
        void writesInputTextWithExtraction() throws Exception {
            // Given
            GraphBuildReportWriter writer = new GraphBuildReportWriter(tempDir.toString());

            // When
            String markdown = Files.readString(
                writer.write(report(), EXECUTED_AT).markdown(), UTF_8);

            // Then
            assertThat(markdown)
                .contains("ext-1")
                .contains("OpenAI가 GPT-4o를 공개했다")
                .contains("Model|gpt-4o (GPT-4o)")
                .contains("Company|openai -RELEASED-> Model|gpt-4o")
                .contains("Person 1건");
        }

        @Test
        @DisplayName("문서 끝에 타입별로 정렬한 노드 이름 색인을 붙인다")
        void appendsNodeIndexByType() throws Exception {
            // Given
            GraphBuildReportWriter writer = new GraphBuildReportWriter(tempDir.toString());

            // When
            String markdown = Files.readString(
                writer.write(report(), EXECUTED_AT).markdown(), UTF_8);

            // Then
            assertThat(markdown).contains("## 노드 이름 색인");
            assertThat(markdown.indexOf("## 노드 이름 색인"))
                .isGreaterThan(markdown.indexOf("### 추출에 넣은 원문"));
            assertThat(markdown).contains("### Company").contains("### Model");
        }
    }

    private JsonNode readJson(Path path) throws Exception {
        return OBJECT_MAPPER.readTree(Files.readString(path, UTF_8));
    }

    private GraphBuildReport report() {
        GraphBuildReport.DocumentRow row = new GraphBuildReport.DocumentRow(
            "ext-1", "OPENAI", "MODEL_RELEASE",
            "OpenAI가 GPT-4o를 공개했다",
            900L, 120L, 2, 1,
            List.of("Company|openai (OpenAI)", "Model|gpt-4o (GPT-4o)"),
            List.of("Company|openai -RELEASED-> Model|gpt-4o"),
            Map.of("Person", 1),
            Map.of(),
            null);

        return new GraphBuildReport(
            GraphBuildReport.SCHEMA_VERSION,
            "2026-08-16T13:00:00",
            new GraphBuildReport.Config(
                "gpt-4o-mini", 0.0,
                List.of("Company", "Model"),
                List.of("RELEASED"),
                "title-summary", 0.15, 0.60),
            new GraphBuildReport.Corpus(615L, 20, 1, 0, 0),
            new GraphBuildReport.Usage(900L, 120L, 1L, 0.01, 0.01, 6.15),
            Map.of("Person", 1),
            Map.of(),
            new GraphBuildReport.GraphSize(2L, 1L),
            List.of(row));
    }

    private GraphBuildReport emptyRunReport() {
        GraphBuildReport base = report();
        return new GraphBuildReport(
            base.schemaVersion(), base.executedAt(), base.config(),
            new GraphBuildReport.Corpus(615L, 20, 0, 0, 0),
            new GraphBuildReport.Usage(0L, 0L, 0L, 0.0, null, null),
            Map.of(), Map.of(),
            new GraphBuildReport.GraphSize(0L, 0L),
            List.of());
    }
}
