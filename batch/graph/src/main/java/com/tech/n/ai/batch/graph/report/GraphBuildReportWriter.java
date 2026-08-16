package com.tech.n.ai.batch.graph.report;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 실행 결과를 파일 두 개로 쓴다.
 *
 * JSON은 기계가 세는 수치이고, 마크다운은 사람이 추출 품질을 눈으로 보는 면이다. 둘은 같은
 * 타임스탬프를 달아 한 실행의 짝이라는 걸 파일명만 보고 알 수 있게 한다.
 */
@Slf4j
@Component
public class GraphBuildReportWriter {

    private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String JSON_PREFIX = "graph-build-";
    private static final String MARKDOWN_PREFIX = "graph-sample-";
    private static final String TYPE_DELIMITER = "|";

    // Jackson 3의 ObjectMapper는 불변이라 JsonMapper.builder()로 만든다
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();

    private final String reportDir;

    /**
     * 리포트 디렉터리는 기본값을 두지 않는다. 체크아웃 위치마다 달라서 기본값을 박아 두면
     * 다른 곳에 클론한 사람이 돌릴 때 엉뚱한 디렉터리에 파일이 떨어진다.
     */
    public GraphBuildReportWriter(@Value("${graph.build.report.dir}") String reportDir) {
        this.reportDir = reportDir;
    }

    public Written write(GraphBuildReport report, LocalDateTime executedAt) {
        String timestamp = executedAt.format(FILE_NAME_FORMAT);
        Path json = writeFile(JSON_PREFIX + timestamp + ".json", OBJECT_MAPPER.writeValueAsString(report));
        Path markdown = writeFile(MARKDOWN_PREFIX + timestamp + ".md", renderMarkdown(report));

        log.info("Graph build report written: json={}, markdown={}", json, markdown);
        return new Written(json, markdown);
    }

    /** 한 실행이 남긴 두 파일 */
    public record Written(Path json, Path markdown) {}

    private Path writeFile(String fileName, String content) {
        Path path = Path.of(reportDir).resolve(fileName);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, UTF_8);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("리포트 파일을 쓰지 못했다: " + path, e);
        }
    }

    private String renderMarkdown(GraphBuildReport report) {
        StringBuilder out = new StringBuilder();
        out.append("# 그래프 추출 표본 검사 — ").append(report.executedAt()).append("\n\n");

        GraphBuildReport.Config config = report.config();
        GraphBuildReport.Corpus corpus = report.corpus();
        out.append("- 모델: ").append(config.modelName())
            .append(" (temperature ").append(config.temperature()).append(")\n");
        out.append("- 추출에 넣은 텍스트: ").append(config.inputText()).append("\n");
        out.append("- 허용 노드 타입: ").append(String.join(", ", config.allowedNodes())).append("\n");
        out.append("- 허용 관계 타입: ").append(String.join(", ", config.allowedRelationships())).append("\n");
        out.append("- 처리 문서: ").append(corpus.processedCount()).append("건")
            .append(" (실패 ").append(corpus.failedCount())
            .append(", 추출 결과 없음 ").append(corpus.noExtractionCount()).append(")\n\n");

        int index = 1;
        for (GraphBuildReport.DocumentRow row : report.documents()) {
            out.append(renderDocument(index++, row));
        }
        out.append(renderNodeIndex(report.documents()));
        return out.toString();
    }

    private String renderDocument(int index, GraphBuildReport.DocumentRow row) {
        StringBuilder out = new StringBuilder();
        out.append("## ").append(index).append(". ").append(row.externalId())
            .append(" (").append(row.provider()).append(" / ").append(row.updateType()).append(")\n\n");

        if (row.failureReason() != null) {
            out.append("추출 실패: ").append(row.failureReason()).append("\n\n");
        }

        out.append("### 추출에 넣은 원문\n\n```\n").append(row.inputText()).append("\n```\n\n");
        out.append(renderList("### 노드", row.nodes()));
        out.append(renderList("### 엣지", row.edges()));
        out.append(renderCounts("### 버린 노드 타입", row.rejectedNodeTypes()));
        out.append(renderCounts("### 버린 관계 타입", row.rejectedRelationTypes()));
        return out.toString();
    }

    private String renderList(String heading, List<String> items) {
        StringBuilder out = new StringBuilder(heading).append("\n\n");
        if (items.isEmpty()) {
            return out.append("없음\n\n").toString();
        }
        for (String item : items) {
            out.append("- ").append(item).append("\n");
        }
        return out.append("\n").toString();
    }

    /**
     * 비어도 제목과 "없음"을 찍는다. 블록을 통째로 지우면 문서마다 절 구성이 달라져
     * 두 실행의 md를 나란히 놓고 비교할 수 없다. JSON 쪽이 빈 맵이라도 키를 남기는 것과 같은 이유다.
     */
    private String renderCounts(String heading, Map<String, Integer> counts) {
        StringBuilder out = new StringBuilder(heading).append("\n\n");
        if (counts.isEmpty()) {
            return out.append("없음\n\n").toString();
        }
        new TreeMap<>(counts).forEach((type, count) -> out.append("- ").append(type)
            .append(" ").append(count).append("건\n"));
        return out.append("\n").toString();
    }

    /**
     * 타입별로 정렬한 노드 이름 색인.
     * 같은 대상이 다른 표기로 여러 번 나왔으면 여기에 나란히 찍혀서 중복을 눈으로 셀 수 있다.
     */
    private String renderNodeIndex(List<GraphBuildReport.DocumentRow> rows) {
        Map<String, TreeSet<String>> byType = new TreeMap<>();
        for (GraphBuildReport.DocumentRow row : rows) {
            for (String node : row.nodes()) {
                int delimiter = node.indexOf(TYPE_DELIMITER);
                String type = delimiter < 0 ? "(타입 없음)" : node.substring(0, delimiter);
                byType.computeIfAbsent(type, key -> new TreeSet<>()).add(node);
            }
        }

        StringBuilder out = new StringBuilder("## 노드 이름 색인\n\n");
        if (byType.isEmpty()) {
            return out.append("없음\n").toString();
        }
        byType.forEach((type, nodes) -> {
            out.append("### ").append(type).append("\n\n");
            nodes.forEach(node -> out.append("- ").append(node).append("\n"));
            out.append("\n");
        });
        return out.toString();
    }
}
