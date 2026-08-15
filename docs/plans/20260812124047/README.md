# LLM 엔지니어링 확장 계획 (2026-08-12)

이 폴더는 "RAG 이후 무엇을 확장할 것인가"를 단계별로 정리한 참조 문서다.
각 문서는 그 자체가 실행 지시가 아니라, **impl 파이프라인의 입력 파일
(`pipeline/inputs/tasks/task-NN-*.md` + `pipeline/inputs/prompts/prompt-NN-*.md`)을
만들 때 참조하는 근거 자료**다.

## 문서 목록

| 문서 | 단계 | 한 줄 요약 |
|------|------|-----------|
| [01-langchain4j-upgrade.md](01-langchain4j-upgrade.md) | 선행? | LangChain4j 1.10.0 → 1.18.x. **03·04가 쓸 모듈은 1.10 라인에도 있어 이 단계를 기다리지 않아도 된다** (2026-08-15 확인 — 각 문서의 제약·리스크 참고) |
| [02-eval-observability.md](02-eval-observability.md) | 1순위 | 검색·답변 품질을 숫자로 재는 배치와 관측 지표 |
| [03-graphrag.md](03-graphrag.md) | 2순위 | 신기술 데이터를 지식 그래프로 만들어 다중 홉 질문에 답한다 |
| [04-agentic-orchestration.md](04-agentic-orchestration.md) | 3순위 | 키워드 `switch` 라우팅을 되돌아갈 수 있는 워크플로로 교체 |
| [05-backlog.md](05-backlog.md) | 미정 | guardrails·MCP·skills·장기 기억·부채 정리. 각각 작고 독립적 |

## 배경 — 왜 이 계획이 나왔나

"RAG는 이제 기업에서 안 다루는 주제 아니냐"는 질문에서 출발했다. 절반만 맞다.

끝난 것은 **naive RAG**다. 문서를 자르고, 임베딩하고, top-k를 뽑아 프롬프트에 붙이는
파이프라인 하나만으로 제품이 되던 시기가 지났다는 뜻이다. 검색해서 근거를 붙인다는
행위 자체는 없어지지 않았고, 이름이 context engineering·agentic retrieval로 바뀌었다.

그리고 이 저장소의 `api-chatbot`은 이미 naive RAG가 아니다. 의도로 경로를 나누고,
질의를 다시 쓰고, 메타데이터 필터와 recency 가중을 걸고, 재순위까지 붙어 있다.
문제는 다른 데 있다 — **이 파이프라인이 좋아졌는지 나빠졌는지 잴 방법이 없다.**
그래서 1순위가 GraphRAG가 아니라 평가 하니스다.

## "그래프 엔지니어링"이라는 키워드에 대해

같은 단어가 서로 다른 두 가지를 가리킨다. 대응하는 기술이 완전히 다르므로 구분해서 쓴다.

| | 데이터의 그래프 (GraphRAG) | 제어 흐름의 그래프 |
|---|---|---|
| 무엇을 그래프로 | 문서에서 뽑은 엔티티와 관계 | 에이전트의 상태 전이 |
| 푸는 문제 | 벡터 검색이 못 푸는 다중 홉 질문 | 분기·반복·중단/재개가 있는 긴 작업 |
| 저장소 | 그래프 DB (Neo4j 등) | 체크포인트 저장소 |
| 해당 문서 | [03-graphrag.md](03-graphrag.md) | [04-agentic-orchestration.md](04-agentic-orchestration.md) |

이 저장소가 다루는 데이터(회사–모델–기술–릴리스–시점)는 원래 관계형이라,
**데이터 쪽 그래프가 제어 흐름 쪽 그래프보다 먼저 값을 낸다**고 판단해 순서를 그렇게 잡았다.

### Java 진영의 LangGraph 대응물

두 갈래가 있고, 둘 다 실재한다. (Maven Central 조회, 2026-08-12 확인)

**`langchain4j-agentic`** — `dev.langchain4j` 그룹의 공식 모듈. 최신 `1.19.0-beta29`이고
**1.10 라인에도 `1.10.0-beta18`이 있다**(2026-08-15 확인).
`langchain4j-agentic-patterns`, `-mcp`, `-a2a`가 함께 있다. 공식 문서가 모듈 전체를
experimental로 명시한다. 그래프를 직접 그리는 방식이 아니라 순차·병렬·조건·루프·supervisor를
조립하는 형태다.

