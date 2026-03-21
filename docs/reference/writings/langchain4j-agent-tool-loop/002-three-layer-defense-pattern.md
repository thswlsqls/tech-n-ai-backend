# LLM Agent의 Tool Loop를 막는 3계층 방어 패턴

**멱등성 추적, 프롬프트 힌트, 에러 메시지 설계로 Agent 폭주 방지하기**

## SEO 최적화 제목 후보

- LangChain4j Agent Tool 무한 호출 방어 패턴 — 멱등성 추적과 프롬프트 힌트로 루프 차단하기
- LLM Agent Tool Loop 해결법 — LangChain4j 3계층 방어 전략 구현기
- LangChain4j Tool 반복 호출 방지 — ToolExecutionMetrics와 @Tool description 활용 가이드
- Java LLM Agent 안정화 — Tool 멱등성 추적, 에러 메시지 설계, 프롬프트 방어 패턴

---

## 이전 글에서 이어서

[이전 글](001-why-llm-agent-called-tool-30-times.md)에서 LangChain4j 기반 Agent가 같은 Tool을 30회 반복 호출하며 폭주한 두 가지 사고를 분석했습니다. 근본 원인은 LLM의 판단 계층, 에러 처리 계층, Adapter 계층에 걸쳐 있었고, 세 계층 모두에서 LLM이 루프를 빠져나올 수 있는 신호가 제대로 전달되지 않은 것이 핵심이었습니다.

이 글에서는 그 원인에 대응하여 적용한 3계층 방어 패턴을 정리합니다. Tool 레벨에서 멱등성을 추적하는 하드 방어, `@Tool` description을 통한 프롬프트 방어, 그리고 에러 메시지와 예외 전파를 통한 시스템 방어입니다.


## 방어 설계의 출발점 — LLM은 지시하지 않으면 멈추지 않는다

방어 패턴을 설계하기 전에 한 가지 전제를 정해야 했습니다. "LLM이 알아서 판단해줄 것"이라는 기대를 버리는 것이었습니다.

이전 사고에서 확인한 것처럼, LLM은 Tool의 반환값을 기반으로 다음 행동을 결정합니다. 반환값이 "작업 완료"를 명확히 알려주지 않으면 LLM은 계속 시도합니다. 에러 메시지가 "다른 방법을 시도해주세요"라고 하면 같은 Tool을 다시 부릅니다. 빈 결과가 에러인지 실제로 데이터가 없는 것인지 구분할 수 없으면, 재시도하는 쪽으로 판단합니다.

따라서 방어 설계의 원칙은 "LLM이 올바른 판단을 내릴 수 있도록 명확한 신호를 제공하되, 그래도 루프에 빠지면 강제로 종료한다"로 정했습니다. 소프트 방어(프롬프트)로 먼저 안내하고, 하드 방어(코드)로 최종 차단하는 구조입니다.


## 하드 방어 — ToolExecutionMetrics를 통한 멱등성 추적

가장 핵심적인 변경은 `ToolExecutionMetrics` 클래스의 확장이었습니다. 이 클래스는 Agent 실행 1회마다 생성되어 해당 실행 동안의 모든 Tool 호출을 추적하는 역할을 합니다. `EmergingTechAgentImpl`에서 Agent 실행이 시작될 때 `new ToolExecutionMetrics()`로 인스턴스를 생성하고, `tools.bindMetrics(metrics)`로 Tool 클래스에 바인딩합니다. 실행이 끝나면 `tools.unbindMetrics()`로 정리하여 ThreadLocal을 통한 메모리 누수를 방지합니다.

기존에 이 클래스에는 `collectedGitHubRepos`라는 `Set<String>` 필드가 있어서, `collect_github_releases`로 수집이 완료된 저장소를 `owner/repo` 형태로 저장하고 있었습니다. 이번에 같은 패턴으로 다음 필드들을 추가했습니다.

```java
private final Set<String> collectedRssProviders;
private final Set<String> collectedScraperProviders;
private final AtomicInteger collectBlockedCount;
```

