package com.tech.n.ai.client.scraper.util;

import org.jsoup.nodes.Element;

/**
 * Jsoup DOM에서 텍스트를 뽑아내는 스크래퍼 공용 헬퍼
 */
public final class ScraperDomUtils {

    private ScraperDomUtils() {
    }

    public static String extractText(Element parent, String selector) {
        if (parent == null) return null;
        Element el = parent.select(selector).first();
        return el != null ? el.text() : null;
    }
}
