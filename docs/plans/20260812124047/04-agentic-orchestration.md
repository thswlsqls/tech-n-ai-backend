# 단계 04 — 라우팅을 되돌아갈 수 있는 워크플로로

## 한 줄 요약

키워드 매칭 + `switch`로 경로를 한 번 고르고 끝나는 구조를, 결과를 보고 다시 시도할 수
있는 워크플로로 바꾼다.

## 왜 이 단계인가

지금 챗봇의 오케스트레이터는 `ChatbotServiceImpl.generateResponse()`의 `switch`문이다
(`ChatbotServiceImpl.java:79-102`). 의도를 한 번 분류해 경로 하나를 고르면 그대로 끝난다.

여기서 오는 한계가 두 가지다.

**되돌아갈 수 없다.** RAG 경로로 갔는데 검색 결과가 비었거나 유사도가 낮아도, 그 결과로
답을 만든다. 웹 검색으로 보강하거나 질의를 바꿔 다시 찾는 경로가 없다. 반대로 웹 검색
경로에서만 fallback이 있는데(`handleWebSearchPipeline`, 결과가 비면 LLM 직접 응답),
이것도 한 번뿐이다.

**분류가 키워드 매칭이다.** 아래 "현재 코드 상태"에 적었듯 실제 동작이 의도와 다르다.

이 두 가지를 고치는 게 요즘 adaptive RAG / corrective RAG라고 부르는 것이다.

## 현재 코드 상태 — 확인된 사실

**`IntentClassificationServiceImpl`은 LLM을 쓰지 않는다.** 하드코딩된 한국어·영어 키워드
집합 네 개(`GREETING_KEYWORDS`, `RAG_KEYWORDS`, `WEB_SEARCH_KEYWORDS`, `LLM_DIRECT_KEYWORDS`)와
정규식 하나로 분기한다.

분류 순서와 그 결과를 그대로 읽으면 이렇다.

1. `@agent` 접두사 → `AGENT_COMMAND`
2. `RAG_KEYWORDS` 포함 → `RAG_REQUIRED`
3. `WEB_SEARCH_KEYWORDS` 포함 → `WEB_SEARCH_REQUIRED`
4. 질문 형태이고 `LLM_DIRECT_KEYWORDS`가 없으면 → `RAG_REQUIRED`
5. 그 외 → `LLM_DIRECT`

**2번이 3번보다 먼저 걸린다는 점이 문제다.** `RAG_KEYWORDS`에 `"ai"`, `"모델"`, `"기술"`,
`"어떤"`, `"무엇"`, `"정보"`, `"알려"` 같은 매우 넓은 항목이 들어 있다. `"ai"`는 부분
문자열로 매칭되므로 영어 단어 상당수에 걸린다. 결과적으로 웹 검색이 필요한 질문
("오늘 최신 AI 뉴스 알려줘")도 대부분 2번에서 `RAG_REQUIRED`로 빠진다.

또 `isGreeting()`은 정의만 되어 있고 호출되지 않는다(`IntentClassificationServiceImpl.java:81`).
`GREETING_KEYWORDS`도 따라서 쓰이지 않는다.

**`api-agent`는 이미 툴 루프로 돈다.** `EmergingTechAgentImpl.initAssistant()`가
`AiServices.builder()`로 어시스턴트를 만들고, 툴 에러 핸들러 3종과
`maxSequentialToolsInvocations(30)`을 건다(`EmergingTechAgentImpl.java:68-84`).
개념 거리는 가깝다.

**챗봇과 에이전트는 `@agent` 접두사로만 이어져 있다.** `handleAgentCommand()`가
`ADMIN` 권한을 확인하고 `AgentDelegationService`로 넘긴다. 일반 사용자는 에이전트에
닿지 못한다.

## 쓸 수 있는 것 — 공식 문서 확인 결과 (2026-08-12)

`docs.langchain4j.dev/tutorials/agents` 기준. 모듈 전체가 experimental로 명시돼 있다.

에이전트는 `@Agent`를 붙인 인터페이스 하나로 정의하고, `AgenticServices.agentBuilder()`로
만든다. 일반 AI 서비스와 같되 서로 조합할 수 있다는 점이 다르다.

| 빌더 | 하는 일 |
|------|--------|
| `agentBuilder()` | 단일 에이전트. `outputKey`로 결과를 공유 변수에 쓴다 |
| `sequenceBuilder()` | 순차 실행 |
| `parallelBuilder()` | 독립적인 작업을 동시에 |
| `loopBuilder()` | `maxIterations`와 `exitCondition`으로 반복. 종료 조건은 공유 상태를 읽어 판정 |
| `conditionalBuilder()` | 조건에 따라 하위 에이전트 선택 |
| `supervisorBuilder()` | 계획 모델이 하위 에이전트 호출을 결정. `responseStrategy` 지정 |
| `humanInTheLoopBuilder()` | 사람이 중간에 개입. `inputKey`/`outputKey`로 흐름에 참여 |

