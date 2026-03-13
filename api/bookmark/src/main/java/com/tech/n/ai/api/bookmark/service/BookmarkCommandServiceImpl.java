package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.common.exception.BookmarkDuplicateException;
import com.tech.n.ai.api.bookmark.common.exception.BookmarkItemNotFoundException;
import com.tech.n.ai.api.bookmark.common.exception.BookmarkNotFoundException;
import com.tech.n.ai.api.bookmark.common.exception.BookmarkValidationException;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkCreateRequest;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkUpdateRequest;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.bookmark.BookmarkWriterRepository;
import com.tech.n.ai.domain.mongodb.document.EmergingTechDocument;
import com.tech.n.ai.domain.mongodb.repository.EmergingTechRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkCommandServiceImpl implements BookmarkCommandService {

    private static final int RESTORE_DAYS_LIMIT = 30;

    private final BookmarkReaderRepository bookmarkReaderRepository;
    private final BookmarkWriterRepository bookmarkWriterRepository;
    private final EmergingTechRepository emergingTechRepository;

    @Transactional
    @Override
    public BookmarkEntity saveBookmark(Long userId, BookmarkCreateRequest request) {
        validateDuplicateBookmark(userId, request.emergingTechId());

        EmergingTechDocument emergingTech = findEmergingTech(request.emergingTechId());

        BookmarkEntity bookmark = createBookmark(userId, request, emergingTech);
        BookmarkEntity savedBookmark = bookmarkWriterRepository.save(bookmark);

        log.info("Bookmark created: id={}, userId={}, emergingTechId={}",
            savedBookmark.getId(), userId, request.emergingTechId());

        return savedBookmark;
    }

    private void validateDuplicateBookmark(Long userId, String emergingTechId) {
        bookmarkReaderRepository.findByUserIdAndEmergingTechIdAndIsDeletedFalse(
            userId, emergingTechId
        ).ifPresent(bookmark -> {
            throw new BookmarkDuplicateException("이미 존재하는 북마크입니다.");
        });
    }

    // MongoDB에서 EmergingTechDocument 조회
    private EmergingTechDocument findEmergingTech(String emergingTechId) {
        try {
            ObjectId objectId = new ObjectId(emergingTechId);
            return emergingTechRepository.findById(objectId)
                .orElseThrow(() -> new BookmarkItemNotFoundException(
                    "EmergingTech를 찾을 수 없습니다: " + emergingTechId));
        } catch (IllegalArgumentException e) {
            throw new BookmarkValidationException(
                "유효하지 않은 EmergingTech ID 형식입니다: " + emergingTechId);
        }
    }

    private BookmarkEntity createBookmark(Long userId, BookmarkCreateRequest request,
                                          EmergingTechDocument emergingTech) {
        BookmarkEntity bookmark = new BookmarkEntity();
        bookmark.setUserId(userId);
        bookmark.setEmergingTechId(request.emergingTechId());
        bookmark.setTitle(emergingTech.getTitle());
        bookmark.setUrl(emergingTech.getUrl());
        bookmark.setProvider(emergingTech.getProvider());
        bookmark.setSummary(emergingTech.getSummary());
        bookmark.setPublishedAt(emergingTech.getPublishedAt());
        bookmark.setTagsAsList(request.tags());
        bookmark.setMemo(request.memo());
        return bookmark;
    }

    @Transactional
    @Override
    public BookmarkEntity updateBookmark(Long userId, String bookmarkTsid, BookmarkUpdateRequest request) {
        Long bookmarkId = parseBookmarkId(bookmarkTsid);
        BookmarkEntity bookmark = findAndValidateBookmark(userId, bookmarkId);

        bookmark.updateContent(request.tags(), request.memo());
        BookmarkEntity updatedBookmark = bookmarkWriterRepository.save(bookmark);

        log.info("Bookmark updated: id={}, userId={}", bookmarkId, userId);

        return updatedBookmark;
    }

    private Long parseBookmarkId(String bookmarkTsid) {
        try {
            return Long.parseLong(bookmarkTsid);
        } catch (NumberFormatException e) {
            throw new BookmarkValidationException("유효하지 않은 북마크 ID 형식입니다: " + bookmarkTsid);
        }
    }

    private BookmarkEntity findAndValidateBookmark(Long userId, Long bookmarkId) {
        BookmarkEntity bookmark = bookmarkReaderRepository.findById(bookmarkId)
            .orElseThrow(() -> new BookmarkNotFoundException("북마크를 찾을 수 없습니다: " + bookmarkId));

        if (!bookmark.isOwnedBy(userId)) {
            throw new UnauthorizedException("본인의 북마크만 접근할 수 있습니다.");
        }

        if (Boolean.TRUE.equals(bookmark.getIsDeleted())) {
            throw new BookmarkNotFoundException("삭제된 북마크입니다.");
        }

        return bookmark;
    }

    @Transactional
    @Override
    public void deleteBookmark(Long userId, String bookmarkTsid) {
        Long bookmarkId = parseBookmarkId(bookmarkTsid);
        BookmarkEntity bookmark = findAndValidateBookmark(userId, bookmarkId);

        bookmark.setDeletedBy(userId);
        bookmarkWriterRepository.delete(bookmark);

        log.info("Bookmark deleted: id={}, userId={}", bookmarkId, userId);
    }

    @Transactional
    @Override
    public BookmarkEntity restoreBookmark(Long userId, String bookmarkTsid) {
        Long bookmarkId = parseBookmarkId(bookmarkTsid);
        BookmarkEntity bookmark = findDeletedBookmark(userId, bookmarkId);
        validateRestorePeriod(bookmark);

        bookmark.restore();
        BookmarkEntity restoredBookmark = bookmarkWriterRepository.save(bookmark);

        log.info("Bookmark restored: id={}, userId={}", bookmarkId, userId);

        return restoredBookmark;
    }

    private BookmarkEntity findDeletedBookmark(Long userId, Long bookmarkId) {
        BookmarkEntity bookmark = bookmarkReaderRepository.findById(bookmarkId)
            .orElseThrow(() -> new BookmarkNotFoundException("북마크를 찾을 수 없습니다: " + bookmarkId));

        if (!bookmark.isOwnedBy(userId)) {
            throw new UnauthorizedException("본인의 북마크만 접근할 수 있습니다.");
        }

        if (!Boolean.TRUE.equals(bookmark.getIsDeleted())) {
            throw new BookmarkValidationException("삭제되지 않은 북마크입니다.");
        }

        return bookmark;
    }

    private void validateRestorePeriod(BookmarkEntity bookmark) {
        if (!bookmark.canBeRestored(RESTORE_DAYS_LIMIT)) {
            throw new BookmarkValidationException(
                "복구 가능 기간이 지났습니다. (" + RESTORE_DAYS_LIMIT + "일 이내만 복구 가능)");
        }
    }
}
