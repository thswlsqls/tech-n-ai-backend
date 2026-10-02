package com.tech.n.ai.api.bookmark.facade;

import com.tech.n.ai.api.bookmark.common.exception.BookmarkValidationException;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkDailyReportRequest;
import com.tech.n.ai.api.bookmark.service.BookmarkReportService;
import com.tech.n.ai.api.bookmark.service.BookmarkViewEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * BookmarkReportFacade 단위 테스트
 *
 * 리포트 구간 검증은 Facade 에만 있다. 컨트롤러 테스트는 Facade 를 목으로 바꾸므로
 * 검증 코드를 실제로 실행하는 곳은 여기뿐이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookmarkReportFacade 단위 테스트")
class BookmarkReportFacadeTest {

    @Mock
    private BookmarkViewEventService bookmarkViewEventService;

    @Mock
    private BookmarkReportService bookmarkReportService;

    @InjectMocks
    private BookmarkReportFacade bookmarkReportFacade;

    private static final Long TEST_USER_ID = 1L;

    @Nested
    @DisplayName("getDailyReport")
    class GetDailyReport {

        @Test
        @DisplayName("90일 구간 - 파싱한 날짜를 서비스에 넘긴다")
        void getDailyReport_90일() {
            bookmarkReportFacade.getDailyReport(
                TEST_USER_ID, new BookmarkDailyReportRequest("2026-05-23", "2026-08-20", "github"));

            verify(bookmarkReportService).getDailyReport(
                TEST_USER_ID, LocalDate.of(2026, 5, 23), LocalDate.of(2026, 8, 20), "github");
        }

        @Test
        @DisplayName("91일 구간 - 400")
        void getDailyReport_91일() {
            assertRejected(new BookmarkDailyReportRequest("2026-05-22", "2026-08-20", null));
        }

        @Test
        @DisplayName("from 이 to 보다 늦음 - 400")
        void getDailyReport_역순구간() {
            assertRejected(new BookmarkDailyReportRequest("2026-08-03", "2026-08-01", null));
        }

        @Test
        @DisplayName("날짜 형식이 틀림 - 400")
        void getDailyReport_날짜형식() {
            assertRejected(new BookmarkDailyReportRequest("2026/08/01", "2026-08-03", null));
        }
    }

    private void assertRejected(BookmarkDailyReportRequest request) {
        assertThatThrownBy(() -> bookmarkReportFacade.getDailyReport(TEST_USER_ID, request))
            .isInstanceOf(BookmarkValidationException.class);
        verifyNoInteractions(bookmarkReportService);
    }
}