**`AgenticScope`** — 에이전트들이 공유하는 데이터. 한 에이전트가 `outputKey`로 쓰고
다른 에이전트가 읽는다. 루프의 종료 조건도 이 상태를 읽는다.

**오류 복구** — `ErrorContext`를 받아 `ErrorRecoveryResult`를 돌려주는 핸들러를 붙일 수 있다.
`throwException()`(기본) / `retry()` / `result(Object)` 세 가지다.

**하위 에이전트로 워크플로를 넣을 수 있다.** supervisor 입장에서는 복잡한 워크플로도
에이전트 하나로 보인다. 단계적으로 옮기기에 유리한 성질이다.

### langgraph4j를 안 쓰는 이유

`org.bsc.langgraph4j`가 Maven Central에 있고 `langgraph4j-langchain4j` 어댑터도 있다.
최신은 `1.9.0-beta2`이고 정식 릴리스는 `1.8.24`까지 나와 있다(2026-08-15 확인).
**베타만 있는 프로젝트가 아니다** — 아래 근거를 성숙도 문제로 읽지 마라. 그래도 기본 선택에서 뺐다.

- 이미 LangChain4j 생태계 위에 있어서, 같은 계열 모듈이 학습·유지보수 비용이 낮다.
- 지금 필요한 것(조건 분기, 재시도 루프)은 `langchain4j-agentic`으로 표현된다.
- LangChain 공식 조직이 아닌 개인/커뮤니티 네임스페이스 배포다.

**다시 검토할 조건**: 실행을 중단했다가 나중에 재개해야 하거나(`langgraph4j-postgres-saver`),
그래프 실행을 화면으로 봐야 할 때(`langgraph4j-studio`). 지금 요구에는 없다.

## 범위

### 포함

**(a) 의도 분류 개선**
키워드 매칭의 실제 동작을 먼저 기록하고(어떤 입력이 어디로 가는지), 그다음 고친다.
LLM 분류로 바꿀지, 키워드 순서만 고칠지는 열린 질문이다. 매 턴 LLM을 한 번 더 부르는
비용이 붙는 선택이다.

**(b) 근거 부족 시 되돌아가는 경로**
RAG 결과의 근거가 약할 때(결과 0건, 유사도 임계 미달, 판정 에이전트가 불충분 판정)
웹 검색으로 보강하거나 질의를 바꿔 다시 찾는다. `loopBuilder`의 `exitCondition`이
이 구조에 대응한다. **반복 상한을 반드시 건다.**

**먼저 프레임워크 없이 되는지 본다.** (a)에 건 것과 같은 조건이다. 같은 모양의 되돌아가기가
이미 저장소에서 돌고 있다 — 웹 검색 결과가 비면 LLM 직접 응답으로 빠지는 분기가
`ChatbotServiceImpl.handleWebSearchPipeline()`에 있다(`ChatbotServiceImpl.java:267-270`).
RAG 경로에 같은 형태를 넣는 것과 `loopBuilder`를 쓰는 것을 나란히 놓고 비교한다.
판단 기준은 **반복이 필요한가**다. 한 번 빠지고 끝나는 fallback이면 평범한 분기로 충분하고,
"질의를 바꿔 다시 찾는다"를 여러 번 돌려야 하면 그때 `loopBuilder`가 값을 한다.

**(c) 워크플로로의 이관**
`switch` 분기를 `conditionalBuilder` 또는 `supervisorBuilder`로 옮긴다. 한 번에 전부
바꾸지 말고 RAG 경로부터 한다.

**이 항목은 동작이 같은 코드를 다른 표현으로 옮기는 일이다.** 사용자에게 가는 값이 그 자체로는
없다. 착수하려면 (b)가 먼저 값을 냈고 그 위에 분기가 더 붙어 `switch`로는 읽기 어려워졌다는
근거가 있어야 한다. 그 근거가 아직 없으면 (a)·(b)만 하고 (c)는 미룬다.

**(d) 효과 측정**
02단계 골든셋으로 기준선과 비교한다. 특히 "근거 없음"(모른다고 답해야 하는) 유형에서
지어내는 비율이 줄었는지 본다.

