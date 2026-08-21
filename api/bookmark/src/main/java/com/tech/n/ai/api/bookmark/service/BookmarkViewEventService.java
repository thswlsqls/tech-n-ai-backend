package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.dto.request.BookmarkViewEventRequest;
import com.tech.n.ai.api.bookmark.dto.response.BookmarkViewEventResponse;

/**
 * 북마크 조회 이벤트 기록 서비스
 */
public interface BookmarkViewEventService {

    /**
     * 조회 이벤트를 한 건 기록하고 그 날짜의 집계를 갱신한다.
     */
    BookmarkViewEventResponse recordView(Long userId, Long bookmarkId, BookmarkViewEventRequest request);
}
