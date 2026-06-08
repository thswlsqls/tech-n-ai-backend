package com.tech.n.ai.common.core.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 날짜/시간 유틸리티 클래스
 * 
 * 참고: Java 8+ java.time 패키지 공식 문서
 * https://docs.oracle.com/javase/8/docs/api/java/time/package-summary.html
 */
public final class DateUtils {
    
    private DateUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * 기본 날짜 포맷: yyyy-MM-dd
     */
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    /**
     * 기본 날짜시간 포맷: yyyy-MM-dd'T'HH:mm:ss
     */
    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    
    /**
     * ISO 날짜시간 포맷: yyyy-MM-dd'T'HH:mm:ss.SSS
     */
    public static final DateTimeFormatter ISO_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    
    /**
     * LocalDate를 문자열로 포맷팅
     * 
     * @param date 날짜
     * @return 포맷된 문자열 (yyyy-MM-dd)
     */
    public static String format(LocalDate date) {
        return format(date, DATE_FORMATTER);
    }
    
    /**
     * LocalDate를 지정된 포맷으로 문자열로 포맷팅
     * 
     * @param date 날짜
     * @param formatter 포맷터
     * @return 포맷된 문자열
     */
    public static String format(LocalDate date, DateTimeFormatter formatter) {
        if (date == null) {
            return null;
        }
        return date.format(formatter);
    }
    
    /**
     * LocalDateTime을 문자열로 포맷팅
     * 
     * @param dateTime 날짜시간
     * @return 포맷된 문자열 (yyyy-MM-dd'T'HH:mm:ss)
     */
    public static String format(LocalDateTime dateTime) {
        return format(dateTime, DATETIME_FORMATTER);
    }
    
    /**
     * LocalDateTime을 지정된 포맷으로 문자열로 포맷팅
     * 
     * @param dateTime 날짜시간
     * @param formatter 포맷터
     * @return 포맷된 문자열
     */
    public static String format(LocalDateTime dateTime, DateTimeFormatter formatter) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(formatter);
    }
    
    /**
     * 문자열을 LocalDate로 파싱
     * 
     * @param dateStr 날짜 문자열 (yyyy-MM-dd)
     * @return LocalDate
     * @throws DateTimeParseException 파싱 실패 시
     */
    public static LocalDate parseDate(String dateStr) {
        return parseDate(dateStr, DATE_FORMATTER);
    }
    
    /**
     * 문자열을 지정된 포맷으로 LocalDate로 파싱
     * 
     * @param dateStr 날짜 문자열
     * @param formatter 포맷터
     * @return LocalDate
     * @throws DateTimeParseException 파싱 실패 시
     */
    public static LocalDate parseDate(String dateStr, DateTimeFormatter formatter) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        return LocalDate.parse(dateStr, formatter);
    }
    
    /**
     * 문자열을 LocalDateTime으로 파싱
     * 
     * @param dateTimeStr 날짜시간 문자열 (yyyy-MM-dd'T'HH:mm:ss)
     * @return LocalDateTime
     * @throws DateTimeParseException 파싱 실패 시
     */
    public static LocalDateTime parseDateTime(String dateTimeStr) {
        return parseDateTime(dateTimeStr, DATETIME_FORMATTER);
    }
    
    /**
     * 문자열을 지정된 포맷으로 LocalDateTime으로 파싱
     * 
     * @param dateTimeStr 날짜시간 문자열
     * @param formatter 포맷터
     * @return LocalDateTime
     * @throws DateTimeParseException 파싱 실패 시
     */
    public static LocalDateTime parseDateTime(String dateTimeStr, DateTimeFormatter formatter) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(dateTimeStr, formatter);
    }
}