`collectedRssProviders`는 `collect_rss_feeds`로 수집이 완료된 provider를, `collectedScraperProviders`는 `collect_scraped_articles`로 수집이 완료된 provider를 추적합니다. `collectBlockedCount`는 이미 수집 완료된 대상에 대한 재수집 시도가 차단된 횟수를 세는 카운터입니다.

여기서 한 가지 설계 결정이 있었습니다. "전체 수집" 처리입니다. 사용자가 "RSS 전체 수집해주세요"라고 요청하면 Agent는 `collect_rss_feeds`를 provider 인자 없이 호출할 수 있습니다. 이 경우 `_ALL_`이라는 키로 `collectedRssProviders`에 저장합니다. 이후 LLM이 개별 provider(예: `OPENAI`)로 다시 수집을 시도하면, `isRssProviderCollected` 메서드가 `_ALL_` 키가 있는지도 확인하여 "이미 전체 수집이 완료되었으므로 개별 수집도 불필요하다"고 판단합니다. 이렇게 하지 않으면, 전체 수집 후 개별 provider를 하나씩 다시 수집하는 루프가 발생할 수 있었습니다.


## 하드 방어의 실제 흐름

`collect_rss_feeds` 메서드를 예로 들면, 수정 후의 실행 흐름은 다음과 같습니다.

```java
@Tool(name = "collect_rss_feeds",
      value = "OpenAI/Google AI 블로그 RSS 피드를 수집하여 DB에 저장합니다. "
            + "수집 결과(신규/중복/실패 건수)를 반환합니다. "
            + "이미 수집한 provider를 다시 호출하지 마세요. "
            + "수집 완료 후 결과를 요약하고 작업을 종료하세요.")
public DataCollectionResultDto collectRssFeeds(String provider) {
    metrics().incrementToolCall();

    // 1단계: 입력값 검증
    if (hasValidationError(...)) { return failure; }

    // 2단계: 이미 수집 완료된 provider면 차단
    if (metrics().isRssProviderCollected(provider)) {
        int blockedCount = metrics().incrementAndGetCollectBlockedCount();
        if (blockedCount > 3) {
            throw new AgentLoopDetectedException(...);
        }
        return DataCollectionResultDto.failure("RSS_FEEDS", provider, 0,
            "BLOCKED: provider '%s' RSS 피드는 이미 수집 완료되었습니다. "
          + "이 provider를 다시 수집하지 마세요. "
          + "수집 결과를 요약하고 작업을 완료하세요.");
    }

    // 3단계: 실제 수집 실행
    DataCollectionResultDto result = dataCollectionAdapter.collectRssFeeds(provider);

    // 4단계: 수집 완료 마킹
    metrics().markRssProviderCollected(provider);

    return result;
}
```

이 흐름에서 중요한 점은, `BLOCKED` 응답이 바로 예외를 던지는 것이 아니라 먼저 LLM에게 "이미 완료되었다"는 메시지를 반환한다는 것입니다. LLM이 이 메시지를 보고 스스로 루프를 중단하면 가장 이상적입니다. 하지만 그래도 계속 호출하면 차단 횟수가 누적되고, 3회를 초과하면 `AgentLoopDetectedException`이 발생하여 Agent 실행 자체를 종료합니다.

`AgentLoopDetectedException`은 일반 `RuntimeException`을 상속하는 간단한 클래스입니다. 핵심은 이 예외가 `ToolErrorHandlers`에서 특별 처리된다는 점입니다.

```java
public static ToolErrorHandlerResult handleToolExecutionError(
        Throwable error, ToolErrorContext context) {
    if (error instanceof AgentLoopDetectedException) {
        log.warn("Agent 루프 감지 - 강제 종료: {}", error.getMessage());
        throw (AgentLoopDetectedException) error;
    }
    // ... 일반 에러 처리
}
```

