package com.tech.n.ai.client.scraper.util;

import com.tech.n.ai.client.scraper.dto.ScrapedContestItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 스크래핑된 데이터 정제 유틸리티 클래스
 * HTML 태그 제거, 날짜 형식 정규화, 중복 항목 제거
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ScrapedDataCleaner {
    
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
     * 날짜 형식 정규화
     * 다양한 날짜 형식을 LocalDateTime으로 파싱 시도
     * 
     * @param dateText 날짜 문자열
     * @return 파싱된 LocalDateTime 또는 null
     */
    public LocalDateTime normalizeDate(String dateText) {
        if (dateText == null || dateText.isEmpty()) {
            return null;
        }
        
        // 일반적인 날짜 형식들 시도
        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        };
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(dateText.trim(), formatter);
            } catch (DateTimeParseException e) {
                // 다음 형식 시도
            }
        }
        
        log.debug("Failed to parse date: {}", dateText);
        return null;
    }
    
    /**
     * ScrapedContestItem 리스트 정제
     * HTML 태그 제거 및 날짜 정규화
     * 
     * @param items 정제할 아이템 리스트
     * @return 정제된 아이템 리스트
     */
    public List<ScrapedContestItem> clean(List<ScrapedContestItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        
        return items.stream()
            .map(item -> {
                if (item == null) {
                    return null;
                }
                
                // HTML 태그 제거 및 정규화
                String cleanedTitle = cleanHtml(item.title());
                String cleanedDescription = item.description() != null ? cleanHtml(item.description()) : null;
                String cleanedOrganizer = item.organizer() != null ? cleanHtml(item.organizer()) : null;
                String cleanedLocation = item.location() != null ? cleanHtml(item.location()) : null;
                String cleanedCategory = item.category() != null ? cleanHtml(item.category()) : null;
                String cleanedPrize = item.prize() != null ? cleanHtml(item.prize()) : null;
                
                // 날짜 정규화는 이미 LocalDateTime이므로 필요 없음
                // 만약 날짜가 문자열로 들어온다면 normalizeDate() 사용
                
                return ScrapedContestItem.builder()
                    .title(cleanedTitle)
                    .url(item.url())
                    .description(cleanedDescription)
                    .startDate(item.startDate())
                    .endDate(item.endDate())
                    .organizer(cleanedOrganizer)
                    .location(cleanedLocation)
                    .category(cleanedCategory)
                    .prize(cleanedPrize)
                    .imageUrl(item.imageUrl())
                    .build();
            })
            .filter(item -> item != null)
            .collect(Collectors.toList());
    }
}
