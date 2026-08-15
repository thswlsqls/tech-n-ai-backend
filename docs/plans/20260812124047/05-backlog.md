# 단계 05 — 백로그: 작고 독립적인 항목들

## 이 문서의 성격

01~04와 달리 순서가 없다. 각 항목은 서로 독립이고 크기가 작아서, 언제든 하나만 뽑아
task로 만들 수 있다. 항목마다 **근거 위치 / 왜 / 대략의 크기 / 선행 조건**만 적었다.

앞쪽 두 항목(A·B)은 새 기능이 아니라 **이미 있는 코드의 문제**다. 발견한 사실을 기록해
두는 것이 목적이고, 고칠지는 별도 판단이다. CLAUDE.md의 "관련 없는 죽은 코드는 언급만
하고 지우지 않는다"에 따라, 손대려면 그 자체로 요청이 있어야 한다.

---

## A. `api-chatbot`의 ChatMemory 제공자가 스텁 상태다

**근거**: `api/chatbot/.../memory/ConversationChatMemoryProvider.java`

확인된 사실:

- `MongoDbChatMemoryStore`를 주입받지만 **쓰지 않는다.** 반환하는
  `MessageWindowChatMemory.builder()`에 `chatMemoryStore(...)`를 걸지 않는다.
- `chatbot.chat-memory.max-tokens` 설정(`maxTokens` 필드)을 읽지만 **쓰지 않는다.**
- `maxMessages(10)`이 하드코딩돼 있고 주석에 "임시값"이라고 적혀 있다.
- 호출될 때마다 `log.warn`을 남긴다. 매 요청 경고 로그가 쌓인다.
- `TokenCountEstimator` 빈이 없어서 `TokenWindowChatMemory`를 못 쓴다는 TODO가 세 개 있다.
  **이 TODO는 이미 낡았다.** `LangChain4jConfig:79-82`가 등록하는 `OpenAiTokenCountEstimator`가
  `TokenCountEstimator`를 구현한다(1.10.0 jar에서 확인). 주입만 하면 되고, 막힌 것은 없다.

동작이 완전히 깨져 있지는 않다. `ChatbotServiceImpl.loadHistoryToMemory()`가 매 턴
DB에서 이력을 다시 읽어 메모리를 채우기 때문이다. 즉 **메모리 저장소가 이중으로 있고
그중 하나가 안 쓰이는 상태**다.

같은 `MongoDbChatMemoryStore`를 `api-agent`는 제대로 연결해서 쓴다
(`EmergingTechAgentImpl.java:69-73`, `maxMessages(30)`). 두 모듈의 방식이 다르다.

**크기**: 작음. 단, "무엇이 맞는 동작인가"를 먼저 정해야 한다 — 매 턴 DB 재로드를
유지할 것인지, `ChatMemoryStore`에 맡길 것인지.
**선행 조건**: 없음.

---

## B. 토큰 집계가 추정치이고, 세는 대상도 어긋나 있다

**근거**: `api/chatbot/.../service/TokenServiceImpl.java:53-62, 68-78`,
`ChatbotServiceImpl.java:108, 255-259`, `config/LangChain4jConfig.java:79-82`

`estimateTokens()`는 `OpenAiTokenCountEstimator`가 있으면 그것으로 센다. 이 빈이
`LangChain4jConfig`에 등록돼 있으므로 기본 경로는 tiktoken 기반 계산이고, 문자 수 기반
휴리스틱은 빈이 없거나 예외가 났을 때만 탄다. 그러니 "문자 수를 4로 나눈다"가 지금
동작은 아니다.

더 큰 문제는 무엇을 세느냐다. `ChatbotServiceImpl.trackTokenUsage()`는 입력 토큰을 사용자
질문(`request.message()`)만으로 센다. 검색으로 붙인 근거와 시스템 프롬프트가 빠져 있으니
실제 청구되는 입력 토큰과 크게 어긋난다. 그리고 `TokenServiceImpl.trackUsage()`는 로그만
남기고 어디에도 저장하지 않는다. DB에 남는 것은 메시지별 추정 토큰 수뿐이다.

즉 고칠 것이 셋이다 — (1) 세는 범위를 실제 프롬프트 전체로 맞추고, (2) 제공자가 응답에
실어 보내는 사용량을 쓰고, (3) 그 값을 어딘가에 저장한다. LangChain4j의 `ChatResponse`가
제공자 사용량을 담고 있으므로 (2)는 그쪽으로 바꾸면 추정이 아니라 실측이 된다.

**크기**: 중간. 저장 스키마와 이미 쌓인 데이터를 어떻게 할지 결정이 필요하다.
**선행 조건**: 없음. **02단계와 묶을 필요도 없다** — 02는 오프라인 비교용 토큰 집계를
추정치로 자기가 내고, 제공자 응답의 실측값을 DB에 쌓는 이 항목은 범위 밖으로 두었다
(`02-eval-observability.md:448-451`). 둘은 재는 대상이 다르다. 02는 실행끼리 비교할
수치를, 이 항목은 실제 청구액을 낸다.