**먼저 짚을 것 — 지금의 02 평가 잡으로는 이 단계의 변경이 보이지 않는다.**
02의 잡은 `ChatbotServiceImpl`을 올리지 않고 체인을 직접 이어 붙인다. 생성자 인자가
15개라 통째로 올릴 대상이 아니라고 02가 판단했고(`02-eval-observability.md:138-144`),
02a의 `@Import` 최소 집합에도 그 클래스가 없다(`02a-batch-eval-module.md:115-123`).
그런데 이 단계가 바꾸는 것은 전부 그 클래스 안이다 — `switch`(`ChatbotServiceImpl.java:79-102`),
보강 루프, 워크플로 이관. 즉 (a) 의도 분류만 잡에 보이고 (b)·(c)는 지표에 전혀 나타나지
않는다. 평가 잡이 `IntentClassificationService`는 태우기 때문에 (a)만 예외다.

따라서 이 단계를 착수하기 전에 **어느 쪽을 할지 정한다.**
① 평가 잡의 진입점을 오케스트레이터까지 넓힌다 (02의 범위를 늘리는 일이다),
② 이 단계가 바꾸는 경로를 별도 진입점으로 빼서 잡이 그것을 태우게 한다,
③ (b)·(c)의 효과는 골든셋이 아닌 다른 방법으로 판정한다.
**이 결정 없이 진행하면 완료 기준의 "기준선 대비 결과 제시"를 만족시킬 수 없다.**

두 번째로 짚을 것 — 위 "근거 없음 유형의 지어내는 비율"은 **02의 열린 질문 1에서
(c) 쪽을 골랐을 때만 나온다.** 02는 이 유형을 근거 기반성·질문 응답성 두 축의 분모에서
빼고(`02-eval-observability.md:294-296`), 이 유형을 재는 거절 정확성 축은 만들지 여부
자체가 열린 질문이다(`:296-298`, `:592-597`). 검색 쪽을 골랐으면 이 축이 없으므로
판정 근거를 다시 정해야 한다.

### 제외
- `api-agent`의 툴 루프 재작성. 이미 동작하고, 이 단계의 대상이 아니다.
- 일반 사용자에게 에이전트 개방. 권한 체계에 손대는 일이라 별도 판단이 필요하다.
- 사람 개입(`humanInTheLoopBuilder`). 챗봇은 동기 응답이라 맞지 않는다.
- 스트리밍 응답.

## 후보 완료 기준

- [ ] 현재 키워드 분류의 실제 동작이 표로 기록돼 있다(입력 예시 → 실제 분류 결과).
      고치기 전에 무엇을 고치는지가 남아 있어야 한다.
- [ ] RAG 경로에서 근거가 부족할 때 보강 경로가 실제로 동작한다(재현 가능한 질문 예시 포함).
- [ ] 반복 상한이 코드에 있고, 상한에 걸렸을 때의 동작이 정의돼 있다.
- [ ] 한 턴당 LLM 호출 횟수의 상한이 있고, 변경 전후 평균 호출 횟수가 기록돼 있다.
- [ ] 02단계 골든셋 기준으로 기준선 대비 결과가 제시돼 있다. 나빠진 항목이 있으면
      그 사실도 적혀 있다.
- [ ] 응답 지연시간이 변경 전후로 측정돼 있다.
- [ ] `:api-chatbot:test`가 그린이고, 새 분기에 테스트가 있다.

## 진행 순서 초안

1. **현행 동작 기록.** 대표 질문 20~30개를 실제로 넣어 어디로 분류되는지 표로 만든다.
   LLM 호출 없이 분류기만 돌리면 되므로 비용이 없다.
   판정: 의도와 다르게 분류되는 입력이 몇 건인지 세어 기록.
2. **최소 변경으로 분류 고치기.** 워크플로 도입 전에, 키워드 순서나 항목 조정만으로
   개선되는 부분이 있는지 본다. 여기서 해결되면 (a)의 범위가 줄어든다.
3. **`langchain4j-agentic` 시험.** RAG 경로 하나만 에이전트로 옮겨 동작을 확인한다.
   experimental 모듈이므로 실제로 쓰이는 API가 문서대로인지 여기서 확인한다.
4. **보강 루프 추가.** `loopBuilder` + `exitCondition`. 상한과 종료 조건을 먼저 정하고 짠다.
5. **측정.** 02단계 잡 실행. 품질뿐 아니라 **호출 횟수와 지연시간**도 함께 본다.

## 판정 기준

- **품질이 올라도 한 턴 LLM 호출이 두 배가 되면 그건 개선이 아니다.** 품질·비용·지연을
  같이 제시하고, 셋을 놓고 판단한다.
- 되돌아가는 경로가 "동작한다"는 것은 그 경로를 타는 질문을 실제로 만들어 확인했다는
  뜻이다. 코드에 분기가 있다는 것으로 대신하지 않는다.
- experimental 모듈이라 API가 문서와 다를 수 있다. 다르면 우회하지 말고 사실을 기록하고
  사람에게 알린다.

## 열린 질문 (P3 후보)

