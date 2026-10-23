package com.tech.n.ai.client.rss.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RssDataCleaner 단위 테스트
 */
@DisplayName("RssDataCleaner 단위 테스트")
class RssDataCleanerTest {

    private final RssDataCleaner cleaner = new RssDataCleaner();

    @Test
    @DisplayName("엔티티를 한 번만 푼다 — &amp;lt;는 <가 아니라 &lt;가 된다")
    void decodeHtmlEntities_decodesOnce() {
        assertThat(cleaner.decodeHtmlEntities("&amp;lt;div&amp;gt;"))
            .isEqualTo("&lt;div&gt;");
    }

    @Test
    @DisplayName("체인 끝쪽 엔티티도 한 번만 푼다 — &amp;#8221;는 &#8221;가 된다")
    void decodeHtmlEntities_decodesLastEntityOnce() {
        assertThat(cleaner.decodeHtmlEntities("&amp;#8221;"))
            .isEqualTo("&#8221;");
    }

    @Test
    @DisplayName("한 번 인코딩된 엔티티는 그대로 풀린다")
    void decodeHtmlEntities_decodesSingleEncoded() {
        assertThat(cleaner.decodeHtmlEntities("Q&amp;A &lt;b&gt; &quot;x&quot;"))
            .isEqualTo("Q&A <b> \"x\"");
    }
}