다른 예외는 LLM에게 텍스트 메시지로 전달되지만, `AgentLoopDetectedException`은 re-throw됩니다. 이렇게 하면 LangChain4j의 Tool 실행 루프를 강제로 탈출하게 됩니다. 그리고 `EmergingTechAgentImpl`에서 이 예외를 catch하여 graceful한 종료 메시지를 생성합니다.

```java
catch (AgentLoopDetectedException e) {
    return AgentExecutionResult.success(
        "수집 작업이 완료되었습니다. (반복 조회 루프 감지로 자동 종료)",
        sessionId, toolCallCount, metrics.getAnalyticsCallCount(), elapsed);
}
```

여기서 `AgentExecutionResult.success()`로 반환하는 것이 의도적인 선택입니다. 루프가 감지되었더라도 그 전까지의 수집은 정상적으로 완료되었을 가능성이 높기 때문입니다. 실제로 첫 번째 사고 케이스에서도 4개 provider의 수집은 모두 정상 완료된 후에 루프가 시작된 것이었습니다. 사용자에게는 "실패"가 아닌 "완료(자동 종료)" 메시지를 보여주는 것이 더 정확한 표현입니다.


## 연속 중복 호출 감지 — 범용적인 두 번째 방어선

`collect_*` 계열 Tool에는 provider 단위의 수집 완료 추적이 적합했지만, 모든 Tool에 이런 도메인 특화 추적을 넣는 것은 현실적이지 않았습니다. 예를 들어 `get_emerging_tech_statistics`는 `groupBy`, `startDate`, `endDate` 등 조합이 다양해서 "이미 완료된 조회"를 정의하기 어렵습니다.

이런 Tool에는 범용적인 연속 중복 호출 감지 패턴을 적용했습니다. `ToolExecutionMetrics`의 `lastToolCallKey`와 `consecutiveDuplicateCount`를 활용하는 방식입니다.

```java
private static final int LOOP_FORCE_STOP_THRESHOLD = 5;

private boolean isConsecutiveDuplicate(String toolName, String args) {
    if (metrics().isConsecutiveDuplicate(toolName, args)) {
        int count = metrics().getConsecutiveDuplicateCount();
        if (count > LOOP_FORCE_STOP_THRESHOLD) {
            throw new AgentLoopDetectedException(
                "%s 연속 중복 호출 %d회 초과. 동일한 인자로 반복 호출하는 "
              + "루프가 감지되어 강제 종료합니다.".formatted(toolName, count));
        }
        return true;
    }
    return false;
}
```

`isConsecutiveDuplicate`는 Tool 이름과 인자를 조합한 문자열을 `lastToolCallKey`와 비교합니다. 같으면 `consecutiveDuplicateCount`를 증가시키고, 다르면 0으로 리셋합니다. 연속으로 같은 Tool이 같은 인자로 2회 이상 호출되면 `true`를 반환합니다.

이 메서드를 사용하는 Tool에서는 `STOP:` 접두어가 붙은 메시지를 반환합니다.

```java
if (isConsecutiveDuplicate("get_emerging_tech_statistics", callArgs)) {
    return new StatisticsDto(groupBy, startDate, endDate, 0, List.of(),
        "STOP: 이 통계는 이미 조회되었습니다. 이전에 받은 결과를 사용하여 "
      + "Markdown 표와 Mermaid 차트로 응답을 작성하세요. "
      + "이 Tool을 다시 호출하지 마세요.");
}
```

`STOP:` 접두어는 LLM에게 "이 Tool 호출을 중단하라"는 강한 신호를 주기 위한 것입니다. 단순히 "이미 조회되었습니다"보다 `STOP:`이라는 명시적 키워드가 LLM의 반복 호출을 억제하는 데 더 효과적이었습니다. 그리고 "이전에 받은 결과를 사용하여 응답을 작성하세요"라는 문구로, LLM이 다음에 무엇을 해야 하는지 구체적인 행동을 제시합니다.


## 프롬프트 방어 — @Tool description의 재설계