**`langgraph4j`** — 그룹 `org.bsc.langgraph4j`, 최신 `1.9.0-beta2`이고 정식 릴리스는
`1.8.24`까지 있다(2026-08-15 확인). LangChain 공식 조직이 아닌
개인/커뮤니티 네임스페이스에서 배포된다. 하위 아티팩트에 `langgraph4j-langchain4j`(어댑터),
`langgraph4j-postgres-saver`(체크포인트 영속화), `langgraph4j-studio`(그래프 시각화),
`langgraph4j-adaptive-rag`(예제)가 있다.

**선택**: 이미 LangChain4j 위에 있으므로 `langchain4j-agentic`을 기본으로 본다.
이질적인 프레임워크를 하나 더 얹는 비용이 얻는 것보다 크다고 봤다.
`langgraph4j`는 "실행을 중단했다가 며칠 뒤 재개", "그래프 실행을 화면으로 본다" 같은
요구가 실제로 생겼을 때 다시 검토한다. 판단 근거는 [04](04-agentic-orchestration.md)에 있다.

## 실행 순서

```
01 버전 업  →  02 평가 하니스  →  03 GraphRAG  →  04 agentic 오케스트레이션
                     ↑                    ↓                 ↓
                     └──── 02가 만든 지표로 03·04의 효과를 판정한다 ────┘
```

**01을 나머지 전부의 선행으로 둘지는 아직 정해지지 않았다.** 위 그림은 그렇게 그렸지만,
03·04가 쓸 모듈(`langchain4j-agentic`·`langchain4j-community-neo4j-retriever`·
`langchain4j-community-llm-graph-transformer`)과 05-D의 `langchain4j-guardrails`가 모두
1.10 라인에 `1.10.0-beta18`로 있어, 지금 라인에서 시험해 볼 수 있다는 것이 확인됐다
(2026-08-15). 02가 01을 필요로 하는 것도 `langchain4j-observation`을 쓰기로 할 때뿐이다.
**버전을 언제 올릴지는 이 사실을 놓고 사람이 정한다.** 02는 03·04의 성공/실패를 판정하는 근거를 만들기
때문에 먼저 와야 한다. 03과 04는 서로 독립이라 순서를 바꿔도 되지만, 03을 먼저 하면
04에서 다룰 "검색 결과가 나쁠 때 되돌아가기"의 대상이 하나 더 생긴다.

05는 언제든 끼워 넣을 수 있는 작은 항목 모음이다.

## impl 파이프라인 입력 파일 만드는 법

각 단계 문서는 아래 대응으로 읽는다. 문서를 그대로 복사하지 말고, 해당 절을 근거로
task/prompt를 새로 쓴다 — 파이프라인 입력은 짧고 판정 가능해야 한다.

| 문서의 절 | 들어갈 곳 |
|-----------|----------|
| 왜 이 단계인가 / 현재 코드 상태 | `task-NN` 의 **배경** |
| 범위 (포함·제외) | `task-NN` 의 **요구사항**, **범위 제외** |
| 후보 완료 기준 | `task-NN` 의 **완료 기준** (체크박스) |
| 제약·리스크 | `task-NN` 의 **제약** |
| 진행 순서 초안 | `prompt-NN` 의 **단계** |
| 판정 기준 | `prompt-NN` 의 **성공 기준**, **주의** |
| 열린 질문 | `prompt-NN` 에 "임의로 정하지 말고 P3 질문으로 올린다"로 명시 |

기존 입력 파일 한 쌍(`task-01-agent-visualization.md` / `prompt-01-agent-visualization.md`)이
형식의 기준이다. 새 파일 번호는 `02`부터 이어 붙인다.

파일명 제안은 각 문서 마지막 절에 적어두었다.

## 사실 확인 기준

이 폴더의 버전·아티팩트 정보는 2026-08-12에 `repo1.maven.org`의 `maven-metadata.xml`을
직접 조회해 확인했다. API 이름과 동작 서술은 `docs.langchain4j.dev` 공식 문서에서 확인했다.
코드 상태 서술에는 파일 경로와 줄 번호를 붙였다 — 구현 전에 실제 파일에서 다시 확인할 것.
