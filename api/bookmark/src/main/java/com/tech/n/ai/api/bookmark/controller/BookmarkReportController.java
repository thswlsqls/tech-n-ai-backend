package com.tech.n.ai.api.bookmark.controller;

import com.tech.n.ai.api.bookmark.dto.request.BookmarkDailyReportRequest;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkViewEventRequest;
import com.tech.n.ai.api.bookmark.dto.response.BookmarkDailyReportResponse;
import com.tech.n.ai.api.bookmark.dto.response.BookmarkViewEventResponse;
import com.tech.n.ai.api.bookmark.facade.BookmarkReportFacade;
import com.tech.n.ai.common.core.dto.ApiResponse;
import com.tech.n.ai.common.security.principal.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 북마크 조회 이벤트·리포트 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/bookmark")
@RequiredArgsConstructor
public class BookmarkReportController {

    private final BookmarkReportFacade bookmarkReportFacade;

    /**
     * 조회 이벤트 기록
     */
    @PostMapping("/{id}/views")
    public ResponseEntity<ApiResponse<BookmarkViewEventResponse>> recordView(
            @PathVariable String id,
            @Valid @RequestBody BookmarkViewEventRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        BookmarkViewEventResponse response =
            bookmarkReportFacade.recordView(userPrincipal.userId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 일별 조회 리포트
     */
    @GetMapping("/reports/daily")
    public ResponseEntity<ApiResponse<BookmarkDailyReportResponse>> getDailyReport(
            @Valid BookmarkDailyReportRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        BookmarkDailyReportResponse response =
            bookmarkReportFacade.getDailyReport(userPrincipal.userId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