두 번째 계층은 `@Tool` annotation의 `value` 필드를 통한 프롬프트 방어입니다. LangChain4j에서 `@Tool`의 `value`는 LLM에게 Tool의 설명으로 전달됩니다. LLM은 이 설명을 읽고 Tool을 언제, 어떻게 사용할지 판단합니다.

수정 전의 description은 Tool의 기능만 설명하고 있었습니다. "RSS 피드를 수집합니다", "GitHub 릴리스를 조회합니다" 정도의 문구였습니다. 수정 후에는 사용 조건과 종료 조건을 명시적으로 포함했습니다.

수정 전:

```java
@Tool(name = "collect_rss_feeds",
      value = "OpenAI/Google AI 블로그 RSS 피드를 수집하여 DB에 저장합니다.")
```

수정 후:

```java
@Tool(name = "collect_rss_feeds",
      value = "OpenAI/Google AI 블로그 RSS 피드를 수집하여 DB에 저장합니다. "
            + "수집 결과(신규/중복/실패 건수)를 반환합니다. "
            + "이미 수집한 provider를 다시 호출하지 마세요. "
            + "수집 완료 후 결과를 요약하고 작업을 종료하세요.")
```

추가된 문구는 두 가지입니다. "이미 수집한 provider를 다시 호출하지 마세요"는 중복 호출 방지 힌트이고, "수집 완료 후 결과를 요약하고 작업을 종료하세요"는 종료 조건 힌트입니다.

이 방식이 효과가 있는 이유는 LangChain4j가 Tool description을 시스템 프롬프트의 일부로 LLM에 전달하기 때문입니다. LLM은 매 턴마다 이 설명을 참조하여 Tool 사용 여부를 결정합니다. 물론 LLM이 이 힌트를 무시할 가능성은 있습니다. 프롬프트는 "지시"가 아니라 "권고"에 가깝기 때문입니다. 그래서 이 계층만으로는 충분하지 않고, 하드 방어와 반드시 병행해야 합니다.

프롬프트 방어의 가치는 "대부분의 정상 케이스에서 불필요한 차단을 발생시키지 않고 루프를 예방한다"는 데 있습니다. 하드 방어의 `BLOCKED` 응답이나 `AgentLoopDetectedException`은 이미 문제가 발생한 후의 대응입니다. 프롬프트 힌트는 문제가 발생하기 전에 LLM이 스스로 올바른 판단을 내리도록 안내하는 예방적 방어입니다.


## 시스템 방어 — 에러 메시지와 예외 전파의 재설계

세 번째 계층은 Tool 실행 실패 시의 에러 처리 방식을 변경한 것입니다. 두 가지 변경이 있었습니다.

첫 번째는 `ToolErrorHandlers`의 에러 메시지 변경입니다.

```java
// 수정 전
"Tool '%s' 실행 실패: %s. 다른 방법을 시도해주세요."

// 수정 후
"Tool '%s' 실행 실패: %s. 이 Tool을 동일한 인자로 재시도하지 마세요. "
+ "해당 작업을 건너뛰고 다음 작업으로 진행하세요."
```

변경의 핵심은 LLM에게 구체적인 행동을 지시하는 것입니다. "다른 방법을 시도해주세요"는 모호합니다. LLM이 할 수 있는 "다른 방법"이 같은 Tool을 다시 부르는 것밖에 없다면, 이 메시지는 사실상 재시도를 유도합니다. 반면 "동일한 인자로 재시도하지 마세요. 해당 작업을 건너뛰고 다음 작업으로 진행하세요"는 두 가지 행동을 명확히 합니다. 재시도 금지, 그리고 다음 작업으로 이동. LLM에게 선택지를 줄여주는 것이 중요했습니다.

두 번째는 `GitHubToolAdapter`의 예외 전파 방식 변경입니다.