---

## C. 제공자별 메시지 변환기 부채

**근거**: `api/chatbot/.../converter/` (`MessageFormatConverter`,
`OpenAiMessageConverter`, `AnthropicMessageConverter`),
`ChatbotServiceImpl.java:139-140`

일반 대화 경로가 이렇게 동작한다.

```java
Object providerFormat = messageConverter.convertToProviderFormat(messages, null);
String response = llmService.generate(providerFormat.toString());
```

메시지 목록을 제공자 형식 객체로 바꾼 뒤 `toString()`으로 문자열을 만들어 프롬프트로 넘긴다.
LangChain4j의 `ChatModel`은 `List<ChatMessage>`를 받아 제공자별 형식 변환을 내부에서
처리한다. 직접 만든 변환기가 왜 필요했는지 확인하고, 필요 없다면 정리 대상이다.

`toString()`으로 만든 문자열이 실제로 어떤 형태인지 로그로 한 번 확인해볼 가치가 있다.
의도한 형식이 아닐 수 있다.

**크기**: 작음~중간. 변환기의 존재 이유를 먼저 확인해야 한다.
**선행 조건**: 없음.

---

## D. Guardrails

**아티팩트**: `dev.langchain4j:langchain4j-guardrails` (최신 1.19.0-beta29,
1.10 라인에는 `1.10.0-beta18`, 2026-08-15 확인)

입력·출력 검증을 프레임워크 레벨에서 건다. 지금은 `api-agent`의 `ToolInputValidator`가
직접 만든 검증을 하고 있어서 겹치는 부분이 있다.

붙일 만한 곳: 프롬프트 주입 방어, 답변이 근거를 벗어났을 때 차단, 출력 형식 강제.

**크기**: 중간.
**선행 조건**: 없음. `1.10.0-beta18`이 배포돼 있고 그 pom이 `langchain4j:1.10.0`과
`langchain4j-core:1.10.0`에만 의존해서, 붙일 대상인 `api-agent`가 쓰는
`langchain4j:1.10.0`(`api/agent/build.gradle:21`)에 그대로 얹힌다. 03·04가 같은 근거로
"01단계는 선행이 아니다"로 고친 것과 같은 사안이다. 버전을 올릴지는 이 항목과 별개로
판단한다.

---

## E. MCP — 툴을 외부에 열거나 외부 툴을 가져오기

**아티팩트**: `langchain4j-mcp`, `langchain4j-community-mcp-server`,
`langchain4j-agentic-mcp` (모두 1.18.x 라인, 2026-08-12 확인)

두 방향이 있다.

- **서버로**: `EmergingTechAgentTools`를 MCP 서버로 노출하면 Claude Code 같은 클라이언트가
  그대로 쓴다. 이 저장소가 수집한 신기술 데이터를 외부 도구에서 조회하게 된다.
- **클라이언트로**: 외부 MCP 서버의 툴을 에이전트가 쓴다.

**서버 쪽이 이 백로그에서 작업량 대비 효과가 가장 좋다.** 툴이 이미 구현돼 있으므로
노출 계층만 붙이면 되고, 결과가 눈에 보인다.

**크기**: 서버는 작음, 클라이언트는 중간.
**선행 조건**: 방향에 따라 다르다. **서버 쪽은 01단계가 맞다** —
`langchain4j-community-mcp-server`와 `langchain4j-agentic-mcp`는 1.10 라인에 없다
(2026-08-15 확인). **클라이언트 쪽은 선행이 없다** — `langchain4j-mcp`는 `1.10.0-beta18`이
배포돼 있어 지금 라인에서 시험할 수 있다. 위에서 권한 서버 쪽을 먼저 한다면 01이 앞선다.
어느 쪽이든 인증·권한을 어떻게 걸지는 정해야 한다(지금 툴은 관리자 전용 경로에 있다).

---

## F. 장기 기억

지금 대화 기억은 최근 N개를 유지하는 창(window) 방식이다. `api-agent`는 30개
(`EmergingTechAgentImpl.MAX_MESSAGES`), `api-chatbot`은 10개(항목 A 참고).
창을 넘어간 내용은 사라진다.

대안은 오래된 대화를 요약해 압축하거나, 대화에서 사실만 뽑아 따로 저장하고 필요할 때
검색하는 방식이다. 후자가 요즘 "agent memory"라고 불리는 쪽이다.

**크기**: 큼. 저장 위치·갱신 시점·검색 방법을 다 정해야 한다.
**선행 조건**: 항목 A가 먼저다. 지금 메모리 계층이 정리되지 않은 상태에서 그 위에
쌓으면 문제가 겹친다.

---

## G. Skills

**아티팩트**: `dev.langchain4j:langchain4j-skills` (최신 1.19.0-beta29, 2026-08-15 확인),
`langchain4j-experimental-skills-shell`

