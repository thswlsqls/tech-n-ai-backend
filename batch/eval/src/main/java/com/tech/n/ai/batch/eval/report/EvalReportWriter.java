package com.tech.n.ai.batch.eval.report;

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

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 리포트 JSON을 파일로 쓴다.
 */
@Slf4j
@Component
public class EvalReportWriter {

    private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // Jackson 3의 ObjectMapper는 불변이라 JsonMapper.builder()로 만든다
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();

    private final String reportDir;

    public EvalReportWriter(@Value("${eval.report.dir}") String reportDir) {
        this.reportDir = reportDir;
    }

    public Path write(EvalReport report, LocalDateTime executedAt) {
        Path path = Path.of(reportDir).resolve(executedAt.format(FILE_NAME_FORMAT) + ".json");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, OBJECT_MAPPER.writeValueAsString(report), UTF_8);
            log.info("Eval report written: {}", path);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("리포트 파일을 쓰지 못했다: " + path, e);
        }
    }
}
