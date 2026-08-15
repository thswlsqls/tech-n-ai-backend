# 단계 01 — LangChain4j 1.10.0 → 1.18.x 버전 업

## 한 줄 요약

이후 단계(02·03·04)가 쓰려는 모듈이 1.10 라인에 아예 존재하지 않는다. 버전 업이 먼저다.

## 왜 이 단계인가

지금 저장소는 LangChain4j **1.10.0**을 쓴다. Maven Central 기준 최신은 **1.18.1**이고,
beta 라인은 **1.18.1-beta28**이다 (2026-08-11 릴리스, 2026-08-12 확인).

여덟 마이너 버전이 밀렸다는 것 자체보다, 다음이 문제다.

- `langchain4j-agentic`(04단계)의 최소 배포 버전이 1.10보다 위다. 1.10.0으로는 못 쓴다.
- `langchain4j-community-neo4j-retriever`, `langchain4j-community-llm-graph-transformer`(03단계)도 마찬가지다.
- `langchain4j-observation`, `langchain4j-guardrails`(02·05단계)도 최신 라인에만 있다.

즉 이 단계는 그 자체로 기능을 늘리지 않는다. 뒤의 세 단계를 가능하게 하는 선행 작업이다.
독립적으로 값을 내는 부분은 그동안 쌓인 버그 수정과 모델 제공자 업데이트뿐이다.

## 현재 코드 상태 — 확인된 사실

버전이 박혀 있는 곳이 네 모듈, 여섯 줄이다. 전부 리터럴로 직접 적혀 있고 BOM을 쓰지 않는다.

| 파일 | 줄 | 아티팩트 | 현재 버전 |
|------|----|---------|----------|
| `common/conversation/build.gradle` | 16 | `langchain4j` (`api` 스코프) | 1.10.0 |
| `api/chatbot/build.gradle` | 13 | `langchain4j` | 1.10.0 |
| `api/chatbot/build.gradle` | 16 | `langchain4j-mongodb-atlas` | 1.10.0-beta18 |
| `api/chatbot/build.gradle` | 19 | `langchain4j-open-ai` | 1.10.0 |
| `api/chatbot/build.gradle` | 22 | `langchain4j-cohere` | 1.10.0-beta18 |
| `api/emerging-tech/build.gradle` | 12, 13 | `langchain4j`, `langchain4j-open-ai` | 1.10.0 |
| `api/agent/build.gradle` | 21, 22 | `langchain4j`, `langchain4j-open-ai` | 1.10.0 |

`common/conversation`은 `api` 스코프로 노출하므로, 이 모듈을 쓰는 `api-chatbot`·`api-agent`에
버전이 전파된다. 버전을 어긋나게 올리면 클래스패스 충돌이 난다.

LangChain4j API를 실제로 쓰는 지점:

- `api/chatbot/.../config/LangChain4jConfig.java` — `ChatModel` 등 빈을 직접 만든다
- `api/chatbot/.../memory/ConversationChatMemoryProvider.java` — `MessageWindowChatMemory`
- `api/agent/.../agent/EmergingTechAgentImpl.java:68-84` — `AiServices.builder()`, 툴 에러 핸들러 3종,
  `maxSequentialToolsInvocations`
- `api/agent/.../agent/AgentAssistant.java` — `ChatMemoryAccess`, `@MemoryId`, `@UserMessage`
- `api/chatbot/.../converter/` — 제공자별 메시지 포맷 변환기(직접 구현)
- `common/conversation/.../memory/MongoDbChatMemoryStore.java` — `ChatMemoryStore` 구현

## 범위

### 포함
- 위 네 모듈의 LangChain4j 의존성을 최신 안정 라인으로 올린다.
- 버전 관리 방식을 BOM(`dev.langchain4j:langchain4j-bom`)으로 바꿀지 결정하고 적용한다.
  현재처럼 여섯 곳에 리터럴을 흩어두면 이후 단계마다 같은 실수를 반복한다.
- 컴파일 에러·동작 변경을 잡고, 영향 모듈 테스트를 전부 통과시킨다.
- beta 아티팩트(`-mongodb-atlas`, `-cohere`)의 버전 라인을 안정 버전과 짝이 맞게 올린다.

