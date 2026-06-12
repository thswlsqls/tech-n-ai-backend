# 기술 블로그 주제 인덱스 — LLM AI 코딩 에이전트 엔지니어링 방법론 발전사

> 1차 소스: Anthropic 공식 엔지니어링 블로그·발표문·문서와 arXiv 논문 (아래 "공식 출처" 절).
> 별도의 설계 명세 문서 없이, 2026-06-13 자료 조사 세션에서 공식 원문을 직접 확인한 결과를 근거로 도출한 시리즈다.
>
> 대상 독자는 **Claude Code 같은 AI 코딩 도구를 일상적으로 쓰지만, 쏟아지는 엔지니어링 용어의 계보를 정리하지 못한 백엔드 개발자**다.
> 모든 단편은 다음 3관점으로 일관되게 잡았다: **(1) 발전 배경(Why) / (2) 핵심 기법 / (3) 용어의 출처**.

## 시리즈의 성격 — 발전사를 따라 누적되는 선형 시리즈

시리즈를 관통하는 축은 **"엔지니어가 설계하는 대상이 한 층씩 올라갔다"**는 한 문장이다.

```
01 프롬프트        02 컨텍스트         03 하네스            04 루프
(문장)      ─→    (토큰 구성)   ─→   (모델 밖 시스템) ─→  (시스템을 돌리는 시스템)
2020~              2025~               2025 말~             2026.6~ (형성 중)
```

- 각 단편은 독립 출판이 가능하다(한 편에 한 단계).
- 동시에 01 → 04 순서로 읽으면 발전사가 쌓인다. 앞 단편이 마지막 절에서 연 질문을 뒤 단편이 받는다.
- 별도의 메타/시리즈 글(`series-*.md`)은 두지 않는다. 총정리는 04의 "네 층의 사다리" 절이 맡는다.

두 번째 관통 장치는 **용어의 생애주기**다. 02(트윗 → 공식 문서 채택)와 04(발언·명명까지만, 공식 채택 없음)가 같은 구도로 대조된다. 특히 04는 "Fable 5 공식 발표문에 loop engineering이라는 단어가 없다"는 직접 확인이 글의 차별점이다.

## 1. 단편 글 목록

| # | 제목 | Why | 기법 | 용어출처 | 핵심 근거 | 분량 |
|---|---|:-:|:-:|:-:|---|---|
| [01](./01-prompt-engineering-from-papers-to-discipline.md) | 프롬프트 엔지니어링 — 문장 쓰기가 엔지니어링이 된 출발점 | ✅ | ✅ | ✅ | arXiv 논문 2편 + Anthropic 문서 | 보통 |
| [02](./02-context-engineering-beyond-prompts.md) | 컨텍스트 엔지니어링 — 설계 대상이 문장에서 토큰 구성으로 | ✅ | ✅ | ✅ | Anthropic 공식 글 2편 (2024-12, 2025-09) | 보통 |
| [03](./03-harness-engineering-systems-around-models.md) | 하네스 엔지니어링 — 최고 모델도 혼자서는 장기 작업에 실패한다 | ✅ | ✅ | ✅ | Anthropic 공식 글 2편 (2025-11, 2026-03) | 김 |
| [04](./04-loop-engineering-term-in-the-making.md) | 루프 엔지니어링 — Anthropic이 만든 말이 아니다 | ✅ | ✅ | ✅ | Fable 5 공식 발표문 (2026-06) + 단어 부재 확인 | 김 |

### 단편 사이 인용 관계 (선형 누적)

| 단편 | 앞 단편 전제 | 뒤 단편으로 넘기는 질문 |
|---|---|---|
| 01 프롬프트 | — (출발점) | "한 턴 전제가 깨지면?" → 02 |
| 02 컨텍스트 | 01의 "설계 대상 = 문장" | "토큰을 관리하는 시스템은 누가 만드나" → 03 · 용어 생애주기 사례 → 04 |
| 03 하네스 | 02의 전략 3종 | "하네스를 돌리는 건 여전히 사람" → 04 |
| 04 루프 | 01~03 전체 | — (시리즈 마무리, 네 층 사다리 총정리) |

## 2. 폐기·병합 로그(투명성)