1. **의도 분류를 LLM으로 바꿀 것인가.** 정확도는 오르지만 매 턴 호출이 하나 늘고
   지연이 붙는다. 키워드로 명백한 것(예: `@agent` 접두사)은 그대로 두고 애매한 것만
   LLM에 넘기는 절충이 가능하다.
2. **`conditionalBuilder`인가 `supervisorBuilder`인가.** conditional은 조건을 코드로 쓰므로
   예측 가능하고 싸다. supervisor는 계획 모델이 판단하므로 유연하지만 매 턴 계획 호출이
   붙고 동작이 덜 예측 가능하다. **conditional부터 시작하고, 부족하면 supervisor로 올리는
   순서를 권한다.**
3. **워크플로를 어느 모듈에 두나.** `api-chatbot`에 두면 지금 구조와 이어지고,
   `api-agent`에 두면 에이전트 관련 코드가 한곳에 모인다. 후자면 서비스 간 호출이
   늘어나고 `X-Internal-Api-Key` 경유가 필요하다.
4. **experimental 모듈을 프로덕션 경로에 넣을 것인가.** 넣는다면 버전을 고정하고
   업그레이드 시 회귀 확인을 의무화하는 등의 장치가 필요하다. 01단계의 열린 질문 1과 연결된다.
5. **기존 `handleWebSearchPipeline`의 fallback을 어떻게 흡수하나.** 지금은 별도 분기에
   박혀 있다. 워크플로로 옮기면 중복이 되므로 정리 방향을 정해야 한다.

## 제약·리스크

- **`langchain4j-agentic`은 experimental이다.** 공식 문서가 "모듈 전체가 experimental이고
  이후 릴리스에서 바뀔 수 있다"고 명시한다. 프로덕션 경로에 넣는 판단은 사람이 한다.
- 반복 루프는 비용을 곱한다. 상한이 없으면 한 질문에 LLM 호출이 수십 번 날 수 있다.
  `api-agent`가 `maxSequentialToolsInvocations(30)`과 `AgentLoopDetectedException`을 둔 것과
  같은 종류의 방어가 필요하다.
- 응답 지연이 늘어난다. 챗봇은 사용자가 기다리는 동기 응답이라 체감이 크다.
- 02단계가 선행이다. 이 단계는 "좋아졌다"를 숫자로 말해야 하는데, 근거를 만드는 게 02다.
  **다만 지금의 02로는 이 단계의 효과를 잴 수 없다** — 아래 (d) 항목을 보라.
- **01단계는 선행이 아니다.** `langchain4j-agentic`은 `1.10.0-beta18`이 배포돼 있다
  (2026-08-15 `repo1.maven.org` 확인). 이 저장소가 이미 쓰는 `langchain4j:1.10.0` +
  `langchain4j-mongodb-atlas:1.10.0-beta18` 짝과 같은 형태다
  (`api/chatbot/build.gradle:13,16,22`). 지금 라인에서 시험해 볼 수 있다.
  버전을 올릴지는 이 단계와 별개로 판단한다 — 다만 1.10 라인의 beta와 최신 라인의 API가
  같다는 보장은 없으므로, 1.10에서 시험한 코드를 그대로 옮길 수 있는지는 확인이 필요하다.

## 참고 (2026-08-12 확인)

- `https://docs.langchain4j.dev/tutorials/agents` — `AgenticServices` 빌더 목록,
  `AgenticScope`, `ErrorRecoveryResult`, experimental 명시
- `https://repo1.maven.org/maven2/dev/langchain4j/langchain4j-agentic/maven-metadata.xml`
  — latest `1.19.0-beta29` (2026-08-15 확인). 1.10 라인에도 `1.10.0-beta18`이 있다 —
  제약·리스크의 "01단계는 선행이 아니다"를 보라
- `https://repo1.maven.org/maven2/org/bsc/langgraph4j/langgraph4j-core/maven-metadata.xml`
  — latest·release 모두 `1.9.0-beta2`, 정식 릴리스는 `1.8.24`까지 (2026-08-15 확인).
  아티팩트 목록은 `https://repo1.maven.org/maven2/org/bsc/langgraph4j/`에서 본다 —
  디렉터리 목록이라 버전은 나오지 않는다

## impl input 생성 힌트

- `pipeline/inputs/tasks/task-06-intent-routing-fix.md` (진행 순서 1~2단계만)
- `pipeline/inputs/tasks/task-07-agentic-rag-loop.md` (3~5단계)

**앞의 것을 먼저 독립 작업으로 돌릴 것을 권한다.** 분류기 문제는 프레임워크 도입 없이
고쳐지는 부분이 있고, 작고, 즉시 값이 난다. 뒤의 것은 experimental 모듈 도입이라
성격이 다르다. 한 task로 묶으면 앞의 확실한 개선이 뒤의 불확실성에 발목 잡힌다.