### 제외
- 새 기능 추가. 이 단계에서는 기존 동작을 그대로 유지하는 것이 목표다.
- `langchain4j-spring-boot4-starter` 도입 (열린 질문으로 남긴다).
- 프롬프트 내용 변경, 모델 교체.

## 후보 완료 기준

- [ ] `./gradlew clean build`가 통과한다.
- [ ] `:api-chatbot:test`, `:api-agent:test`, `:api-emerging-tech:test`,
      `:common-conversation:test`가 각각 그린이다.
- [ ] LangChain4j 버전 리터럴이 한 곳에서만 관리된다(BOM 채택 시). 채택하지 않기로 했다면
      그 판단 근거가 PR 본문에 적혀 있다.
- [ ] `api-chatbot`의 RAG 경로와 `api-agent`의 툴 실행 경로가 버전 업 전후로 같은 결과를
      낸다는 것을 확인했다(수동 확인이라도 기록을 남긴다).
- [ ] beta 아티팩트의 버전 라인이 안정 버전과 대응한다(예: `1.18.1` ↔ `1.18.1-beta28`).

## 진행 순서 초안

1. **변경점 조사**. LangChain4j 공식 릴리스 노트에서 1.11 ~ 1.18 사이의 breaking change를
   모은다. `dev.langchain4j:langchain4j-openrewrite-recipes` 아티팩트가 배포돼 있는데,
   이름상 마이그레이션 레시피로 보인다 — 실제 제공 레시피 목록을 먼저 확인하고, 쓸모가
   있으면 쓴다. 없으면 수동으로 간다.
   판정: 우리가 쓰는 API 목록(위 "실제 쓰는 지점")과 변경점 목록을 대조해, 영향받는 항목이
   전부 열거되기 전에는 다음 단계로 넘어가지 않는다.
2. **BOM 도입 여부 결정**. 루트 `build.gradle`이나 별도 `.gradle` 파일에 BOM을 두는 방식이
   `jpa.gradle` 관행과 맞는지 본다.
3. **버전 교체 → 컴파일**. 모듈 하나씩이 아니라 한 번에 올린다. `common-conversation`이
   `api` 스코프로 전파하므로 부분 업그레이드는 오히려 위험하다.
4. **테스트 통과**. 영향 모듈 개별 실행.
5. **동작 확인**. 챗봇 RAG 질의 한 건, 에이전트 툴 실행 한 건을 로컬에서 실제로 돌린다.
   LLM 실호출 비용이 드는 부분이라 각 1회로 제한한다.

## 판정 기준

- 컴파일이 되는 것과 동작이 같은 것은 다르다. 특히 `AiServices` 툴 루프와 `ChatMemoryStore`
  구현은 시그니처가 그대로여도 내부 동작이 바뀔 수 있다. 실제 호출로 확인한다.
- 테스트가 LLM을 mock하고 있다면, mock이 통과한다고 실동작을 보증하지 않는다. 어느 부분이
  mock으로만 확인됐는지 PR 본문에 적는다.

## 열린 질문 (P3 후보)

1. **안정 버전만 쓸 것인가, beta를 허용할 것인가.** 지금도 `-beta18` 두 개를 쓰고 있으니
   이미 beta를 쓰는 셈이다. 다만 03·04단계의 핵심 모듈은 전부 beta라, 정책을 명시적으로
   정해두는 편이 낫다. 공식 문서가 `langchain4j-agentic`을 experimental로 명시한다.
2. **`langchain4j-spring-boot4-starter`(1.18.1-beta28)를 도입할 것인가.** 이 저장소는
   Spring Boot 4를 쓰고 starter도 배포돼 있다. 다만 지금은 `LangChain4jConfig`에서
   빈을 직접 만들고 있어서, starter로 바꾸면 설정 위치가 이동한다. 얻는 것(설정 표준화)과
   잃는 것(명시적 제어)을 비교해 결정할 문제다.
