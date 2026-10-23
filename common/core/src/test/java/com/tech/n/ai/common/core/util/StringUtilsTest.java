package com.tech.n.ai.common.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StringUtils
 */
@DisplayName("StringUtils unit tests")
class StringUtilsTest {

    @Nested
    @DisplayName("isEmpty / isBlank")
    class EmptyAndBlank {

        @Test
        @DisplayName("null and empty string are both empty and blank")
        void nullAndEmpty() {
            assertThat(StringUtils.isEmpty(null)).isTrue();
            assertThat(StringUtils.isEmpty("")).isTrue();
            assertThat(StringUtils.isBlank(null)).isTrue();
            assertThat(StringUtils.isBlank("")).isTrue();
        }

        @Test
        @DisplayName("whitespace-only string is blank but not empty")
        void whitespaceOnly() {
            assertThat(StringUtils.isEmpty("  \t")).isFalse();
            assertThat(StringUtils.isBlank("  \t")).isTrue();
            assertThat(StringUtils.isNotEmpty("  \t")).isTrue();
            assertThat(StringUtils.isNotBlank("  \t")).isFalse();
        }

        @Test
        @DisplayName("string with text is neither empty nor blank")
        void withText() {
            assertThat(StringUtils.isNotEmpty(" a ")).isTrue();
            assertThat(StringUtils.isNotBlank(" a ")).isTrue();
        }
    }

    @Nested
    @DisplayName("trim / trimToNull")
    class Trim {

        @Test
        @DisplayName("trim keeps null as null and strips surrounding whitespace")
        void trim() {
            assertThat(StringUtils.trim(null)).isNull();
            assertThat(StringUtils.trim("  a b  ")).isEqualTo("a b");
        }

        @Test
        @DisplayName("trimToNull returns null when nothing is left after trimming")
        void trimToNull() {
            assertThat(StringUtils.trimToNull(null)).isNull();
            assertThat(StringUtils.trimToNull("   ")).isNull();
            assertThat(StringUtils.trimToNull(" a ")).isEqualTo("a");
        }
    }

    @Nested
    @DisplayName("defaultString")
    class DefaultString {

        @Test
        @DisplayName("null becomes the empty string or the given default")
        void nullInput() {
            assertThat(StringUtils.defaultString(null)).isEmpty();
            assertThat(StringUtils.defaultString(null, "n/a")).isEqualTo("n/a");
        }

        @Test
        @DisplayName("non-null input is returned unchanged, even when blank")
        void nonNullInput() {
            assertThat(StringUtils.defaultString("  ")).isEqualTo("  ");
            assertThat(StringUtils.defaultString("", "n/a")).isEmpty();
        }
    }
}