```java
// 수정 전 — 예외를 빈 리스트로 변환
} catch (Exception e) {
    log.error("GitHub releases 조회 실패: ...", e);
    return List.of();
}

// 수정 후 — RuntimeException으로 전파
} catch (Exception e) {
    log.error("GitHub releases 조회 실패: ...", e);
    throw new RuntimeException(
        "GitHub releases 조회 실패 (%s/%s): %s. "
      + "이 저장소에 대해 더 이상 재시도하지 마세요."
            .formatted(owner, repo, e.getMessage()), e);
}
```

이 변경으로 예외가 `ToolErrorHandlers`까지 전파되고, LLM에게는 "실패"라는 명확한 신호가 전달됩니다. 빈 리스트가 "데이터 없음"인지 "에러"인지 구분할 수 없었던 문제가 해결된 것입니다. 예외 메시지 자체에도 "이 저장소에 대해 더 이상 재시도하지 마세요"라는 문구를 포함하여, `ToolErrorHandlers`의 에러 메시지와 함께 이중으로 재시도를 억제합니다.


## 수정 전후 동작 비교

첫 번째 사고(collect_rss_feeds 루프)의 경우, 수정 전에는 30회 동안 실제 RSS 수집 API가 호출되며 2분 30초가 소요되었습니다. 수정 후에는 4개 provider에 대한 실제 수집 4회가 완료된 뒤, LLM이 다시 호출을 시도하면 `BLOCKED` 응답을 받습니다. 대부분의 경우 `BLOCKED` 응답과 `@Tool` description의 종료 힌트를 보고 LLM이 스스로 결과 요약으로 전환합니다. 만약 계속 시도하더라도 차단 4회째에 `AgentLoopDetectedException`이 발생하여 graceful 종료됩니다. 전체 소요 시간은 약 25초로, 기존 대비 6분의 1 수준입니다.

두 번째 사고(fetch_github_releases 루프)의 경우, 수정 전에는 30회 동안 GitHub API가 호출되며 50초가 소요되었습니다. 수정 후에는 첫 번째 호출에서 실제 API 요청이 실행되고, 에러가 발생하면 `RuntimeException`으로 전파되어 LLM에게 "실패 + 재시도 금지" 메시지가 전달됩니다. LLM은 이 메시지를 받고 다음 작업으로 진행하거나, 그래도 재시도하면 최대 4회 차단 후 강제 종료됩니다. 소요 시간은 약 5초입니다.


## 함께 발견된 부수 이슈들

Tool Loop 디버깅 과정에서 Agent 시스템의 다른 영역에서도 수정이 필요한 문제들이 발견되었습니다. 직접적으로 Tool Loop와 관련되지는 않지만, Agent 시스템의 안정성과 관찰성에 영향을 미치는 것들이었습니다.

먼저, MongoDB에서 대화 세션 문서가 중복 생성되는 문제가 있었습니다. CQRS 구조에서 Kafka를 통해 Aurora의 세션 생성 이벤트를 MongoDB로 동기화하는데, `agent-api`와 `chatbot-api`가 서로 다른 consumer group으로 같은 토픽을 구독하고 있었습니다. 두 consumer가 동시에 `findBySessionId`에서 "없음"을 확인하고 각각 새 document를 insert하여 중복이 발생한 것입니다. application-level upsert(`find → orElse(new) → save`)를 `MongoTemplate.upsert`로 변경하여 MongoDB-level atomic operation으로 해결했습니다. 여러 consumer group이 동일 토픽을 구독하는 CQRS 환경에서는 write 시 반드시 atomic upsert를 사용해야 한다는 점을 확인한 사례였습니다.

Agent 실행이 실패했을 때 ASSISTANT 메시지가 대화 이력에 저장되지 않는 문제도 있었습니다. `AgentFacade`에서 `result.success()`가 `true`인 경우에만 메시지를 저장하고 있었는데, 이 때문에 실패한 실행의 결과를 세션 재조회 시 확인할 수 없었습니다. 관리자 도구에서는 실패 정보도 이력으로 남기는 것이 운영과 디버깅에 유리하므로, 성공 여부와 무관하게 항상 저장하도록 변경했습니다.

