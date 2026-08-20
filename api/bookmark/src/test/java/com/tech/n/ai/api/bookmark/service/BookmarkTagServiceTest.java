package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.client.TagSuggestClient;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity;
import com.tech.n.ai.domain.aurora.repository.writer.bookmark.BookmarkWriterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BookmarkTagServiceTest {

    private final BookmarkWriterRepository repository = Mockito.mock(BookmarkWriterRepository.class);
    private final TagSuggestClient tagSuggestClient = Mockito.mock(TagSuggestClient.class);
    private final BookmarkTagService service = new BookmarkTagService(repository, tagSuggestClient);

    @Test
    @DisplayName("태그를 덧붙이면 결과가 반환된다")
    void appendTags() {
        BookmarkEntity bookmark = Mockito.mock(BookmarkEntity.class);
        Mockito.when(repository.findById(1L)).thenReturn(Optional.of(bookmark));
        Mockito.when(repository.save(Mockito.any())).thenReturn(bookmark);

        BookmarkEntity result = service.appendTags(1L, List.of("spring"));

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("전체 태그 갱신이 동작한다")
    void refreshAllTags() {
        Mockito.when(repository.findAllIdsByUserId(1L)).thenReturn(List.of());

        service.refreshAllTags(1L);
    }
}
