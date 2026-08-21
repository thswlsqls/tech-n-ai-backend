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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    private static final String PROVIDER = "github";

    @Nested
    @DisplayName("getDailyReport")
    class GetDailyReport {

        @Test
        @DisplayName("날짜별 값을 그대로 담는다")
        void getDailyReport_날짜별값() {
            LocalDate from = LocalDate.of(2026, 8, 1);
            LocalDate to = LocalDate.of(2026, 8, 3);
            when(bookmarkDailyStatReaderRepository.findRange(TEST_USER_ID, from, to, PROVIDER))
                .thenReturn(List.of(
                    createStat(LocalDate.of(2026, 8, 1), PROVIDER, 3L),
                    createStat(LocalDate.of(2026, 8, 2), PROVIDER, 5L),
                    createStat(LocalDate.of(2026, 8, 3), PROVIDER, 1L)
                ));

            BookmarkDailyReportResponse response = bookmarkReportService.getDailyReport(
                TEST_USER_ID, new BookmarkDailyReportRequest("2026-08-01", "2026-08-03", PROVIDER));

            assertThat(response.days()).hasSize(3);
            assertThat(response.days().get(0).date()).isEqualTo("2026-08-01");
            assertThat(response.days().get(0).viewCount()).isEqualTo(3L);
            assertThat(response.days().get(1).date()).isEqualTo("2026-08-02");
            assertThat(response.days().get(1).viewCount()).isEqualTo(5L);
            assertThat(response.days().get(2).date()).isEqualTo("2026-08-03");
            assertThat(response.days().get(2).viewCount()).isEqualTo(1L);
            assertThat(response.totalViews()).isEqualTo(9L);
        }

        @Test
        @DisplayName("구간이 길어도 집계 조회는 1회다")
        void getDailyReport_구간조회_1회() {
            LocalDate from = LocalDate.of(2026, 5, 23);
            LocalDate to = LocalDate.of(2026, 8, 20);   // 90일 구간
            when(bookmarkDailyStatReaderRepository.findRange(TEST_USER_ID, from, to, PROVIDER))
                .thenReturn(List.of(createStat(from, PROVIDER, 2L)));

            bookmarkReportService.getDailyReport(
                TEST_USER_ID, new BookmarkDailyReportRequest("2026-05-23", "2026-08-20", PROVIDER));

            verify(bookmarkDailyStatReaderRepository, times(1))
                .findRange(eq(TEST_USER_ID), eq(from), eq(to), eq(PROVIDER));
            verifyNoMoreInteractions(bookmarkDailyStatReaderRepository);
        }

        @Test
        @DisplayName("집계가 없으면 빈 목록")
        void getDailyReport_집계없음() {
            LocalDate from = LocalDate.of(2026, 8, 1);
            LocalDate to = LocalDate.of(2026, 8, 5);
            when(bookmarkDailyStatReaderRepository.findRange(TEST_USER_ID, from, to, PROVIDER))
                .thenReturn(List.of());

            BookmarkDailyReportResponse response = bookmarkReportService.getDailyReport(
                TEST_USER_ID, new BookmarkDailyReportRequest("2026-08-01", "2026-08-05", PROVIDER));

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
