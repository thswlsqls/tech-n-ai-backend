package com.tech.n.ai.api.bookmark.service;

import com.tech.n.ai.api.bookmark.dto.request.BookmarkDailyReportRequest;
import com.tech.n.ai.api.bookmark.dto.response.BookmarkDailyReportResponse;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkDailyStatEntity;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkDailyStatReaderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * BookmarkReportService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookmarkReportService 단위 테스트")
class BookmarkReportServiceTest {

    @Mock
    private BookmarkDailyStatReaderRepository bookmarkDailyStatReaderRepository;

    @InjectMocks
    private BookmarkReportServiceImpl bookmarkReportService;

    private static final Long TEST_USER_ID = 1L;

    @Nested
    @DisplayName("getDailyReport")
    class GetDailyReport {

        @Test
        @DisplayName("3일 구간 - 날짜별 집계를 모두 담는다")
        void getDailyReport_3일구간() {
            when(bookmarkDailyStatReaderRepository.findByUserIdAndStatDate(anyLong(), any(LocalDate.class)))
                .thenReturn(List.of(createStat(LocalDate.of(2026, 8, 1), "github", 3L)));

            BookmarkDailyReportResponse response = bookmarkReportService.getDailyReport(
                TEST_USER_ID, new BookmarkDailyReportRequest("2026-08-01", "2026-08-03", null));

            assertThat(response.days()).hasSize(3);
            assertThat(response.totalViews()).isEqualTo(9L);
        }

        @Test
        @DisplayName("provider 필터 - 맞지 않는 집계는 뺀다")
        void getDailyReport_provider필터() {
            when(bookmarkDailyStatReaderRepository.findByUserIdAndStatDate(anyLong(), any(LocalDate.class)))
                .thenReturn(List.of(
                    createStat(LocalDate.of(2026, 8, 1), "github", 3L),
                    createStat(LocalDate.of(2026, 8, 1), "rss", 7L)
                ));

            BookmarkDailyReportResponse response = bookmarkReportService.getDailyReport(
                TEST_USER_ID, new BookmarkDailyReportRequest("2026-08-01", "2026-08-01", "github"));

            assertThat(response.days()).hasSize(1);
            assertThat(response.days().get(0).provider()).isEqualTo("github");
            assertThat(response.totalViews()).isEqualTo(3L);
        }

        @Test
        @DisplayName("집계가 없으면 빈 목록")
        void getDailyReport_집계없음() {
            when(bookmarkDailyStatReaderRepository.findByUserIdAndStatDate(anyLong(), any(LocalDate.class)))
                .thenReturn(List.of());

            BookmarkDailyReportResponse response = bookmarkReportService.getDailyReport(
                TEST_USER_ID, new BookmarkDailyReportRequest("2026-08-01", "2026-08-05", null));

            assertThat(response.days()).isEmpty();
            assertThat(response.totalViews()).isZero();
        }
    }

    private BookmarkDailyStatEntity createStat(LocalDate date, String provider, Long viewCount) {
        BookmarkDailyStatEntity stat = BookmarkDailyStatEntity.of(TEST_USER_ID, date, provider);
        stat.setViewCount(viewCount);
        return stat;
    }
}
