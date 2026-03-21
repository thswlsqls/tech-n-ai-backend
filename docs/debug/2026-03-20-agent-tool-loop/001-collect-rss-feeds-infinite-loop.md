# 001 - collect_rss_feeds / collect_scraped_articles 무한 루프로 Agent 실행 실패

## 기본 정보
- **발생일**: 2026-03-20
- **모듈**: `api/agent`
- **심각도**: Critical (Agent 응답 완전 실패 + 프론트엔드 연결 끊김)
- **상태**: 해결 완료

## 증상
- 프론트엔드에서 "RSS 피드 전체 수집해주세요" 요청 시 Agent 호출 실패 팝업 표시
- 서버 로그에서 `collect_rss_feeds(provider=OPENAI)` / `collect_rss_feeds(provider=GOOGLE)` 반복 호출 확인
- 약 2분 30초 동안 동일 provider에 대해 수십 회 중복 수집 후 LangChain4j의 30회 tool invocation 제한 도달
- 최종 에러: `java.lang.RuntimeException: Something is wrong, exceeded 30 sequential tool invocations`
- 후속 에러: 프론트엔드 연결이 먼저 끊겨 `Broken pipe` (`ClientAbortException`) 발생

## 근본 원인

### collect_* 도구에 중복 호출 방어 메커니즘 부재

기존에 `fetch_github_releases` → `collect_github_releases` 간에는 루프 방어가 구현되어 있었음:
```java
// fetch_github_releases에서 이미 collect된 저장소 재조회 차단
if (metrics().isGitHubRepoCollected(correctedOwner, correctedRepo)) {
    int blockedCount = metrics().incrementAndGetFetchBlockedCount();
    if (blockedCount > 3) {
        throw new AgentLoopDetectedException(...);
    }
    return "BLOCKED: ...";
}
```

그러나 `collect_rss_feeds`와 `collect_scraped_articles`에는 **동일한 방어 메커니즘이 전혀 없었음**:
```java
// Before (문제 코드) - 어떤 차단도 없이 매번 실제 수집 실행
@Tool(name = "collect_rss_feeds", ...)
public DataCollectionResultDto collectRssFeeds(String provider) {
    metrics().incrementToolCall();
    // 검증만 하고 바로 실제 수집 실행 — 중복 호출 감지 없음
    return dataCollectionAdapter.collectRssFeeds(provider);
}
```

### LLM(gpt-4o-mini)의 행동 패턴
1. "RSS 피드 전체 수집" 요청 수신
2. 규칙 10에 따라 `collect_rss_feeds(OPENAI)` → `collect_rss_feeds(GOOGLE)` → `collect_scraped_articles(ANTHROPIC)` → `collect_scraped_articles(META)` 순서로 정상 수집 완료
3. **수집 완료 후 결과 요약 대신 동일 도구를 다시 호출** — LLM이 "더 수집해야 한다"고 판단
4. 매번 성공 응답(`new=0, duplicate=41`)을 받지만, LLM은 이를 "작업 미완료"로 해석하여 재호출 반복
5. 30회 제한 도달 → `RuntimeException` → 프론트엔드 Broken pipe

### 기존 방어가 동작하지 않은 이유
| 방어 메커니즘 | fetch_github_releases | collect_rss_feeds |
|---|---|---|
| 수집 완료 추적 (`collectedRepos`) | O | **X** |
| BLOCKED 응답 반환 | O | **X** |
| `AgentLoopDetectedException` (4회 초과) | O | **X** |
| `@Tool` description에 재호출 금지 힌트 | O | **X** |

## 수정 내용

### 1. `ToolExecutionMetrics.java` — RSS/Scraper 수집 완료 추적 추가

```java
// 새로 추가된 필드
private final Set<String> collectedRssProviders;      // RSS 수집 완료 provider
private final Set<String> collectedScraperProviders;   // Scraper 수집 완료 provider
private final AtomicInteger collectBlockedCount;       // 중복 차단 횟수

// 새로 추가된 메서드
void markRssProviderCollected(String provider)         // 수집 완료 마킹
boolean isRssProviderCollected(String provider)        // 수집 여부 확인 (전체 수집 시 개별도 포함)
void markScraperProviderCollected(String provider)
boolean isScraperProviderCollected(String provider)
int incrementAndGetCollectBlockedCount()               // 차단 횟수 증가
```

