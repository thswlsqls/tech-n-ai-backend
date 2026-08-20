package com.tech.n.ai.api.bookmark.controller;

import com.tech.n.ai.api.bookmark.service.BookmarkTagService;
import com.tech.n.ai.common.core.dto.ApiResponse;
import com.tech.n.ai.common.security.principal.UserPrincipal;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 북마크 태그 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/bookmark/tag")
@RequiredArgsConstructor
public class BookmarkTagController {

    private final BookmarkTagService bookmarkTagService;

    /**
     * 북마크에 태그를 덧붙인다.
     */
    @PostMapping("/{bookmarkId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> appendTags(
            @PathVariable Long bookmarkId,
            @RequestBody List<String> tags,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        BookmarkEntity bookmark = bookmarkTagService.appendTags(bookmarkId, tags);

        Map<String, Object> body = new HashMap<>();
        body.put("bookmarkId", bookmark.getId());
        body.put("tags", bookmark.getTags());
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /**
     * 사용자의 전체 북마크 태그를 다시 계산한다. 내부 배치에서 호출한다.
     */
    @PostMapping("/internal/refresh/{userId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> refreshAll(@PathVariable Long userId) {
        List<BookmarkEntity> refreshed = bookmarkTagService.refreshAllTags(userId);

        List<Map<String, Object>> body = new ArrayList<>();
        for (BookmarkEntity bookmark : refreshed) {
            Map<String, Object> row = new HashMap<>();
            row.put("bookmarkId", bookmark.getId());
            row.put("tags", bookmark.getTags());
            body.add(row);
        }
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
