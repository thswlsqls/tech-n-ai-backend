package com.tech.n.ai.api.agent.tool.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TextTruncator 단위 테스트
 */
@DisplayName("TextTruncator 단위 테스트")
class TextTruncatorTest {

    @Test
    @DisplayName("최대 길이 이하면 그대로 돌려준다")
    void truncate_짧은_텍스트() {
        assertThat(TextTruncator.truncate("abc", 3)).isEqualTo("abc");
    }

    @Test
    @DisplayName("최대 길이를 넘으면 자르고 말줄임표를 붙인다")
    void truncate_긴_텍스트() {
        assertThat(TextTruncator.truncate("abcdef", 3)).isEqualTo("abc...");
    }

    @Test
    @DisplayName("자르는 위치가 이모지 가운데면 이모지 앞에서 자른다")
    void truncate_이모지_경계() {
        // "😀"는 char 두 개(index 2의 앞 반쪽 \uD83D, index 3의 뒤 반쪽 \uDE00)다. 3에서 자르면 앞 반쪽만 남는다
        assertThat(TextTruncator.truncate("ab😀cd", 3)).isEqualTo("ab...");
        // 4에서 자르면 이모지가 통째로 들어오므로 그대로 둔다
        assertThat(TextTruncator.truncate("ab😀cd", 4)).isEqualTo("ab😀...");
    }

    @Test
    @DisplayName("null이면 빈 문자열을 돌려준다")
    void truncate_null() {
        assertThat(TextTruncator.truncate(null, 3)).isEmpty();
    }
}
