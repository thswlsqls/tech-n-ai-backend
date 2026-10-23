package com.tech.n.ai.api.agent.tool.util;

/**
 * 텍스트 절단 유틸리티
 */
public final class TextTruncator {

    private TextTruncator() {
    }

    /**
     * 텍스트를 지정된 최대 길이로 절단
     *
     * @param text 원본 텍스트
     * @param maxLength 최대 길이
     * @return 절단된 텍스트 (null인 경우 빈 문자열 반환)
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        // 이모지처럼 char 두 개로 된 문자를 반으로 자르지 않도록 한 칸 앞에서 자른다
        int end = maxLength;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "...";
    }
}