프롬프트를 코드에서 빼내 파일로 관리하는 방식. 지금은 `AgentPromptConfig`와
`PromptServiceImpl`이 프롬프트를 들고 있다.

효과는 프롬프트를 코드 배포 없이 바꾸고, 변경 이력을 따로 볼 수 있다는 것이다.
지금 프롬프트 개수를 생각하면 급하지 않다.

**크기**: 중간.
**선행 조건**: 01단계.

---

## H. 모델 라우팅 — **접음 (2026-08-15)**

> **이 항목은 접기로 결정했다.** 전제가 성립하지 않는다는 것이 확인됐다.
> 아래 내용은 왜 접었는지 남기려고 둔다 — 지우면 같은 제안이 다시 올라온다.
> **다시 열려면 먼저 "상위 모델을 하나 더 둘지"를 결정해야 한다.**

**아티팩트**: `dev.langchain4j:langchain4j-community-model-router` (1.18.0-beta28)

쉬운 질문은 작은 모델로, 어려운 질문만 큰 모델로 보내 비용을 줄이는 방식.
`LangChain4jConfig`가 지금 어떤 모델을 어떻게 쓰는지 먼저 확인해야 한다.

**그 확인의 답이 나왔고, 결론은 지금 이 항목이 성립하지 않는다는 것이다.**
이 저장소의 챗 모델은 두 곳뿐이고 둘 다 `gpt-4o-mini`다
(`api/chatbot/src/main/resources/application-chatbot-api.yml:11`,
`api/agent/src/main/resources/application-agent-api.yml:6`). 자바 기본값도 같다
(`LangChain4jConfig.java:28`, `AiAgentConfig.java:23`). 즉 "어려운 질문만 큰 모델로"의
전제인 **큰 모델이 저장소에 없다.** 지금 라우팅을 넣으면 내려보낼 곳이 없어 비용이 줄지
않고, 값을 내려면 먼저 더 비싼 모델을 붙여야 하므로 오히려 비용이 는다.

**따라서 이 항목은 상위 모델을 도입하기로 정한 뒤에야 다시 본다.** 그때는 "라우팅을
넣을까"가 아니라 "모델을 하나 더 둘까"가 먼저 결정할 일이다.

접기 전에는 "효과를 말하려면 비용 측정이 있어야 하므로 항목 B가 앞선다"고 적었다.
그 선후 관계는 이 항목을 다시 열 때 되살아난다.

**크기**: 해당 없음 (접음).
**선행 조건**: 상위 모델 도입 결정. 그 결정이 나기 전에는 이 항목을 task로 만들지 않는다.

---

## I. 기타 기록

- `IntentClassificationServiceImpl.isGreeting()`(81번째 줄)과 `GREETING_KEYWORDS`가
  정의만 되고 호출되지 않는다. → 04단계에서 분류기를 손댈 때 함께 처리하는 게 자연스럽다.
- `ResultRefinementChain.refine()`이 오버로드 3개(**인자 2·3·4개**)인데 운영 호출은
  4개짜리 하나뿐으로 보인다. 다만 **2개짜리는 `ResultRefinementChainTest`에서 12곳이
  호출한다**(2026-08-15 확인). 지우려 들면 그 테스트가 깨지므로, 없앨지는 테스트를 어떻게
  할지와 함께 정해야 한다. 지금은 기록만 남긴다.

**크기**: 작음. 둘 다 다른 작업에 딸려 처리하는 것이 자연스럽다.
**선행 조건**: 없음. 첫째 항목은 04단계가 분류기를 손댈 때, 둘째는 `ResultRefinementChain`을
건드리는 작업이 생길 때 함께 본다.

---

## impl input 생성 힌트

이 문서의 항목은 하나씩 독립 task로 만든다. 여러 개를 묶지 않는다 — 성격이 다르고,
묶으면 리뷰가 어려워진다.

우선순위를 매긴다면:

1. **A (ChatMemory 스텁)** — 실제 문제이고 작다. 매 요청 경고 로그가 쌓이는 것도 있다.
2. **E (MCP 서버)** — 작업량 대비 결과가 눈에 보인다.
3. **B (토큰 실측)** — 실제 청구액을 확인할 수 있게 된다. (항목 H의 선행이기도 했으나
   H를 접었으므로 지금은 그 자체로 값이 있는지로만 판단한다.)
   **02단계의 전제는 아니다** — 02는 오프라인 비교용 토큰 집계를 자기 범위에서 추정치로
   직접 낸다(`02-eval-observability.md:448-451`). 02를 기다릴 필요 없이 언제든 할 수 있다.
4. 나머지 — 필요가 생겼을 때.

A·B·C·I는 **버그 수정 성격**이라 task를 쓸 때 "재현 → 테스트 먼저 → 통과" 순서로
성공 기준을 잡는다. D~H는 **기능 추가**라 범위 제외를 분명히 적어야 한다.
