package com.tech.n.ai.client.feign.domain.github.api;

import com.tech.n.ai.client.feign.domain.github.client.GitHubFeignClient;
import com.tech.n.ai.client.feign.domain.github.contract.GitHubContract;
import com.tech.n.ai.client.feign.domain.github.contract.GitHubDto.EventsRequest;
import com.tech.n.ai.client.feign.domain.github.contract.GitHubDto.EventsResponse;
import com.tech.n.ai.client.feign.domain.github.contract.GitHubDto.Event;
import com.tech.n.ai.client.feign.domain.github.contract.GitHubDto.ReleasesRequest;
import com.tech.n.ai.client.feign.domain.github.contract.GitHubDto.ReleasesResponse;
import com.tech.n.ai.client.feign.domain.github.contract.GitHubDto.Release;
import com.tech.n.ai.common.core.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class GitHubApi implements GitHubContract {

    private final GitHubFeignClient githubFeign;

    @Value("${feign-clients.github.token:}")
    private String token;

    @Override
    public EventsResponse getEvents(EventsRequest request) {
        List<Event> events = githubFeign.getEvents(
                bearerAuthorization(),
                request.perPage(),
                request.page()
        );
        return EventsResponse.builder()
                .events(events != null ? events : List.of())
                .build();
    }

    @Override
    public ReleasesResponse getReleases(ReleasesRequest request) {
        List<Release> releases = githubFeign.getReleases(
                bearerAuthorization(),
                request.owner(),
                request.repo(),
                request.perPage(),
                request.page()
        );
        return ReleasesResponse.builder()
                .releases(releases != null ? releases : List.of())
                .build();
    }

    private String bearerAuthorization() {
        return StringUtils.isNotEmpty(token) ? "Bearer " + token : null;
    }

}