**전체 수집 처리**: `provider=""` (전체) 수집 완료 시 `_ALL_` 키로 저장하여, 이후 개별 provider(`OPENAI`, `GOOGLE` 등) 조회에도 수집 완료로 판정.

### 2. `EmergingTechAgentTools.java` — 중복 호출 차단 로직 추가

`collectRssFeeds()`, `collectScrapedArticles()` 양쪽에 동일 패턴 적용:

```java
// After (수정 코드)
@Tool(name = "collect_rss_feeds",
      value = "... 이미 수집한 provider를 다시 호출하지 마세요. 수집 완료 후 결과를 요약하고 작업을 종료하세요.")
public DataCollectionResultDto collectRssFeeds(String provider) {
    metrics().incrementToolCall();

    // 1. 입력값 검증
    if (hasValidationError(...)) { return failure; }

    // 2. 이미 수집 완료된 provider면 중복 수집 차단 (신규 추가)
    if (metrics().isRssProviderCollected(provider)) {
        int blockedCount = metrics().incrementAndGetCollectBlockedCount();
        if (blockedCount > 3) {
            throw new AgentLoopDetectedException(...);
        }
        return DataCollectionResultDto.failure("RSS_FEEDS", provider, 0, "BLOCKED: ...");
    }

    // 3. 실제 수집 실행
    DataCollectionResultDto result = dataCollectionAdapter.collectRssFeeds(provider);

    // 4. 수집 완료 마킹 (신규 추가)
    metrics().markRssProviderCollected(provider);

    return result;
}
```

### 3. `EmergingTechAgentImpl.java` — 로그 개선

```java
// Before
log.warn("... toolCalls={}, fetchBlocked={}, elapsed={}ms", ...);

// After
log.warn("... toolCalls={}, fetchBlocked={}, collectBlocked={}, elapsed={}ms",
        ..., metrics.getCollectBlockedCount(), ...);
```

## 수정 후 예상 동작

```
20:16:52 collect_rss_feeds(OPENAI)  → 정상 수집 (new=6, dup=35)  → OPENAI 마킹
20:17:06 collect_rss_feeds(GOOGLE)  → 정상 수집 (new=2, dup=14)  → GOOGLE 마킹
20:17:11 collect_scraped_articles(ANTHROPIC) → 정상 수집           → ANTHROPIC 마킹
20:17:14 collect_scraped_articles(META)      → 정상 수집           → META 마킹
20:17:15 collect_rss_feeds(OPENAI)  → BLOCKED 응답 (차단 1회)
20:17:15 collect_rss_feeds(GOOGLE)  → BLOCKED 응답 (차단 2회)
20:17:16 collect_rss_feeds(OPENAI)  → BLOCKED 응답 (차단 3회)
20:17:16 collect_rss_feeds(GOOGLE)  → AgentLoopDetectedException (차단 4회 > 3)
→ graceful 종료: "수집 작업이 완료되었습니다. (반복 조회 루프 감지로 자동 종료)"
```

**소요 시간**: 기존 ~2분 30초 (30회 실제 API 호출) → 수정 후 ~25초 (4회 실제 수집 + 최대 4회 BLOCKED)

## 영향 범위
- `collect_rss_feeds`: RSS 피드 수집 요청 시 무한 루프 방지
- `collect_scraped_articles`: 웹 스크래핑 수집 요청 시 동일 방어
- `collect_github_releases`: 기존 방어 로직 유지 (변경 없음)

## 관련 파일
| 파일 | 변경 유형 |
|------|----------|
| `api/agent/.../metrics/ToolExecutionMetrics.java` | RSS/Scraper 추적 필드 및 메서드 추가 |
| `api/agent/.../tool/EmergingTechAgentTools.java` | `collectRssFeeds`, `collectScrapedArticles`에 차단 로직 추가 |
| `api/agent/.../agent/EmergingTechAgentImpl.java` | 루프 감지 로그에 `collectBlocked` 추가 |

## 재발 방지
- 향후 새로운 `collect_*` 도구 추가 시 동일 패턴(수집 완료 마킹 → 중복 호출 차단 → `AgentLoopDetectedException`) 적용 필수
- `@Tool` description에 "이미 수집한 대상을 다시 호출하지 말 것" 힌트를 반드시 포함하여 LLM 레벨에서도 방어
