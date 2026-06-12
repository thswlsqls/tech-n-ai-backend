# 02. 컨텍스트 엔지니어링 — 설계 대상이 문장에서 토큰 구성으로 넓어진 이유

## 한줄 요약(Hook)

> 2025년 9월 Anthropic은 컨텍스트 엔지니어링을 "프롬프트 엔지니어링의 자연스러운 발전"이라고 공식 문서에 적었다. 바뀐 것은 기법이 아니라 단위다. 한 턴짜리 문장을 다듬던 일이, 여러 턴을 도는 에이전트의 컨텍스트 윈도우 전체를 관리하는 일이 됐다.

## 핵심 질문

- 멀티 턴 에이전트의 등장이 왜 프롬프트 최적화만으로는 부족하게 만들었나?
- Anthropic 공식 정의에서 컨텍스트 엔지니어링의 관리 대상은 정확히 무엇인가?
- compaction·structured note-taking·sub-agent는 각각 어떤 문제를 푸나?
- 이 용어는 어떤 경로로 공식 어휘가 됐나? (2025년 6월 X 발언 → 9월 공식 블로그)

## 다루는 관점

- ✅ 발전 배경(Why) — 에이전트의 등장과 "컨텍스트는 유한 자원"이라는 문제
- ✅ 핵심 기법 — 공식 글이 제시한 장기 작업 전략 3종
- ✅ 용어의 출처 — 트윗에서 시작해 공식 문서가 채택하기까지의 경로

## 근거

- Anthropic, *Building effective agents* (2024-12-19) — agent를 "systems where LLMs dynamically direct their own processes and tool usage"로 정의. 고정 경로의 workflow와 자율 결정하는 agent를 구분. 멀티 턴 시스템이 기본 단위가 되는 전환점
- Anthropic, *Effective context engineering for AI agents* (2025-09-29) — 정의 원문: "the set of strategies for curating and maintaining the optimal set of tokens (information) during LLM inference". 프롬프트 엔지니어링의 자연스러운 발전이라는 명시. 전략 3종: compaction(요약 후 새 윈도우로 재시작), structured note-taking(컨텍스트 밖 영속 메모), sub-agent 구조(집중 작업 후 요약 반환)
- [미검증 · 근거 아님] Tobi Lütke·Andrej Karpathy의 X 게시물(2025-06) — "context engineering" 표현 선호 발언. 용어 대중화 시점의 참고용. 발언의 존재는 원 게시물로 확인 가능하나 공식 문서가 아니므로 기술적 사실의 근거로 쓰지 않는다

## 타깃 독자 & 난이도

- Claude Code 같은 AI 코딩 도구를 일상적으로 쓰지만, 쏟아지는 엔지니어링 용어의 계보를 정리하지 못한 백엔드 개발자
- ★★☆☆☆ (사전지식: 01편의 프롬프트 엔지니어링 전제. 컨텍스트 윈도우가 뭔지 아는 정도)

## 예상 분량

- 보통 (완성 글 기준 7,000자 안팎)

## 글 아웃라인

1. **들어가며 — 01의 전제가 깨지는 순간**
   - "한 턴, 한 과제"라는 프롬프트 엔지니어링의 전제(01)를 한 줄로 요약하고, 여러 턴을 도는 에이전트를 등장시킨다
2. **에이전트의 공식 정의 — 2024년 12월**
   - workflow(미리 정한 경로)와 agent(스스로 결정)의 구분. 이 구분이 왜 컨텍스트 문제를 낳는지
3. **컨텍스트는 유한 자원이다**
   - 턴이 쌓일수록 시스템 지시·도구 결과·대화 이력이 한 윈도우를 두고 경쟁한다는 문제 설정
4. **공식 정의 — 토큰 구성을 큐레이션한다**
   - 2025년 9월 글의 정의 원문 인용과 풀이. "프롬프트 엔지니어링의 자연스러운 발전"이라는 문장의 의미
5. **전략 3종 — compaction, note-taking, sub-agent**
   - 각 전략이 푸는 문제를 하나씩. 도구 사용자 입장에서 체감되는 지점(자동 요약, 메모 파일, 서브에이전트 위임)과 연결
6. **용어의 생애주기 1막 — 트윗에서 공식 문서로**
   - 2025년 6월 X 발언(미검증임을 본문에 밝힘) → 3개월 뒤 Anthropic 공식 글. 용어가 공식 어휘가 되는 경로의 첫 사례
7. **마무리 — 토큰을 관리하는 시스템은 누가 만드나**
   - compaction을 실행하고 메모를 저장하는 건 모델 밖 코드다. 그 시스템 자체의 설계가 다음 단계라는 질문을 열어 둔다 (03 예고)

## 참고할 1차 출처 (공식 문서)

- Anthropic — Building effective agents — https://www.anthropic.com/engineering/building-effective-agents
- Anthropic — Effective context engineering for AI agents — https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents

### 용어 유래 참고 자료 (근거 아님 · 미검증)

- Andrej Karpathy X 게시물 (2025-06) — https://x.com/karpathy/status/1937902205765607626

## 시리즈 인용 관계

01이 열어 둔 "한 턴 전제가 깨지면?"에 답하는 편이다. 7번 절에서 "컨텍스트를 관리하는 코드는 모델 밖에 있다"는 질문을 열어 두고, 답(하네스)은 **03 — 하네스 엔지니어링**으로 넘긴다. 6번 절의 "용어 생애주기" 관찰은 **04 — 루프 엔지니어링**이 대조 사례(아직 공식화되지 않은 용어)로 재인용한다.

## 작성 메모

- 용어 유래 절(6번)에서 X 게시물을 인용할 때는 **본문에 "공식 출처가 아니며 발언의 존재만 확인했다"고 밝힌다.** Cognition의 Walden Yan이 더 먼저 썼다는 설은 2차 자료라 확인하지 못했으므로 적지 않거나 "확인 못 함"으로만 둔다.
- 정의 원문은 영어 그대로 인용하고 바로 아래에 한국어로 푼다. 번역만 싣지 않는다.
- 전략 3종을 매뉴얼처럼 길게 쓰지 않는다. 이 글의 각은 "왜 이 단계가 왔나"다. 각 전략은 "어떤 문제를 푸는가" 한 단락씩이면 충분하다.
- "context rot" 같은 부속 개념은 공식 글에 있는 범위에서만 언급한다.
