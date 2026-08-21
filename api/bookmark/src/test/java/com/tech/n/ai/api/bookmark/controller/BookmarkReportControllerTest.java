package com.tech.n.ai.api.bookmark.controller;

import com.tech.n.ai.api.bookmark.common.exception.BookmarkExceptionHandler;
import com.tech.n.ai.api.bookmark.common.exception.BookmarkValidationException;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkDailyReportRequest;
import com.tech.n.ai.api.bookmark.dto.request.BookmarkViewEventRequest;
import com.tech.n.ai.api.bookmark.dto.response.BookmarkDailyReportResponse;
import com.tech.n.ai.api.bookmark.dto.response.BookmarkViewEventResponse;
import com.tech.n.ai.api.bookmark.facade.BookmarkReportFacade;
import com.tech.n.ai.common.security.principal.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BookmarkReportController 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookmarkReportController 단위 테스트")
class BookmarkReportControllerTest {

    @Mock
    private BookmarkReportFacade bookmarkReportFacade;

    @InjectMocks
    private BookmarkReportController bookmarkReportController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final Long TEST_USER_ID = 1L;
    private static final String BASE_URL = "/api/v1/bookmark";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
            .standaloneSetup(bookmarkReportController)
            .setCustomArgumentResolvers(new TestUserPrincipalArgumentResolver())
            .setControllerAdvice(new BookmarkExceptionHandler())
            .build();
    }

    @Nested
    @DisplayName("POST /api/v1/bookmark/{id}/views")
    class RecordView {

        @Test
        @DisplayName("정상 기록 - 200 OK")
        void recordView_성공() throws Exception {
            BookmarkViewEventResponse response =
                BookmarkViewEventResponse.of(100L, LocalDateTime.now(), 1L);
            when(bookmarkReportFacade.recordView(anyLong(), anyString(), any(BookmarkViewEventRequest.class)))
                .thenReturn(response);

            mockMvc.perform(post(BASE_URL + "/100/views")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new BookmarkViewEventRequest("web"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.todayViewCount").value("1"));

            verify(bookmarkReportFacade).recordView(
                eq(TEST_USER_ID), eq("100"), eq(new BookmarkViewEventRequest("web")));
        }

        @Test
        @DisplayName("삭제된 북마크 - 404")
        void recordView_없는북마크() throws Exception {
            when(bookmarkReportFacade.recordView(anyLong(), anyString(), any(BookmarkViewEventRequest.class)))
                .thenThrow(new com.tech.n.ai.api.bookmark.common.exception.BookmarkNotFoundException(
                    "삭제된 북마크입니다: 100"));

            mockMvc.perform(post(BASE_URL + "/100/views")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new BookmarkViewEventRequest("web"))))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/bookmark/reports/daily")
    class GetDailyReport {

        @Test
        @DisplayName("정상 조회 - 200 OK")
        void getDailyReport_성공() throws Exception {
            BookmarkDailyReportResponse response = new BookmarkDailyReportResponse(
                "2026-08-01", "2026-08-03", 9L,
                List.of(new BookmarkDailyReportResponse.DailyView("2026-08-01", "github", 9L)));
            when(bookmarkReportFacade.getDailyReport(anyLong(), any()))
                .thenReturn(response);

            mockMvc.perform(get(BASE_URL + "/reports/daily")
                    .param("from", "2026-08-01")
                    .param("to", "2026-08-03")
                    .param("provider", "github"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2000"))
                .andExpect(jsonPath("$.data.totalViews").value("9"))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-08-01"))
                .andExpect(jsonPath("$.data.days[0].provider").value("github"))
                .andExpect(jsonPath("$.data.days[0].viewCount").value("9"));

            verify(bookmarkReportFacade).getDailyReport(
                eq(TEST_USER_ID),
                eq(new BookmarkDailyReportRequest("2026-08-01", "2026-08-03", "github")));
        }

        @Test
        @DisplayName("provider 를 빈 값으로 보내면 미지정으로 넘어간다")
        void getDailyReport_provider빈값() throws Exception {
            when(bookmarkReportFacade.getDailyReport(anyLong(), any()))
                .thenReturn(new BookmarkDailyReportResponse("2026-08-01", "2026-08-03", 0L, List.of()));

            mockMvc.perform(get(BASE_URL + "/reports/daily")
                    .param("from", "2026-08-01")
                    .param("to", "2026-08-03")
                    .param("provider", ""))
                .andExpect(status().isOk());

            verify(bookmarkReportFacade).getDailyReport(
                eq(TEST_USER_ID),
                eq(new BookmarkDailyReportRequest("2026-08-01", "2026-08-03", null)));
        }

        @Test
        @DisplayName("from 이 to 보다 늦음 - 400")
        void getDailyReport_역순구간() throws Exception {
            when(bookmarkReportFacade.getDailyReport(anyLong(), any()))
                .thenThrow(new BookmarkValidationException("from은 to보다 늦을 수 없습니다."));

            mockMvc.perform(get(BASE_URL + "/reports/daily")
                    .param("from", "2026-08-03")
                    .param("to", "2026-08-01"))
                .andExpect(status().isBadRequest());
        }
    }

    /**
     * 테스트용 UserPrincipal ArgumentResolver
     */
    static class TestUserPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(UserPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return new UserPrincipal(TEST_USER_ID, "test@example.com", "USER");
        }
    }
}
