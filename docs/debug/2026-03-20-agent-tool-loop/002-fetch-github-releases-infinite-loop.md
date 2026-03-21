# 002 - fetch_github_releases 동일 인자 30회 반복 호출로 Agent 실행 실패

## 기본 정보
- **발생일**: 2026-03-20
- **모듈**: `api/agent`
- **심각도**: Critical (Agent 응답 완전 실패)
- **상태**: 해결 완료
- **선행 이슈**: [001 - collect_rss_feeds / collect_scraped_articles 무한 루프](001-collect-rss-feeds-infinite-loop.md)

## 증상
- Admin 프론트엔드에서 "OpenAI Python SDK 최신 릴리스 조회해주세요." 메시지 전송
- Agent가 `fetch_github_releases(owner=openai, repo=openai-python)`를 **동일 인자로 30회 연속 호출**
- 약 50초(20:40:17 ~ 20:41:09) 동안 반복 후 LangChain4j 30회 tool invocation 제한 도달
- 최종 에러: `java.lang.RuntimeException: Something is wrong, exceeded 30 sequential tool invocations`

### 서버 로그 (발췌)
```
20:40:17 Tool 호출: fetch_github_releases(owner=openai, repo=openai-python)
20:40:20 Tool 호출: fetch_github_releases(owner=openai, repo=openai-python)
20:40:22 Tool 호출: fetch_github_releases(owner=openai, repo=openai-python)
... (동일 호출 27회 반복) ...
20:41:07 Tool 호출: fetch_github_releases(owner=openai, repo=openai-python)
20:41:09 ERROR Agent 실행 실패: exceeded 30 sequential tool invocations
```

## 근본 원인

### 3가지 복합 원인

001 이슈에서 `collect_*` 도구에 중복 호출 방어를 추가했으나, **`fetch_*`/`scrape_*` 조회 전용 도구에는 동일 방어가 적용되지 않았음**.

#### 원인 1: `fetch_github_releases` 자체 반복 호출 추적 부재

`ToolExecutionMetrics`에는 `collectedGitHubRepos`(collect 후 fetch 차단)만 있고, **동일 저장소를 fetch로 여러 번 조회하는 것을 추적하는 필드가 없었음**:

```java
// Before - collect→fetch 차단만 존재, fetch→fetch 차단 없음
if (metrics().isGitHubRepoCollected(correctedOwner, correctedRepo)) {
    // collect 후 fetch만 차단
    return "BLOCKED: ...";
}
// collect를 하지 않았으면 동일 저장소를 몇 번이든 fetch 가능
List<GitHubReleaseDto> releases = githubAdapter.getReleases(...);
```

#### 원인 2: `ToolErrorHandlers`의 재시도 유도 에러 메시지

에러 발생 시 LLM에게 전달되는 메시지가 **재시도를 적극 유도**하는 문구였음:

```java
// Before - "다른 방법을 시도해주세요" → LLM이 같은 도구를 다시 시도
return ToolErrorHandlerResult.text(
    String.format("Tool '%s' 실행 실패: %s. 다른 방법을 시도해주세요.", ...));
```

LLM은 이 메시지를 "다른 인자로 재시도하라"가 아닌 "같은 도구를 다시 호출하라"로 해석하여 동일 호출 반복.

#### 원인 3: `GitHubToolAdapter`의 일반 예외 삼킴

403/429 외의 일반 예외(404, 네트워크 타임아웃 등)를 삼키고 빈 리스트를 반환하여, LLM이 "데이터 없음"과 "에러"를 구분할 수 없었음:

```java
// Before - 모든 일반 예외를 빈 리스트로 변환
} catch (Exception e) {
    log.error("GitHub releases 조회 실패: ...", e);
    return List.of();  // LLM에게는 "릴리스 없음"으로 전달
}
```

→ LLM은 `"openai/openai-python 저장소에 릴리스가 없습니다."` 메시지를 받고, "데이터를 가져오지 못했으니 다시 시도해야 한다"고 판단하여 재호출.

### `scrape_web_page`에도 동일 문제 존재

`scrape_web_page`에는 어떠한 중복 호출 방어도 없었음. 동일 URL을 반복 크롤링하는 루프가 발생할 수 있는 잠재적 위험 상태.

### 루프 발생 시퀀스

```
1. LLM: fetch_github_releases("openai", "openai-python") 호출
2. GitHubToolAdapter: API 호출 → 에러 발생 → List.of() 반환
3. EmergingTechAgentTools: "저장소에 릴리스가 없습니다." 반환
4. LLM: "릴리스가 없다니 이상하다. 다시 시도해보자" → 동일 호출
5. 2~4 반복 × 30회 → "exceeded 30 sequential tool invocations"
```

