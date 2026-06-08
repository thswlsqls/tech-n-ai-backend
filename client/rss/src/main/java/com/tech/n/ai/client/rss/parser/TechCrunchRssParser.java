package com.tech.n.ai.client.rss.parser;

import com.tech.n.ai.client.rss.config.RssProperties;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * TechCrunch RSS 피드 파서
 * RSS 2.0 형식의 TechCrunch 피드를 파싱
 */
@Component
public class TechCrunchRssParser extends AbstractRssParser {

    public TechCrunchRssParser(
            @Qualifier("rssWebClientBuilder") WebClient.Builder webClientBuilder,
            RssProperties properties,
            RetryRegistry retryRegistry) {
        super(webClientBuilder, properties, retryRegistry);
    }

    @Override
    protected String getSourceKey() {
        return "techcrunch";
    }

    @Override
    public String getSourceName() {
        return "TechCrunch";
    }

    @Override
    protected String prepareContent(String content) {
        // XML 콘텐츠 정규화 (BOM 제거, XML 선언 이전 문자 제거)
        content = normalizeXmlContent(content);

        // 첫 번째 문자들의 코드포인트 확인 (디버깅용)
        log.info("Feed content length: {}", content.length());
        log.info("Feed content first 200 chars: [{}]",
            content.length() > 200 ? content.substring(0, 200) : content);

        // 첫 5개 문자의 코드포인트 출력
        StringBuilder codePoints = new StringBuilder();
        for (int i = 0; i < Math.min(5, content.length()); i++) {
            codePoints.append(String.format("U+%04X ", (int) content.charAt(i)));
        }
        log.info("First 5 char code points: {}", codePoints.toString());

        // XML 선언으로 시작하지 않으면 경고
        if (!content.startsWith("<?xml")) {
            log.warn("Feed content does NOT start with XML declaration. First char code: U+{}",
                String.format("%04X", (int) content.charAt(0)));
        }

        return content;
    }

    /**
     * XML 콘텐츠 정규화
     * - BOM(Byte Order Mark) 제거
     * - XML 선언 이전의 불필요한 문자 제거
     * - 비정상적인 앞부분 문자 정리
     */
    private String normalizeXmlContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        // 1. UTF-8 BOM 제거
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        // 2. XML 선언(<?xml) 이전의 모든 문자 제거
        int xmlDeclIndex = content.indexOf("<?xml");
        if (xmlDeclIndex > 0) {
            log.warn("Found {} characters before XML declaration, removing them", xmlDeclIndex);
            content = content.substring(xmlDeclIndex);
        } else if (xmlDeclIndex < 0) {
            // XML 선언이 없으면 <rss 태그를 찾음
            int rssIndex = content.indexOf("<rss");
            if (rssIndex > 0) {
                log.warn("No XML declaration found, but found <rss> at index {}. Removing preceding content.", rssIndex);
                content = content.substring(rssIndex);
            }
        }

        // 3. 앞뒤 공백 제거
        return content.trim();
    }
}
