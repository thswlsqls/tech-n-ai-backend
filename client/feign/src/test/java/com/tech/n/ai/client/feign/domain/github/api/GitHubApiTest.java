package com.tech.n.ai.client.feign.domain.github.api;

import com.tech.n.ai.client.feign.domain.github.client.GitHubFeignClient;
import com.tech.n.ai.client.feign.domain.github.contract.GitHubDto.EventsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * GitHubApi 단위 테스트
 */
@DisplayName("GitHubApi 단위 테스트")
class GitHubApiTest {

    private GitHubFeignClient githubFeign;
    private GitHubApi gitHubApi;

    @BeforeEach
    void setUp() {
        githubFeign = mock(GitHubFeignClient.class);
        gitHubApi = new GitHubApi(githubFeign);
    }

    @Test
    @DisplayName("토큰이 공백뿐이면 Authorization 헤더 값으로 null을 넘긴다")
    void blankTokenSendsNoHeader() {
        ReflectionTestUtils.setField(gitHubApi, "token", "   ");

        gitHubApi.getEvents(new EventsRequest(10, 1));

        verify(githubFeign).getEvents(isNull(), any(), any());
    }

    @Test
    @DisplayName("토큰이 있으면 \"Bearer <token>\"을 헤더 값으로 넘긴다")
    void tokenSendsBearerHeader() {
        ReflectionTestUtils.setField(gitHubApi, "token", "abc");

        gitHubApi.getEvents(new EventsRequest(10, 1));

        verify(githubFeign).getEvents(eq("Bearer abc"), any(), any());
    }
}
