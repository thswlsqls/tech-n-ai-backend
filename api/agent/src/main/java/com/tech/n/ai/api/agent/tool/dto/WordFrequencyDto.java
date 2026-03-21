package com.tech.n.ai.api.agent.tool.dto;

import java.util.List;

/**
 * 텍스트 빈도 분석 결과 DTO
 *
 * <p>topWords: 단일 키워드(unigram) 빈도 상위 N개
 * <p>topBigrams: 2-gram 키워드 빈도 상위 N개 (향후 확장용)
 */
public record WordFrequencyDto(
    long totalDocuments,
    String period,
    List<WordCount> topWords,
    List<WordCount> topBigrams,
    String message
) {
    /**
     * 메시지 없는 기본 생성자 (정상 응답용)
     */
    public WordFrequencyDto(long totalDocuments, String period,
                            List<WordCount> topWords, List<WordCount> topBigrams) {
        this(totalDocuments, period, topWords, topBigrams, null);
    }

    public record WordCount(
        String word,
        long count
    ) {}
}
