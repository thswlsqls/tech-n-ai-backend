package com.tech.n.ai.client.rss.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * RSS 데이터 정제 유틸리티 클래스
 * HTML 태그 제거, 특수 문자 정규화, 내용 요약 생성
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RssDataCleaner {
    
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern MULTIPLE_WHITESPACE_PATTERN = Pattern.compile("\\s+");
    
    /**
     * HTML 태그 제거
     * 
     * @param html HTML이 포함된 문자열
     * @return HTML 태그가 제거된 순수 텍스트
     */
    public String removeHtmlTags(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        
        String cleaned = HTML_TAG_PATTERN.matcher(html).replaceAll("");
        return cleaned.trim();
    }
    
    /**
     * HTML 엔티티 디코딩
     * 기본적인 HTML 엔티티를 일반 문자로 변환
     * 
     * @param text HTML 엔티티가 포함된 문자열
     * @return 디코딩된 문자열
     */
    public String decodeHtmlEntities(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // &amp;는 마지막에 바꾼다. 먼저 바꾸면 "&amp;lt;"가 "&lt;"를 거쳐 "<"까지 두 번 풀린다.
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&#39;", "'")
            .replace("&#8217;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .replace("&amp;", "&");
    }
    
    /**
     * 특수 문자 정규화
     * 연속된 공백을 단일 공백으로 변환
     * 
     * @param text 정규화할 문자열
     * @return 정규화된 문자열
     */
    public String normalizeWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        return MULTIPLE_WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();
    }
    
    /**
     * HTML 태그 제거 및 정규화 통합 처리
     * 
     * @param html HTML이 포함된 문자열
     * @return 정제된 순수 텍스트
     */
    public String cleanHtml(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        
        String cleaned = removeHtmlTags(html);
        cleaned = decodeHtmlEntities(cleaned);
        cleaned = normalizeWhitespace(cleaned);
        
        return cleaned;
    }
    
    /**
     * 내용 요약 생성
     * 지정된 길이로 텍스트를 자르고 말줄임표 추가
     * 
     * @param text 원본 텍스트
     * @param maxLength 최대 길이
     * @return 요약된 텍스트
     */
    public String createSummary(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        if (text.length() <= maxLength) {
            return text;
        }
        
        // 단어 중간에서 자르지 않도록 처리
        String truncated = text.substring(0, maxLength);
        int lastSpace = truncated.lastIndexOf(' ');
        
        if (lastSpace > maxLength * 0.8) {
            truncated = truncated.substring(0, lastSpace);
        }
        
        return truncated + "...";
    }
    
    /**
     * 기본 요약 생성 (200자)
     * 
     * @param text 원본 텍스트
     * @return 요약된 텍스트
     */
    public String createSummary(String text) {
        return createSummary(text, 200);
    }
}
