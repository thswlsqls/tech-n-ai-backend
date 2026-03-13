package com.tech.n.ai.api.bookmark.service;


import com.tech.n.ai.api.bookmark.common.exception.BookmarkNotFoundException;
import com.tech.n.ai.api.bookmark.common.exception.BookmarkValidationException;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkHistoryListRequest;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkHistoryEntity;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkHistoryReaderRepository;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.bookmark.BookmarkWriterRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkHistoryServiceImpl implements BookmarkHistoryService {
    
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    
    private final BookmarkHistoryReaderRepository bookmarkHistoryReaderRepository;
    private final BookmarkReaderRepository bookmarkReaderRepository;
    private final BookmarkWriterRepository bookmarkWriterRepository;
    private final ObjectMapper objectMapper;
    
    @Override
    public Page<BookmarkHistoryEntity> findHistory(String userId, String entityId, BookmarkHistoryListRequest request) {
        Long bookmarkId = parseEntityId(entityId);
        Long currentUserId = parseUserId(userId);
        
        validateBookmarkOwnership(bookmarkId, currentUserId);
        
        // 2. 페이징 처리
        Pageable pageable = PageRequest.of(
            request.page() - 1,
            request.size(),
            Sort.by(Sort.Direction.DESC, "changedAt")
        );
        
        // 3. 필터링 조건에 따라 조회
        if (request.operationType() != null && !request.operationType().isBlank()) {
            // operationType 필터링
            if (request.startDate() != null && request.endDate() != null) {
                // 날짜 범위 필터링
                LocalDateTime startDate = parseDateTime(request.startDate(), "startDate");
                LocalDateTime endDate = parseDateTime(request.endDate(), "endDate");
                return bookmarkHistoryReaderRepository.findByBookmarkIdAndChangedAtBetween(
                    bookmarkId, startDate, endDate, pageable
                );
            } else {
                // operationType만 필터링
                return bookmarkHistoryReaderRepository.findByBookmarkIdAndOperationType(
                    bookmarkId, request.operationType(), pageable
                );
            }
        } else if (request.startDate() != null && request.endDate() != null) {
            // 날짜 범위만 필터링
            LocalDateTime startDate = parseDateTime(request.startDate(), "startDate");
            LocalDateTime endDate = parseDateTime(request.endDate(), "endDate");
            return bookmarkHistoryReaderRepository.findByBookmarkIdAndChangedAtBetween(
                bookmarkId, startDate, endDate, pageable
            );
        } else {
            // 필터링 없이 전체 조회
            return bookmarkHistoryReaderRepository.findByBookmarkId(bookmarkId, pageable);
        }
    }
    
    @Override
    public BookmarkHistoryEntity findHistoryAt(String userId, String entityId, String timestamp) {
        Long bookmarkId = parseEntityId(entityId);
        Long currentUserId = parseUserId(userId);

        validateBookmarkOwnership(bookmarkId, currentUserId);

        // 2. 시점 파싱
        LocalDateTime targetTime = parseDateTime(timestamp, "timestamp");
        
        // 3. 특정 시점 이전의 가장 최근 히스토리 조회
        List<BookmarkHistoryEntity> histories = bookmarkHistoryReaderRepository
            .findTop1ByBookmarkIdAndChangedAtLessThanEqualOrderByChangedAtDesc(bookmarkId, targetTime);
        
        if (histories.isEmpty()) {
            throw new BookmarkNotFoundException("해당 시점의 히스토리를 찾을 수 없습니다: " + timestamp);
        }
        
        return histories.get(0);
    }
    
    @Transactional
    @Override
    public BookmarkEntity restoreFromHistory(String userId, String entityId, String historyId) {
        Long historyIdLong = parseHistoryId(historyId);
        Long bookmarkId = parseEntityId(entityId);
        
        BookmarkHistoryEntity history = findHistoryById(historyIdLong);
        BookmarkEntity bookmark = findBookmarkById(bookmarkId);
        
        Map<String, Object> afterDataMap = parseHistoryData(history, entityId, historyId);
        updateBookmarkFromHistory(bookmark, afterDataMap);
        
        BookmarkEntity updatedBookmark = bookmarkWriterRepository.save( bookmark);
        
        log.info("Bookmark restored from history: bookmarkId={}, historyId={}, userId={}", 
            entityId, historyId, userId);
        
        return updatedBookmark;
    }
    
    private void validateBookmarkOwnership(Long bookmarkId, Long userId) {
        BookmarkEntity bookmark = bookmarkReaderRepository.findById(bookmarkId)
            .orElseThrow(() -> new BookmarkNotFoundException("북마크를 찾을 수 없습니다: " + bookmarkId));
        
        if (!bookmark.isOwnedBy(userId)) {
            throw new UnauthorizedException("본인의 북마크 히스토리만 조회할 수 있습니다.");
        }
    }
    
    private BookmarkHistoryEntity findHistoryById(Long historyId) {
        return bookmarkHistoryReaderRepository.findByHistoryId(historyId)
            .orElseThrow(() -> new BookmarkNotFoundException("히스토리를 찾을 수 없습니다: " + historyId));
    }
    
    private BookmarkEntity findBookmarkById(Long bookmarkId) {
        return bookmarkReaderRepository.findById(bookmarkId)
            .orElseThrow(() -> new BookmarkNotFoundException("북마크를 찾을 수 없습니다: " + bookmarkId));
    }
    
    private Map<String, Object> parseHistoryData(BookmarkHistoryEntity history, String entityId, String historyId) {
        if (history.getAfterData() == null) {
            throw new BookmarkValidationException("히스토리에 복구할 데이터가 없습니다.");
        }
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> afterDataMap = objectMapper.readValue(history.getAfterData(), Map.class);
            return afterDataMap;
        } catch (JacksonException e) {
            log.error("Failed to parse history after_data: bookmarkId={}, historyId={}", entityId, historyId, e);
            throw new BookmarkValidationException("히스토리 데이터 파싱 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    private void updateBookmarkFromHistory(BookmarkEntity bookmark, Map<String, Object> afterDataMap) {
        // UNIQUE 제약조건(user_id, emerging_tech_id)에 포함된 필드는 복원하지 않음
        // 나머지 필드들은 모두 복원
        // 키가 있으면 해당 값(null 포함)으로 복원, 키가 없으면 기존 값 유지

        // 사용자 편집 필드 복구 (null 복원 지원)
        if (afterDataMap.containsKey("tag")) {
            bookmark.setTag((String) afterDataMap.get("tag"));
        }
        if (afterDataMap.containsKey("memo")) {
            bookmark.setMemo((String) afterDataMap.get("memo"));
        }

        // EmergingTech 비정규화 필드 복구 (emergingTechId 제외)
        if (afterDataMap.containsKey("title")) {
            bookmark.setTitle((String) afterDataMap.get("title"));
        }
        if (afterDataMap.containsKey("url")) {
            bookmark.setUrl((String) afterDataMap.get("url"));
        }
        if (afterDataMap.containsKey("provider")) {
            bookmark.setProvider((String) afterDataMap.get("provider"));
        }
        if (afterDataMap.containsKey("summary")) {
            bookmark.setSummary((String) afterDataMap.get("summary"));
        }
        if (afterDataMap.containsKey("publishedAt")) {
            Object publishedAtValue = afterDataMap.get("publishedAt");
            if (publishedAtValue != null) {
                try {
                    bookmark.setPublishedAt(
                        LocalDateTime.parse(publishedAtValue.toString(), ISO_FORMATTER));
                } catch (DateTimeParseException e) {
                    log.warn("History publishedAt 파싱 실패: {}", publishedAtValue, e);
                }
            } else {
                bookmark.setPublishedAt(null);
            }
        }
    }

    private Long parseEntityId(String entityId) {
        try {
            return Long.parseLong(entityId);
        } catch (NumberFormatException e) {
            throw new BookmarkValidationException("유효하지 않은 엔티티 ID 형식입니다: " + entityId);
        }
    }

    private Long parseUserId(String userId) {
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new BookmarkValidationException("유효하지 않은 사용자 ID 형식입니다: " + userId);
        }
    }

    private Long parseHistoryId(String historyId) {
        try {
            return Long.parseLong(historyId);
        } catch (NumberFormatException e) {
            throw new BookmarkValidationException("유효하지 않은 히스토리 ID 형식입니다: " + historyId);
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr, String fieldName) {
        // Try ISO_DATE_TIME format first (e.g., 2024-01-15T10:30:00)
        try {
            return LocalDateTime.parse(dateTimeStr, ISO_FORMATTER);
        } catch (DateTimeParseException e1) {
            // Try ISO_DATE format (e.g., 2024-01-15) and convert to LocalDateTime
            try {
                return java.time.LocalDate.parse(dateTimeStr, DateTimeFormatter.ISO_DATE)
                    .atStartOfDay();
            } catch (DateTimeParseException e2) {
                throw new BookmarkValidationException(
                    String.format("%s 날짜 형식이 유효하지 않습니다: %s (지원 형식: yyyy-MM-dd 또는 yyyy-MM-ddTHH:mm:ss)",
                        fieldName, dateTimeStr)
                );
            }
        }
    }
}
