package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.common.exception.BookmarkNotFoundException;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkListRequest;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkSearchRequest;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkReaderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Bookmark Query Service 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkQueryServiceImpl implements BookmarkQueryService {
    
    private final BookmarkReaderRepository bookmarkReaderRepository;
    
    @Override
    public Page<BookmarkEntity> findBookmarks(Long userId, BookmarkListRequest request) {
        Pageable pageable = PageRequest.of(
            request.page() - 1,
            request.size(),
            parseSort(request.sort())
        );
        
        Specification<BookmarkEntity> spec = Specification.<BookmarkEntity>unrestricted()
            .and((root, query, cb) -> cb.equal(root.get("userId"), userId))
            .and((root, query, cb) -> cb.equal(root.get("isDeleted"), false));

        if (request.provider() != null && !request.provider().isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("provider"), request.provider()));
        }

        return bookmarkReaderRepository.findAll(spec, pageable);
    }
    
    @Override
    public BookmarkEntity findBookmarkById(Long userId, Long id) {
        BookmarkEntity entity = bookmarkReaderRepository.findById(id)
            .orElseThrow(() -> new BookmarkNotFoundException("북마크를 찾을 수 없습니다: " + id));
        
        if (!entity.isOwnedBy(userId)) {
            throw new UnauthorizedException("본인의 북마크만 조회할 수 있습니다.");
        }
        
        if (Boolean.TRUE.equals(entity.getIsDeleted())) {
            throw new BookmarkNotFoundException("삭제된 북마크입니다: " + id);
        }
        
        return entity;
    }
    
    @Override
    public Page<BookmarkEntity> searchBookmarks(Long userId, BookmarkSearchRequest request) {
        Pageable pageable = PageRequest.of(
            request.page() - 1,
            request.size(),
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        
        Specification<BookmarkEntity> spec = Specification.<BookmarkEntity>unrestricted()
            .and((root, query, cb) -> cb.equal(root.get("userId"), userId))
            .and((root, query, cb) -> cb.equal(root.get("isDeleted"), false));
        
        String searchTerm = request.q();
        String searchField = request.searchField();
        
        if ("title".equals(searchField)) {
            spec = spec.and((root, query, cb) ->
                cb.like(cb.lower(root.get("title")), "%" + searchTerm.toLowerCase() + "%"));
        } else if ("tag".equals(searchField)) {
            spec = spec.and((root, query, cb) ->
                cb.like(cb.lower(root.get("tag")), "%" + searchTerm.toLowerCase() + "%"));
        } else if ("memo".equals(searchField)) {
            spec = spec.and((root, query, cb) ->
                cb.like(cb.lower(root.get("memo")), "%" + searchTerm.toLowerCase() + "%"));
        } else {
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), "%" + searchTerm.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("tag")), "%" + searchTerm.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("memo")), "%" + searchTerm.toLowerCase() + "%")
            ));
        }
        
        return bookmarkReaderRepository.findAll(spec, pageable);
    }
    
    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        
        String[] parts = sort.split(",");
        if (parts.length != 2) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        
        String field = parts[0].trim();
        String direction = parts[1].trim().toLowerCase();
        
        Sort.Direction sortDirection = "asc".equals(direction) 
            ? Sort.Direction.ASC 
            : Sort.Direction.DESC;
        
        return Sort.by(sortDirection, field);
    }
}
