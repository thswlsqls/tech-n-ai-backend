package com.tech.n.ai.batch.eval.goldenset;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * 저장소에 들어 있는 골든셋 JSON을 읽는다.
 */
@Slf4j
@Component
public class GoldenSetLoader {

    private static final String RESOURCE_PATH = "goldenset/emerging-tech-goldenset.json";

    // Jackson 3의 ObjectMapper는 불변이라 JsonMapper.builder()로 만든다
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    public GoldenSet load() {
        try (InputStream in = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            GoldenSet goldenSet = OBJECT_MAPPER.readValue(in, GoldenSet.class);
            log.info("Golden set loaded: version={}, collection={}, items={}",
                goldenSet.version(), goldenSet.collection(), goldenSet.items().size());
            return goldenSet;
        } catch (IOException e) {
            throw new UncheckedIOException("골든셋 JSON을 읽지 못했다: " + RESOURCE_PATH, e);
        }
    }
}