### 기존 방어가 동작하지 않은 이유

| 방어 메커니즘 | collect_* (001에서 추가) | fetch_github_releases | scrape_web_page |
|---|---|---|---|
| 자체 반복 호출 추적 | O (`collectedRssProviders` 등) | **X** | **X** |
| BLOCKED 응답 반환 | O | **X** (collect→fetch만) | **X** |
| `AgentLoopDetectedException` (4회 초과) | O | **X** | **X** |
| `@Tool` description에 재호출 금지 힌트 | O | **△** (collect→fetch만) | **X** |
| 에러 시 재시도 억제 메시지 | - | **X** ("다른 방법을 시도해주세요") | **X** |
| 에러 명시적 전파 | - | **X** (빈 리스트로 삼킴) | - |

## 수정 내용

### 1. `ToolExecutionMetrics.java` — fetch/scrape 조회 완료 추적 추가

```java
// 새로 추가된 필드
private final Set<String> fetchedGitHubRepos;       // fetch 조회 완료 저장소
private final Set<String> scrapedUrls;               // scrape 크롤링 완료 URL
private final AtomicInteger fetchLoopBlockedCount;   // fetch/scrape 자체 반복 차단 횟수

// 새로 추가된 메서드
void markGitHubRepoFetched(String owner, String repo)   // fetch 완료 마킹
boolean isGitHubRepoFetched(String owner, String repo)   // fetch 여부 확인
void markUrlScraped(String url)                          // scrape 완료 마킹
boolean isUrlScraped(String url)                         // scrape 여부 확인
int incrementAndGetFetchLoopBlockedCount()               // 차단 횟수 증가
```

`fetchLoopBlockedCount`는 `fetch_github_releases`와 `scrape_web_page`가 공유. `collectBlockedCount`가 `collect_rss_feeds`/`collect_scraped_articles` 간에 공유되는 것과 동일한 패턴.

### 2. `EmergingTechAgentTools.java` — 자체 반복 호출 차단 로직 추가

#### `fetchGitHubReleases()` 변경

```java
// After (수정 코드)
@Tool(name = "fetch_github_releases",
      value = "... 이미 수집 또는 조회된 저장소를 다시 조회하면 차단됩니다. ...")
public String fetchGitHubReleases(String owner, String repo) {
    // 기존: collect→fetch 차단 (유지)
    if (metrics().isGitHubRepoCollected(...)) { ... }

    // 신규: fetch→fetch 차단
    if (metrics().isGitHubRepoFetched(correctedOwner, correctedRepo)) {
        int blockedCount = metrics().incrementAndGetFetchLoopBlockedCount();
        if (blockedCount > 3) {
            throw new AgentLoopDetectedException(...);
        }
        return "BLOCKED: .../... 저장소는 이미 fetch_github_releases로 조회 완료되었습니다. ...";
    }

    List<GitHubReleaseDto> releases = githubAdapter.getReleases(...);

    // 조회 완료 마킹 (빈 결과도 포함 — 재시도해도 동일 결과)
    metrics().markGitHubRepoFetched(correctedOwner, correctedRepo);

    return releases.isEmpty() ? "저장소에 릴리스가 없습니다." : releases.toString();
}
```

#### `scrapeWebPage()` 변경

```java
// After (수정 코드)
@Tool(name = "scrape_web_page",
      value = "... 이미 크롤링한 URL을 다시 호출하면 차단됩니다. ...")
public ScrapedContentDto scrapeWebPage(String url) {
    // 신규: URL 반복 크롤링 차단
    if (metrics().isUrlScraped(url)) {
        int blockedCount = metrics().incrementAndGetFetchLoopBlockedCount();
        if (blockedCount > 3) {
            throw new AgentLoopDetectedException(...);
        }
        return new ScrapedContentDto(null, "BLOCKED: ...", url);
    }

    ScrapedContentDto result = scraperAdapter.scrape(url);
    metrics().markUrlScraped(url);
    return result;
}
```

### 3. `ToolErrorHandlers.java` — 재시도 억제 메시지로 변경

```java
// Before
"Tool '%s' 실행 실패: %s. 다른 방법을 시도해주세요."

// After
"Tool '%s' 실행 실패: %s. 이 Tool을 동일한 인자로 재시도하지 마세요. 해당 작업을 건너뛰고 다음 작업으로 진행하세요."
```