프론트엔드에서 페이지네이션 size 불일치로 대화 내용이 표시되지 않는 문제도 있었습니다. 최신 메시지를 먼저 보여주기 위해 probe 요청(`size=1`)으로 `totalPageNumber`를 얻고, actual 요청(`size=50`)으로 마지막 페이지를 요청하는 전략이었는데, `totalPageNumber`이 `size=1` 기준으로 계산된 값이므로 `size=50`으로 요청하면 offset이 초과되어 빈 결과가 반환되었습니다. `totalPageNumber` 대신 `totalSize`를 기반으로 실제 page size에 맞게 재계산하는 것으로 수정했습니다.


## 새로운 Tool을 추가할 때의 체크리스트

이번 사고와 수정을 거치면서, Agent에 새로운 Tool을 추가할 때 확인해야 할 항목들이 정리되었습니다.

`@Tool` description에 기능 설명뿐 아니라 사용 조건과 종료 조건을 포함해야 합니다. "이미 처리한 대상을 다시 호출하지 말 것", "완료 후 결과를 요약하고 종료할 것" 같은 힌트가 없으면 LLM은 Tool을 반복 호출할 수 있습니다.

`ToolExecutionMetrics`에 해당 Tool의 실행 완료를 추적하는 필드를 추가해야 합니다. 수집 계열 Tool이면 provider나 대상 단위로, 조회 계열 Tool이면 최소한 연속 중복 호출 감지 패턴이라도 적용해야 합니다. 빈 결과도 "완료"로 마킹하는 것이 중요합니다. 재시도해도 결과가 달라지지 않기 때문입니다.

외부 API를 호출하는 Adapter에서는 예외를 삼키지 않아야 합니다. 빈 리스트나 기본값으로 변환하면 LLM이 에러와 정상 응답을 구분할 수 없게 됩니다. `RuntimeException`으로 전파하여 `ToolErrorHandlers`가 LLM에게 에러를 명시적으로 전달할 수 있게 해야 합니다.

테스트에서는 중복 호출 차단과 `AgentLoopDetectedException` 발생을 검증해야 합니다. 정상 호출 → 중복 호출 → `BLOCKED` 반환 → 임계값 초과 → 예외 발생이라는 전체 흐름을 테스트로 확인해야 나중에 로직이 변경되더라도 방어가 유지됩니다.


## 마무리

이번에 적용한 3계층 방어 패턴을 돌이켜보면, 각 계층이 서로 다른 역할을 하고 있습니다. 프롬프트 방어는 LLM이 스스로 올바른 판단을 내리도록 안내합니다. 하드 방어는 프롬프트를 무시하고 반복 호출하더라도 실제 외부 API 호출을 차단합니다. 시스템 방어는 에러 상황에서 LLM이 재시도 대신 다음 작업으로 진행하도록 유도합니다.

어느 한 계층만으로는 충분하지 않습니다. 프롬프트만 있으면 LLM이 무시할 수 있고, 하드 방어만 있으면 매번 차단 응답을 거쳐야 해서 비효율적이며, 시스템 방어만 있으면 에러가 아닌 정상 응답에서 발생하는 루프를 막을 수 없습니다. 세 계층이 함께 작동할 때 대부분의 루프 시나리오를 커버할 수 있다는 것을 확인했습니다.

LLM Agent 시스템을 개발하면서 느끼는 점은, 전통적인 소프트웨어와 다른 설계 감각이 필요하다는 것입니다. 함수의 반환값이 코드가 아닌 LLM에 의해 해석되고, 에러 메시지가 로그가 아닌 LLM의 다음 행동을 결정하는 입력이 됩니다. Tool을 설계할 때 "이 반환값을 LLM이 어떻게 해석할 것인가", "이 에러 메시지가 LLM에게 어떤 행동을 유도할 것인가"를 함께 고민해야 합니다. 이번 디버깅을 통해 그런 관점을 좀 더 구체적으로 갖게 된 것 같습니다.
