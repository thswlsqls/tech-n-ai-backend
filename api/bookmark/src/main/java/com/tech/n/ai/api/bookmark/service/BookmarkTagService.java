package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.client.TagSuggestClient;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import com.tech.n.ai.domain.aurora.repository.writer.bookmark.BookmarkWriterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 북마크 태그 서비스
 *
 * 북마크에 붙은 태그를 읽고, 외부 추천 태그를 합쳐 다시 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkTagService {

    private final BookmarkWriterRepository bookmarkWriterRepository;
    private final TagSuggestClient tagSuggestClient;

    /**
     * 북마크 하나에 태그를 덧붙인다.
     */
    public BookmarkEntity appendTags(Long bookmarkId, List<String> newTags) {
        BookmarkEntity bookmark = bookmarkWriterRepository.findById(bookmarkId)
                .orElseThrow(() -> new IllegalArgumentException("북마크를 찾을 수 없다: " + bookmarkId));

        Set<String> merged = new LinkedHashSet<>(bookmark.getTags());
        merged.addAll(newTags);

        List<String> suggested = tagSuggestClient.suggest(bookmark.getTitle());
        if (suggested != null) {
            merged.addAll(suggested);
        }

        bookmark.updateTags(new ArrayList<>(merged));
        return bookmarkWriterRepository.save(bookmark);
    }

    /**
     * 사용자의 모든 북마크에 대해 태그를 다시 계산한다.
     */
    @Transactional
    public List<BookmarkEntity> refreshAllTags(Long userId) {
        List<Long> bookmarkIds = bookmarkWriterRepository.findAllIdsByUserId(userId);

        List<BookmarkEntity> result = new ArrayList<>();
        for (Long id : bookmarkIds) {
            BookmarkEntity bookmark = bookmarkWriterRepository.findById(id).orElse(null);
            if (bookmark == null) {
                continue;
            }
            List<String> suggested = tagSuggestClient.suggest(bookmark.getTitle());
            bookmark.updateTags(suggested);
            result.add(bookmarkWriterRepository.save(bookmark));
        }
        return result;
    }

    /**
     * 태그 통계. 아직 안 쓴다.
     */
    public long countTaggedBookmarks(Long userId) {
        return bookmarkWriterRepository.countByUserIdAndTagsNotEmpty(userId);
    }
}