### 4. `GitHubToolAdapter.java` — 일반 예외를 RuntimeException으로 전파

```java
// Before — 빈 리스트로 삼킴 (에러 신호 소실)
} catch (Exception e) {
    log.error("GitHub releases 조회 실패: ...", e);
    return List.of();
}

// After — RuntimeException으로 전파 (ToolErrorHandlers가 LLM에게 에러 전달)
} catch (Exception e) {
    log.error("GitHub releases 조회 실패: ...", e);
    throw new RuntimeException(
        "GitHub releases 조회 실패 (%s/%s): %s. 이 저장소에 대해 더 이상 재시도하지 마세요."
            .formatted(owner, repo, e.getMessage()), e);
}
```

## 수정 후 예상 동작

```
20:40:17 fetch_github_releases(openai, openai-python) → 결과 반환 + markGitHubRepoFetched()
20:40:19 fetch_github_releases(openai, openai-python) → BLOCKED 응답 (차단 1회)
20:40:20 fetch_github_releases(openai, openai-python) → BLOCKED 응답 (차단 2회)
20:40:21 fetch_github_releases(openai, openai-python) → BLOCKED 응답 (차단 3회)
20:40:22 fetch_github_releases(openai, openai-python) → AgentLoopDetectedException (차단 4회 > 3)
→ graceful 종료: "수집 작업이 완료되었습니다. (반복 조회 루프 감지로 자동 종료)"
```

**소요 시간**: 기존 ~50초 (30회 API 호출) → 수정 후 ~5초 (1회 실제 호출 + 최대 4회 BLOCKED)

## 영향 범위
- `fetch_github_releases`: 동일 저장소 반복 조회 차단 (신규)
- `scrape_web_page`: 동일 URL 반복 크롤링 차단 (신규)
- `ToolErrorHandlers`: 모든 Tool 에러 시 재시도 억제 메시지 적용
- `GitHubToolAdapter`: 일반 예외 전파로 에러 가시성 향상

## 관련 파일

| 파일 | 변경 유형 |
|------|----------|
| `api/agent/.../metrics/ToolExecutionMetrics.java` | `fetchedGitHubRepos`, `scrapedUrls`, `fetchLoopBlockedCount` 추가 |
| `api/agent/.../tool/EmergingTechAgentTools.java` | `fetchGitHubReleases`, `scrapeWebPage`에 자체 반복 차단 로직 추가 |
| `api/agent/.../tool/handler/ToolErrorHandlers.java` | 에러 메시지 재시도 억제로 변경 |
| `api/agent/.../tool/adapter/GitHubToolAdapter.java` | 일반 예외 `RuntimeException` 전파 |

### 테스트 파일

| 파일 | 변경 유형 |
|------|----------|
| `api/agent/.../metrics/ToolExecutionMetricsTest.java` | `FetchedGitHubRepos`, `ScrapedUrls`, `FetchLoopBlockedCount` 테스트 추가 (8개) |
| `api/agent/.../tool/EmergingTechAgentToolsTest.java` | fetch 루프 차단 3개 + scrape 루프 차단 2개 테스트 추가 |
| `api/agent/.../tool/adapter/GitHubToolAdapterTest.java` | `getReleases_API실패` 테스트를 `RuntimeException` 전파 검증으로 변경 |

## 전체 루프 방어 현황 (001 + 002 수정 후)

| 도구 | 자체 반복 차단 | collect→fetch 차단 | 에러 시 재시도 억제 |
|------|---|---|---|
| `fetch_github_releases` | O (002 추가) | O (기존) | O (002 변경) |
| `scrape_web_page` | O (002 추가) | - | O (002 변경) |
| `collect_github_releases` | - (Adapter에서 중복 처리) | - | O (002 변경) |
| `collect_rss_feeds` | O (001 추가) | - | O (002 변경) |
| `collect_scraped_articles` | O (001 추가) | - | O (002 변경) |

## 재발 방지
- 향후 새로운 조회 전용 도구(`fetch_*`, `scrape_*`) 추가 시 `ToolExecutionMetrics`에 추적 필드를 반드시 추가하고 동일 인자 반복 차단 패턴 적용
- `@Tool` description에 "이미 조회/크롤링한 대상을 다시 호출하지 말 것" 힌트 필수 포함
- 외부 API Adapter에서 일반 예외를 삼키지 않고 명시적으로 전파하여 LLM이 에러와 빈 결과를 구분할 수 있도록 함
- `ToolErrorHandlers` 에러 메시지에 "재시도하지 마세요" 명시하여 LLM 레벨 재시도 루프 방지