- 🔁 **"용어는 트윗에서 태어나 공식 문서에서 어른이 된다" 메타 단편** — 조사 세션의 글감 후보였으나, 02(컨텍스트 용어 유래)·04(루프 용어 유래)와 내용이 겹치고 그 단편만 미검증 출처 비중이 높아진다. **02의 6번 절 + 04의 5번 절로 분산 병합** (편수 확정 시 사용자 합의).
- ❌ **"실습형: Claude Code로 네 층 직접 밟아보기"** — 발전사·개념 정리 시리즈와 성격이 다르다(근거가 공식 문서가 아니라 본인 실험). 본 시리즈에서 제외하고, 쓰려면 별도 단편으로 기획한다.
- ❌ **"prompt engineering 용어 자체의 기원 추적"** — 공식 출처로 못 박지 못했다. 01은 기법의 기원(논문)만 다루고 용어 탄생 시점은 단정하지 않는다.
- ❌ **3편 압축안(프롬프트+컨텍스트 합본)** — 두 단계는 전제(한 턴 vs 멀티 턴)가 달라 합치면 한 편 분량이 터지고 발전사의 마디가 흐려진다. 4편으로 확정.

## 3. 작성 가이드

- **인용 정책**: 기술적 사실의 근거는 Anthropic 공식 블로그·발표문·문서와 arXiv 게시 논문만 쓴다. 블로그·포럼·AI 생성 콘텐츠 인용 금지(`tech-n-ai-backend/CLAUDE.md` 외부 자료 참조 원칙). 논문은 제목·저자·플랫폼·URL을 명시한다.
- **용어 유래 서술의 예외 처리**: 이 시리즈는 용어의 출처 추적이 주제라서 X 게시물·개인 블로그(Karpathy, Cherny, Osmani)를 다뤄야 한다. 이들은 **기술적 사실의 근거가 아니라 "발언·명명의 기록"으로만** 인용하고, 본문에서 미검증·비공식임을 밝힌다. 각 설계도의 "용어 유래 참고 자료 (근거 아님 · 미검증)" 절이 그 목록이다.
- **본문 언어·톤**: 한국어 `-ㅂ니다`체(완성 글은 `write-tech-blog` 규칙). 기술 용어는 첫 등장에서 영어 병기(프롬프트 엔지니어링(prompt engineering)) 후 한글 표기.
- **영어 인용**: 정의·핵심 주장 인용문은 영어 원문 그대로 싣고 바로 아래 한국어로 푼다. 번역만 싣지 않는다.
- **날짜를 본문에 박는다**: 발전사 시리즈이므로 각 문서·발표의 게시일(2024-12-19, 2025-09-29, 2025-11-26, 2026-03-24, 2026-06-09)이 서사의 뼈대다. 원문에서 확인한 날짜만 쓴다.
- **시간 의존 사실 재확인**: "루프 엔지니어링의 공식 채택 없음"은 2026-06-13 기준이다. 04 작성 시점에 Anthropic 공식 자료를 재확인한다. 모델명·가격 등 빠르게 바뀌는 사실은 본문에 넣지 않거나 기준일을 명시한다.
- **단편 작성 시**: 각 단편 끝의 "시리즈 인용 관계" 절을 유지해 앞뒤 단편이 어떤 질문을 주고받는지 신호를 남긴다. 앞 단편이 정의한 개념을 뒤 단편이 다시 정의하지 않는다.
- **분량·SEO**: 완성 글은 `write-tech-blog`에서 7,000자 이상·SEO 제목 후보 3개+·번호 없는 소제목으로 다듬는다. 설계도의 아웃라인 번호는 기획용이다.

## 공식 출처

- Brown et al., *Language Models are Few-Shot Learners*, arXiv (2020) — https://arxiv.org/abs/2005.14165
- Wei et al., *Chain-of-Thought Prompting Elicits Reasoning in Large Language Models*, arXiv (2022) — https://arxiv.org/abs/2201.11903
- Anthropic — Prompt engineering overview — https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/overview
- Anthropic — Building effective agents (2024-12-19) — https://www.anthropic.com/engineering/building-effective-agents
- Anthropic — Effective context engineering for AI agents (2025-09-29) — https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents
- Anthropic — Effective harnesses for long-running agents (2025-11-26) — https://www.anthropic.com/engineering/effective-harnesses-for-long-running-agents
- Anthropic — Harness design for long-running application development (2026-03-24) — https://www.anthropic.com/engineering/harness-design-long-running-apps
- Anthropic — Claude Fable 5 and Claude Mythos 5 (2026-06-09) — https://www.anthropic.com/news/claude-fable-5-mythos-5

> 위 공식 출처 외의 X 게시물·개인 블로그(Karpathy, Osmani 등)는 "용어 유래 참고 자료"로만 쓰며, 기술적 사실의 근거로 인용하지 않는다.
