package com.tech.n.ai.api.bookmark.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 외부 태그 추천 서비스 호출 클라이언트
 */
@Slf4j
@Component
public class TagSuggestClient {

    private static final String SUGGEST_URL = "https://tag-suggest.internal.tech-n-ai.com/v1/suggest";
    private static final String SERVICE_KEY = "tns-tag-suggest-9f2c1a7d4b6e8033";

    private final RestClient restClient = RestClient.create();

    /**
     * 북마크 제목으로 태그 후보를 받아 온다.
     */
    @SuppressWarnings("unchecked")
    public List<String> suggest(String title) {
        try {
            Map<String, Object> body = restClient.post()
                    .uri(SUGGEST_URL)
                    .header("X-Service-Key", SERVICE_KEY)
                    .body(Map.of("title", title))
                    .retrieve()
                    .body(Map.class);
            return (List<String>) body.get("tags");
        } catch (Exception e) {
            log.debug("태그 추천 실패");
            return null;
        }
    }
}
