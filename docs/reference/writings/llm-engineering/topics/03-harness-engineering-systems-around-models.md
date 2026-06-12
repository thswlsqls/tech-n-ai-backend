# 03. 하네스 엔지니어링 — 최고 모델도 혼자서는 장기 작업에 실패한다

## 한줄 요약(Hook)

> "even a frontier coding model like Opus 4.5 running on the Claude Agent SDK in a loop across multiple context windows will fall short." Anthropic이 공식 블로그에 적은 문장이다. 모델이 부족해서가 아니다. 장기 작업의 성패는 모델 바깥 시스템 — 하네스 — 의 설계가 가른다.

## 핵심 질문

- 하네스 없이 돌린 프런티어 모델은 장기 작업에서 어떻게 실패하나?
- Anthropic 공식 글 2편이 제시한 하네스 설계 요소는 무엇인가?
- 모델이 좋아지면 하네스는 어떻게 되나 — 스캐폴딩은 영원한가?
- "harness"라는 개념과 "harness engineering"이라는 이름은 출처가 다르다. 어떻게 다른가?

## 다루는 관점

- ✅ 발전 배경(Why) — 컨텍스트 윈도우 하나를 넘는 작업이 낳은 문제
- ✅ 핵심 기법 — 2단 에이전트 구조, 점진 진행, 생성·평가 분리
- ✅ 용어의 출처 — 개념(공식)과 명명(비공식)의 분리

## 근거

- Anthropic, *Effective harnesses for long-running agents* (2025-11-26) — 프런티어 모델도 하네스 없이는 부족하다는 인용문. 실패 원인: 한 번에 너무 많은 작업 시도, 조기 완료 판정. 설계 요소: 환경을 준비하는 initializer agent + 점진적으로 진행하는 coding agent의 2단 구조, JSON 형식 기능 체크리스트, 한 번에 한 기능, git log·진행 파일로 상황 파악, 브라우저 자동화 검증
- Anthropic, *Harness design for long-running application development* (2026-03-24) — 생성 에이전트와 평가 에이전트를 분리해 자기평가 편향을 줄이는 패턴. 작업을 작은 단위로 쪼개고 구조화된 아티팩트로 컨텍스트 전달. 모델이 좋아지면 기존 스캐폴딩이 불필요해질 수 있으니 정기적으로 걷어내라는 교훈
- [미검증 · 근거 아님] Addy Osmani, *Agent Harness Engineering* (개인 블로그, 2026-04-19) — "Agent = Model + Harness. If you're not the model, you're the harness." 방법론에 이름을 붙인 글. 용어 유래 참고용

## 타깃 독자 & 난이도

- Claude Code 같은 AI 코딩 도구를 일상적으로 쓰지만, 쏟아지는 엔지니어링 용어의 계보를 정리하지 못한 백엔드 개발자
- ★★★☆☆ (사전지식: 01·02편. 에이전트가 여러 턴을 돈다는 감각)

## 예상 분량

- 김 (완성 글 기준 9,000자 안팎)

## 글 아웃라인

1. **들어가며 — 02가 열어 둔 질문**
   - compaction을 실행하고 메모를 저장하는 코드는 모델 밖에 있다. 그 시스템의 이름이 하네스다
2. **실패의 양상 — 모델은 과욕을 부리고 일찍 끝낸다**
   - 공식 글이 짚은 실패 원인 두 가지(한 번에 너무 많이, 조기 완료 판정)를 코딩 작업 사례로 풀이
3. **하네스의 뼈대 — 준비하는 에이전트와 진행하는 에이전트**
   - initializer/coding agent 2단 구조. JSON 기능 체크리스트와 "한 번에 한 기능" 규칙이 왜 실패 양상의 해독제인지
4. **검증을 분리한다 — 자기 채점의 편향**
   - 생성과 평가를 다른 에이전트로 나누는 패턴(2026-03 글). 브라우저 자동화 같은 외부 검증 수단
5. **스캐폴딩은 영원하지 않다**
   - 모델이 좋아지면 걷어내야 한다는 공식 교훈. 하네스가 "쌓는 것"이 아니라 "조정하는 것"이라는 관점
6. **개념은 공식, 이름은 비공식**
   - Anthropic 공식 글은 harness라는 개념과 단어를 쓰지만 "harness engineering"이라는 방법론 이름은 쓰지 않는다. 이름 붙이기는 외부(개인 블로그)에서 왔다는 사실을 미검증 표시와 함께 전달
7. **마무리 — 하네스를 돌리는 건 여전히 사람이다**
   - 하네스가 아무리 좋아도 그것을 언제 시작하고 멈추고 다시 시킬지는 사람이 정한다. 그 자리마저 시스템으로 바꾸려는 움직임을 예고한다 (04 예고)

## 참고할 1차 출처 (공식 문서)

- Anthropic — Effective harnesses for long-running agents — https://www.anthropic.com/engineering/effective-harnesses-for-long-running-agents
- Anthropic — Harness design for long-running application development — https://www.anthropic.com/engineering/harness-design-long-running-apps

### 용어 유래 참고 자료 (근거 아님 · 미검증)

- Addy Osmani — Agent Harness Engineering (개인 블로그) — https://addyosmani.com/blog/agent-harness-engineering/

## 시리즈 인용 관계

02가 열어 둔 "토큰을 관리하는 시스템은 누가 만드나"에 답하는 편이다. 02의 전략 3종(compaction 등)은 정의를 반복하지 않고 "02에서 본 전략들"로 줄인다. 7번 절에서 "하네스를 돌리는 사람"이라는 질문을 열어 두고, 답(루프)은 **04 — 루프 엔지니어링**으로 넘긴다. 6번 절의 "개념은 공식, 이름은 비공식" 관찰도 04가 같은 구도로 재인용한다.

## 작성 메모

- 인용문("even a frontier coding model ... will fall short")은 영어 원문 그대로 싣고 한국어로 푼다. 이 문장이 글 전체의 축이다.
- 공식 글 2편의 게시일(2025-11-26, 2026-03-24)을 본문에 명시해 발전사의 시간축을 세운다.
- Osmani의 "Agent = Model + Harness" 인용은 **출처가 개인 블로그임을 본문에 밝히고**, 기술적 사실이 아니라 명명의 기록으로만 쓴다.
- 하네스 구성 요소를 전부 나열하려 들지 않는다. 실패 양상 → 해독제 구조로 묶어서 서술해야 발전사 글의 결이 유지된다.
- Claude Code 사용 경험(체크리스트, 서브에이전트, 훅)과 연결할 때는 "공식 글의 패턴이 도구에서 이렇게 보인다" 수준까지만. 도구 매뉴얼이 되지 않게 한다.
