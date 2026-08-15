package com.tech.n.ai.batch.eval.job;

import com.tech.n.ai.api.chatbot.service.IntentClassificationService;
import com.tech.n.ai.api.chatbot.service.dto.Intent;
import com.tech.n.ai.batch.eval.goldenset.GoldenSet;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetItem;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetLoader;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 골든셋이 실제 데이터와 맞는지 확인해 마크다운 표로 남긴다.
 *
 * 세 가지를 본다. 기대 근거 external_id가 컬렉션에 있는지, 그 문서의 status가 PUBLISHED인지,
 * 질문이 의도 분류에서 RAG_REQUIRED로 떨어지는지.
 */
@Slf4j
@Component
public class GoldenSetVerifyTasklet implements Tasklet {

    private static final String COLLECTION_EMERGING_TECHS = "emerging_techs";
    private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final GoldenSetLoader goldenSetLoader;
    private final IntentClassificationService intentService;
    private final MongoTemplate mongoTemplate;
    private final String reportDir;

    public GoldenSetVerifyTasklet(GoldenSetLoader goldenSetLoader,
                                   IntentClassificationService intentService,
                                   MongoTemplate mongoTemplate,
                                   @Value("${eval.report.dir}") String reportDir) {
        this.goldenSetLoader = goldenSetLoader;
        this.intentService = intentService;
        this.mongoTemplate = mongoTemplate;
        this.reportDir = reportDir;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        GoldenSet goldenSet = goldenSetLoader.load();

        List<String> rows = new ArrayList<>();
        for (GoldenSetItem item : goldenSet.items()) {
            Intent intent = intentService.classifyIntent(item.question());
            List<String> missing = new ArrayList<>();
            List<String> notPublished = new ArrayList<>();

            for (String externalId : item.expectedExternalIds()) {
                Document doc = findByExternalId(externalId);
                if (doc == null) {
                    missing.add(externalId);
                } else if (!"PUBLISHED".equals(doc.getString("status"))) {
                    notPublished.add(externalId);
                }
            }

            boolean ok = missing.isEmpty() && notPublished.isEmpty() && intent == Intent.RAG_REQUIRED;
            rows.add("| %s | %s | %d | %s | %s | %s | %s |".formatted(
                item.id(),
                item.type(),
                item.expectedExternalIds().size(),
                missing.isEmpty() ? "-" : String.join(", ", missing),
                notPublished.isEmpty() ? "-" : String.join(", ", notPublished),
                intent,
                ok ? "OK" : "확인 필요"));
        }

        write(goldenSet, rows);
        return RepeatStatus.FINISHED;
    }

    private Document findByExternalId(String externalId) {
        Query query = new Query(Criteria.where("external_id").is(externalId));
        return mongoTemplate.findOne(query, Document.class, COLLECTION_EMERGING_TECHS);
    }

    private void write(GoldenSet goldenSet, List<String> rows) {
        LocalDateTime now = LocalDateTime.now();
        Path path = Path.of(reportDir).resolve("goldenset-verify-" + now.format(FILE_NAME_FORMAT) + ".md");

        StringBuilder body = new StringBuilder();
        body.append("# 골든셋 검증 결과\n\n");
        body.append("- 골든셋 버전: ").append(goldenSet.version()).append('\n');
        body.append("- 컬렉션: ").append(goldenSet.collection()).append('\n');
        body.append("- 확인 시각: ").append(now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
        body.append("| id | 유형 | 기대 근거 수 | 없는 문서 | PUBLISHED 아님 | 의도 분류 | 판정 |\n");
        body.append("| --- | --- | --- | --- | --- | --- | --- |\n");
        rows.forEach(row -> body.append(row).append('\n'));

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, body.toString(), UTF_8);
            log.info("Golden set verify report written: {}", path);
        } catch (IOException e) {
            throw new UncheckedIOException("검증 결과 파일을 쓰지 못했다: " + path, e);
        }
    }
}