3. **어느 버전까지 올릴 것인가.** 입력 파일을 만들면서 릴리스 목록을 확인한 결과,
   LangChain4j는 여러 라인을 동시에 유지보수한다(1.5.x, 1.11.x, 1.18.x가 모두 살아 있다).
   선택지가 셋이다.
   - **1.18.1** (2026-07-29) — 최신 마이너. 02~04단계가 쓰려는 모듈이 전부 이 라인에 있다.
   - **1.13.x** (1.13.0은 2026-04-09) — **Spring Boot 4 지원이 들어온 지점.**
     이 저장소는 Boot 4를 쓰므로 starter가 의미를 갖기 시작하는 최소 버전이다.
   - **1.11.11** (2026-08-11) — 1.11 유지보수 라인의 최신 패치. 1.18.1보다 나중에 나왔다.
     가장 적게 움직이지만 이후 계획의 모듈들을 못 쓴다.

   조사 중 확인된 구체적 변경점 세 건이 이 저장소가 쓰는 부분과 겹친다 —
   1.11.0의 ChatMemory 시스템 메시지 처리 변경(#4304), 1.13.0의 Spring Boot 4 지원,
   1.18.0의 EmbeddingModel request/response API 개편(#5735).

## 제약·리스크

- LangChain4j는 1.0 이후에도 통합(integration) 모듈을 beta 라인에 두고 자주 바꾼다.
  `-mongodb-atlas`, `-cohere`가 여기 해당한다. 벡터 검색과 재순위가 이 모듈에 걸려 있어
  회귀 위험이 가장 큰 지점이다.
- Spring Boot 4 / Jackson 3 조합에서 나는 문제는
  `~/.claude/projects/-Users-m1-workspace-tech-n-ai/memory/spring-boot-4-migration.md`에
  정리돼 있다. import나 설정을 손대기 전에 먼저 본다.
- 이 단계는 네 모듈을 동시에 건드리므로 worktree 격리가 특히 중요하다.

## 참고 (2026-08-12 확인)

- `https://repo1.maven.org/maven2/dev/langchain4j/langchain4j/maven-metadata.xml` — `latest` = 1.18.1
- `https://repo1.maven.org/maven2/dev/langchain4j/` — 배포 중인 아티팩트 전체 목록
- `https://docs.langchain4j.dev/tutorials/agents` — agentic 모듈이 experimental임을 명시
- `https://api.github.com/repos/langchain4j/langchain4j/releases` — 릴리스 라인과 변경점
- `https://repo1.maven.org/maven2/dev/langchain4j/langchain4j-bom/1.10.0/langchain4j-bom-1.10.0.pom` —
  BOM이 `langchain4j.stable.version`(1.10.0)과 `langchain4j.beta.version`(1.10.0-beta18)
  두 프로퍼티로 아티팩트를 관리하며, 이 저장소가 쓰는 네 아티팩트를 모두 포함한다

## 만들어진 impl input (2026-08-12)

이 문서에서 **2쌍**을 도출했다. 분할 근거는 위 BOM 확인 결과다 — BOM 1.10.0을 넣으면
지금과 똑같은 버전으로 해석되므로, "구조 변경"과 "버전 변경"을 서로 다른 diff로
떼어낼 수 있다.

| 순서 | 입력 파일 | 성격 |
|------|----------|------|
| 1 | `task-20260812125555-langchain4j-bom` / `prompt-...` | 버전 동결 상태로 BOM 도입. 의존성 트리 diff가 비어야 통과하는 검증 가능한 리팩토링 |
| 2 | `task-20260812125925-langchain4j-upgrade` / `prompt-...` | BOM 한 줄을 올리고 깨진 곳 수정 + 마이그레이션 노트 |

실행: `./impl-session.sh "task=20260812125555"` → 완료 후 `"task=20260812125925"`.

모듈별 분할은 하지 않았다. `common-conversation`이 `api` 스코프로 버전을 전파하므로
부분 업그레이드가 불가능하다. 조사 단계를 별도 쌍으로 빼는 것도 하지 않았다 —
조사 결과(마이그레이션 노트)는 코드 수정의 근거라 같은 PR에 있는 편이 낫다.
대신 두 번째 prompt에서 조사(1~2단계)를 끝내기 전에는 버전을 못 건드리게 막았다.
