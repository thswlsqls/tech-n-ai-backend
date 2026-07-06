# LLM AI Agent 토큰 사용량 세부 추적 오픈소스 조사

- 조사일: 2026-07-02 (GitHub stars·최근 push 시점 동일 기준)
- 목적: LLM AI Agent(및 LLM 애플리케이션)의 토큰 사용량을 세부적으로 추적하는 오픈소스를 찾아 두 그룹으로 분류
  - **(A)** 토큰 사용량 세부를 추적하고 대시보드/시각화로 보여주는 오픈소스
  - **(B)** 토큰 사용량 세부를 분석해 비용·효율을 개선(최적화)하는 솔루션을 제공하는 오픈소스
- 채택 기준(조작적 정의): 요청 단위 input/output/total 토큰을 기록하고, 추가로 (a) 모델·에이전트·단계(step/span/tool-call)별 분해, (b) 토큰→비용 환산, (c) 시간·사용자·세션 등 차원 집계 중 최소 하나를 만족

## 검증 방법과 신뢰 등급

이 보고서는 deep-research 워크플로우(웹 검색 → 공식 출처 fetch → 주장 추출 → 3표 교차검증)로 시작했다. 발굴·수집 단계는 끝났지만 교차검증·종합 단계가 세션 사용량 한도로 중단되어, 미검증 후보는 조사자가 공식 저장소·문서를 직접 열어 다시 확인했다. 각 항목의 신뢰 등급을 아래처럼 표기한다.

- **[검증2]** 워크플로우 3표 교차검증(3-0 또는 2-0)까지 통과 — Langfuse, Arize Phoenix
- **[확인1]** 조사자가 공식 저장소/문서를 직접 열어 인용까지 확인
- **[미확인]** 공식 출처를 확정하지 못했거나 재확인을 끝내지 못함

출처는 프로젝트 공식 GitHub 저장소와 공식 문서만 근거로 삼았다. 비공식 블로그·리스트 글은 후보 발견 단서로만 쓰고 사실 근거로는 쓰지 않았다.

---

## 1) 요약 비교표

> **정렬 기준**: 커뮤니티 성숙도(GitHub stars)를 1차 기준, 신뢰도(유지보수 지속성 = 최근 push 시점)를 2차 기준으로 삼았다. 최근 활동이 1년 이상 없는 저장소는 stars가 많아도 아래로 내렸다. stars·push는 2026-07-02 조회 기준.

### 그룹 (A) — 토큰 추적 + 대시보드/시각화

| 순위 | 이름 | Stars | 최근 push | 그룹 | 라이선스 | self-host | 토큰 세부 추적 방식(요약) | 저장소 |
|---|---|---|---|---|---|---|---|---|
| 1 | LiteLLM `[확인1]` | 52.3k | 2026-07-02 | A(+B) | MIT (단, `enterprise/` 예외 = open-core) | O (프록시 self-host) | `LiteLLM_SpendLogs`에 prompt/completion/total_tokens + USD spend 기록, key·user·team·model별 분해, UI Usage 탭 | github.com/BerriAI/litellm |
| 2 | Langfuse `[검증2]` | 30.3k | 2026-07-02 | A(+B) | MIT (단, `ee/` 폴더 예외 = open-core) | O (Docker Compose) | observation(generation/embedding)별 usage_details로 사용 유형별 토큰 기록 + ingestion 시점 비용 환산 + Metrics API로 user·tag 차원 집계 | github.com/langfuse/langfuse |
| 3 | Arize Phoenix `[검증2]` | 10.4k | 2026-07-02 | A | Elastic License 2.0 (source-available, OSI 아님) | O (로컬/Docker/K8s) | LLM span별 `llm.token_count.prompt/completion/total` 표준 속성 기록(OTel 기반) | github.com/Arize-ai/phoenix |
| 4 | OpenLLMetry (Traceloop) `[확인1]` | 7.3k | 2026-07-01 | A(계측 계층) | Apache-2.0 | O (SDK, 백엔드는 외부) | OTel 기반 LLM 계측으로 span 단위 사용량 캡처. **자체 대시보드는 없고** 외부 관측 백엔드로 export | github.com/traceloop/openllmetry |
| 5 | Helicone `[확인1]` | 5.9k | 2026-06-11 | A(+B) | Apache-2.0 | O (docker-compose, Helm) | AI Gateway 경유 요청의 비용·지연·사용량 추적 + 분석 대시보드 | github.com/Helicone/helicone |
| 6 | OpenLIT `[확인1]` | 2.6k | 2026-07-02 | A(+B) | Apache-2.0 | O (Docker Compose, UI :3000) | 요청별 토큰 추적 + 비용 환산(커스텀 단가 파일 지원) + 비용·토큰·상호작용 대시보드 | github.com/openlit/openlit |

그룹 (A)는 모든 저장소가 최근까지 활발히 유지보수되고 있어 stars 순서가 곧 정렬 순서다. 다만 4위 OpenLLMetry는 stars는 높지만 **자체 대시보드가 없는 계측 계층**이라, 대시보드 완성도만 놓고 보면 실사용 시 Langfuse·Phoenix·OpenLIT보다 순위를 낮춰 볼 여지가 있다.

### 그룹 (B) — 토큰 분석 기반 비용·효율 최적화

| 순위 | 이름 | Stars | 최근 push | 그룹 | 라이선스 | self-host | 최적화 방식(요약) | 저장소 |
|---|---|---|---|---|---|---|---|---|
| 1 | LiteLLM `[확인1]` | 52.3k | 2026-07-02 | B(+A) | MIT (open-core) | O | 예산(max_budget)·TPM 토큰 제한(input/output/total 선택), 라우팅·폴백, 캐싱 | github.com/BerriAI/litellm |
| 2 | Portkey AI Gateway `[확인1]` | 12.3k | 2026-05-25 | B(+A) | MIT | O | 가중치 라우팅, 폴백, 단순·시맨틱 캐싱, 사용량·비용 분석 | github.com/Portkey-AI/gateway |
| 3 | LLMLingua `[확인1]` | 6.4k | 2026-04-08 | B | MIT | O (라이브러리) | 프롬프트 압축(최대 20x)으로 입력 토큰 절감 | github.com/microsoft/LLMLingua |
| 4 | Bifrost (Maxim) `[확인1]` | 6.2k | 2026-07-02 | B(+A) | Apache-2.0 | O | 가상 키·팀·고객 단위 예산(계층적 비용 통제), 사용량 추적, 대시보드 | github.com/maximhq/bifrost |
| 5 | Helicone `[확인1]` | 5.9k | 2026-06-11 | B(+A) | Apache-2.0 | O | 게이트웨이 라우팅·자동 폴백, 캐싱, 비용 추적 | github.com/Helicone/helicone |
| 6 | GPTCache `[확인1]` ⚠️ | 8.1k | 2025-07-11 | B | MIT | O (라이브러리) | 시맨틱 캐시로 중복 질의의 LLM 요청·토큰 절감 | github.com/zilliztech/GPTCache |
| 7 | RouteLLM `[확인1]` ⚠️ | 5.1k | 2024-08-10 | B | Apache-2.0 | O (라이브러리) | 쉬운 질의를 저렴한 모델로 라우팅(최대 85% 비용 절감 주장) | github.com/lm-sys/RouteLLM |

⚠️ **유지보수 정체**: GPTCache(약 1년)와 RouteLLM(약 2년)은 stars가 적지 않지만 최근 커밋이 오래 끊겨 신뢰도(유지보수 지속성) 기준에서 아래로 내렸다. stars만 보면 GPTCache(8.1k)가 3~5위보다 높지만, 활발히 유지보수되는 저장소를 우선했다. 도입 검토 시 최신 모델 단가·의존성 호환성 문제를 직접 확인해야 한다.

### 프레임워크 네이티브 usage 추적 기능 (독립 제품이 아닌 SDK 기능)

| 이름 | 그룹 | 라이선스 | 토큰 추적 방식(요약) | 출처 |
|---|---|---|---|---|
| OpenAI Agents SDK (Python) `[확인1]` | 추적(A 입력) | Apache-2.0 | run 결과의 `result.context_wrapper.usage`에 input/output/total tokens·requests 집계, 요청별 항목 제공 | openai.github.io/openai-agents-python/usage |
| LangChain (langchain-core) `[확인1]` | 추적(A 입력) | MIT | `AIMessage.usage_metadata`(input/output/total) + `UsageMetadataCallbackHandler`가 **모델명별로 누적**, `get_usage_metadata_callback` 제공 | github.com/langchain-ai/langchain (`libs/core/.../callbacks/usage.py`) |
| CrewAI `[확인1]` | 추적(A 입력) | 저장소 확인 필요 | kickoff 후 `usage_metrics`/`CrewOutput.token_usage` 노출(공식 문서에 개별 토큰 필드 명세는 미기재) | docs.crewai.com/en/concepts/crews |

---

## 2) 프로젝트별 상세

### A. Langfuse `[검증2]`
1. **이름**: Langfuse
2. **저장소/문서**: https://github.com/langfuse/langfuse · https://langfuse.com/docs/observability/features/token-and-cost-tracking
3. **라이선스 / self-host**: MIT (단 `ee/`, `web/src/ee/`, `worker/src/ee/` 디렉터리는 별도 EE 라이선스 = open-core). Docker Compose로 self-host 가능(README: "Run Langfuse on your own machine in 5 minutes using Docker Compose"). VM·Kubernetes/Helm·Terraform 템플릿도 안내.
4. **규모·활성도**: GitHub stars 약 30,269, 최근 push 2026-07-02 (활발).
5. **토큰 세부 추적**: generation/embedding 관측 단위(observation)마다 `usage_details`로 사용 유형별(input·output 등) 토큰 수를 기록하고 total이 없으면 자동 계산("number of units consumed per usage type"). 모델 정의의 사용 유형별 단가로 ingestion 시점에 비용 환산("Inferred cost are calculated at the time of ingestion"). Metrics API로 user·tag 등 차원 집계 가능("application type, user, or tags"). → 조작적 정의 (a)(b)(c) 모두 만족.
6. **그룹**: A(대시보드형 관측). 토큰→비용 환산·예산 활용 관점에서 B 성격도 일부.
7. **(B 해당 시) 최적화 방식**: 토큰·비용 집계를 rate-limiting·과금 근거로 쓰는 수준. 프롬프트 압축·라우팅 같은 직접 절감 기능이 주력은 아님.
8. **근거**: 저장소 README, 공식 token-and-cost-tracking 문서 (위 인용).

### A. Arize Phoenix `[검증2]`
1. **이름**: Arize Phoenix
2. **저장소/문서**: https://github.com/Arize-ai/phoenix
3. **라이선스 / self-host**: **Elastic License 2.0 (ELv2)** — LICENSE 파일에서 확인. source-available이며 OSI 승인 오픈소스는 아니다(무료·self-host는 가능). 로컬·노트북·Docker·클라우드·K8s(Helm)로 실행.
4. **규모·활성도**: GitHub stars 약 10,371, 최근 push 2026-07-02 (활발).
5. **토큰 세부 추적**: OpenTelemetry 기반 계측으로 LLM span마다 `llm.token_count.prompt` / `llm.token_count.completion` / `llm.token_count.total`을 표준 속성으로 기록. → 조작적 정의 (a) span별 분해 만족.
6. **그룹**: A(대시보드형 관측·평가·트러블슈팅).
7. **(B) 최적화**: 해당 없음(관측·평가 중심).
8. **근거**: 저장소 README("open-source AI observability platform ... OpenTelemetry-based instrumentation"), span 속성 참조 문서, LICENSE 파일(ELv2).
   - 주의: README는 "open-source"라 표기하나 라이선스는 ELv2(source-available)이므로, 순수 OSI 오픈소스로 분류하지 말고 "무료·self-host 가능한 source-available"로 본다.

### A(+B). OpenLIT `[확인1]`
1. **이름**: OpenLIT
2. **저장소/문서**: https://github.com/openlit/openlit
3. **라이선스 / self-host**: Apache-2.0. Docker Compose로 self-host, 로컬 UI `127.0.0.1:3000`.
4. **규모·활성도**: GitHub stars 약 2,569, 최근 push 2026-07-02.
5. **토큰 세부 추적**: 요청 단위 추적 + 토큰→비용 환산. 커스텀/파인튜닝 모델용 커스텀 단가 파일 지원("Cost Tracking for Custom and Fine-Tuned Models ... custom pricing files"). → (b) 비용 환산, (c) 차원 집계 만족.
6. **그룹**: A(분석 대시보드: "Analytics Dashboard ... track metrics, costs, and user interactions"). 커스텀 단가 기반 예산 관점에서 B 성격 일부.
7. **(B) 최적화**: 비용 추적·예산 근거 제공 중심.
8. **근거**: 저장소 README(위 인용), gh 라이선스 조회(Apache-2.0).

### A+B. Helicone `[확인1]`
1. **이름**: Helicone
2. **저장소/문서**: https://github.com/Helicone/helicone
3. **라이선스 / self-host**: Apache-2.0. docker-compose로 self-host, enterprise용 Helm 차트.
4. **규모·활성도**: GitHub stars 약 5,892, 최근 push 2026-06-11.
5. **토큰 세부 추적**: AI Gateway로 100+ 모델 요청을 대리 처리하며 비용·지연 등 지표 추적("Cost & Latency Tracking ... Track metrics like cost, latency, quality"). PostHog 연동 등 분석 제공. → (b)(c) 만족.
6. **그룹**: A(관측·분석) + B(게이트웨이 최적화).
7. **(B) 최적화**: 지능형 라우팅·자동 폴백("intelligent routing and automatic fallbacks"), 캐싱.
8. **근거**: 저장소 README(위 인용), gh 라이선스 조회(Apache-2.0).

### A+B. LiteLLM `[확인1]`
1. **이름**: LiteLLM (Proxy / AI Gateway)
2. **저장소/문서**: https://github.com/BerriAI/litellm · https://docs.litellm.ai/docs/proxy/cost_tracking · https://docs.litellm.ai/docs/proxy/users
3. **라이선스 / self-host**: MIT (단 `enterprise/` 디렉터리는 별도 상용 라이선스 = open-core, LICENSE 파일 확인). 프록시를 직접 self-host.
4. **규모·활성도**: GitHub stars 약 52,342, 최근 push 2026-07-02 (매우 활발).
5. **토큰 세부 추적**: `LiteLLM_SpendLogs` 테이블에 `prompt_tokens`·`completion_tokens`·`total_tokens`와 USD `spend` 기록. api_key·user·team_id·model_group·provider별 분해, `/global/spend/report`로 group_by 리포트. model cost map으로 자동 비용 환산. → (a)(b)(c) 모두 만족.
6. **그룹**: A(UI Usage 탭 시각화) + B(게이트웨이 최적화).
7. **(B) 최적화**: 예산(max_budget) USD + reset 주기(budget_duration: 초/분/시/일)를 proxy·team·team member·internal user·virtual key·key별 모델·end-customer 단위로 설정. TPM(tokens/min) 제한을 input/output/total 중 선택해 계수. 라우팅·폴백·캐싱.
8. **근거**: cost_tracking·users 공식 문서(위 인용), LICENSE 파일(MIT + enterprise 예외).

### A(계측). OpenLLMetry (Traceloop) `[확인1]`
1. **이름**: OpenLLMetry
2. **저장소/문서**: https://github.com/traceloop/openllmetry
3. **라이선스 / self-host**: Apache-2.0. SDK 형태로 self-host(수집·시각화는 외부 백엔드).
4. **규모·활성도**: GitHub stars 약 7,257, 최근 push 2026-07-01.
5. **토큰 세부 추적**: OpenTelemetry 위에 얹은 LLM 계측 확장으로 LLM/Vector DB 표준 계측 제공. 토큰 사용량은 OTel GenAI 규약 기반 span 속성으로 캡처(README 발췌에는 토큰 필드가 명시되지 않아 이 부분은 규약 기반으로 판단).
6. **그룹**: A의 "계측 계층". **자체 대시보드는 없고** Datadog·Honeycomb 등 25+ 백엔드로 export.
7. **(B) 최적화**: 해당 없음(계측·전송 계층).
8. **근거**: 저장소 README("set of extensions built on top of OpenTelemetry ... standard OpenTelemetry instrumentations for LLM providers"), gh 라이선스(Apache-2.0).
   - 주의: 이 저장소만으로는 토큰 대시보드를 제공하지 않으므로, 대시보드는 연결하는 백엔드(예: Phoenix, 상용 관측 도구)에 의존.

### B. Portkey AI Gateway `[확인1]`
1. **이름**: Portkey AI Gateway
2. **저장소/문서**: https://github.com/Portkey-AI/gateway
3. **라이선스 / self-host**: MIT. open-source·self-host.
4. **규모·활성도**: GitHub stars 약 12,279, 최근 push 2026-05-25.
5. **토큰 세부 추적**: 사용량 분석에서 요청량·지연·비용·오류율 모니터링("Monitor and analyze your AI and LLM usage, including request volume, latency, costs and error rates"). → (b)(c) 만족.
6. **그룹**: B(게이트웨이 최적화) + A(사용량 분석).
7. **(B) 최적화**: 가중치 라우팅, 실패 시 폴백, 단순·시맨틱 캐싱("Cache responses from LLMs to reduce costs ... simple and semantic caching").
8. **근거**: 저장소 README(위 인용), gh 라이선스(MIT).

### B. Bifrost (Maxim) `[확인1]`
1. **이름**: Bifrost
2. **저장소/문서**: https://github.com/maximhq/bifrost
3. **라이선스 / self-host**: Apache-2.0. 23+ 공급자를 OpenAI 호환 단일 API로 통합, self-host.
4. **규모·활성도**: GitHub stars 약 6,203, 최근 push 2026-07-02.
5. **토큰 세부 추적**: 거버넌스에 "Usage tracking, rate limiting, and fine-grained access control" 포함, 실시간 모니터링·분석 웹 인터페이스.
6. **그룹**: B(예산·거버넌스) + A(대시보드).
7. **(B) 최적화**: 가상 키·팀·고객 단위 계층적 예산 통제("Hierarchical cost control with virtual keys, teams, and customer budgets"), rate limiting.
8. **근거**: 저장소 README(위 인용), gh 라이선스(Apache-2.0).
   - 주의: README가 일부 기능을 "enterprise feature"로 표기 — 무료 self-host 티어의 정확한 기능 경계는 배포 문서에서 추가 확인 권장.

### B. LLMLingua `[확인1]`
1. **이름**: LLMLingua (LongLLMLingua 포함)
2. **저장소/문서**: https://github.com/microsoft/LLMLingua
3. **라이선스 / self-host**: MIT. 파이썬 라이브러리로 self-host.
4. **규모·활성도**: GitHub stars 약 6,386, 최근 push 2026-04-08.
5. **토큰 관련**: 소형 LM으로 비핵심 토큰을 식별·제거해 프롬프트를 최대 20x 압축("up to 20x compression with minimal performance loss"). 토큰 자체 추적 도구는 아니고 **토큰 절감** 도구.
6. **그룹**: B(입력 토큰 절감).
7. **(B) 최적화 방식**: 프롬프트 압축(입력·생성 길이 축소).
8. **근거**: 저장소 README(위 인용).

### B. GPTCache `[확인1]`
1. **이름**: GPTCache
2. **저장소/문서**: https://github.com/zilliztech/GPTCache
3. **라이선스 / self-host**: MIT. LangChain·llama_index 연동 라이브러리, self-host.
4. **규모·활성도**: GitHub stars 약 8,085, 최근 push 2025-07-11(상대적으로 오래됨).
5. **토큰 관련**: 시맨틱 캐시로 유사 질의를 캐시 응답으로 처리해 요청·토큰 절감("reduces the number of requests and tokens sent to the LLM service").
6. **그룹**: B(캐싱으로 토큰 절감).
7. **(B) 최적화 방식**: 시맨틱 유사도 기반 캐싱.
8. **근거**: 저장소 README(위 인용).

### B. RouteLLM `[확인1]`
1. **이름**: RouteLLM
2. **저장소/문서**: https://github.com/lm-sys/RouteLLM
3. **라이선스 / self-host**: Apache-2.0. OpenAI 클라이언트 드롭인 라이브러리, self-host.
4. **규모·활성도**: GitHub stars 약 5,123, 최근 push 2024-08-10(유지보수 활동 낮음).
5. **토큰 관련**: 쉬운 질의를 저렴한 모델로 라우팅해 비용 절감(최대 85% 절감·GPT-4 성능 95% 유지 주장). 토큰 추적 도구는 아니고 **모델 라우팅** 최적화.
6. **그룹**: B(모델 라우팅).
7. **(B) 최적화 방식**: 비용-품질 임계값 기반 라우팅.
8. **근거**: 저장소 README(위 인용).

### 프레임워크 네이티브 usage 기능 (참고)
- **OpenAI Agents SDK (Python)** `[확인1]`: `result.context_wrapper.usage`에 input/output/total tokens·requests가 run 전체(툴 호출·핸드오프 포함) 집계, `request_usage_entries`로 요청별 상세. 라이선스 Apache-2.0. 출처: openai.github.io/openai-agents-python/usage.
- **LangChain (langchain-core)** `[확인1]`: `AIMessage.usage_metadata`(input/output/total) + `UsageMetadataCallbackHandler`가 모델명별로 사용량 누적, `get_usage_metadata_callback` 컨텍스트 매니저 제공. 공식 소스 `libs/core/langchain_core/callbacks/usage.py`에서 확인.
- **CrewAI** `[확인1]`: kickoff 후 `usage_metrics`·`CrewOutput.token_usage` 노출. 단, 공식 문서에 개별 토큰 필드(prompt/completion 등) 명세가 없어 상세 필드는 코드/버전 확인 필요. 출처: docs.crewai.com/en/concepts/crews.

---

## 3) 제외 목록 (후보였으나 기준 미달·성격 불일치)

- **OpenTelemetry GenAI Semantic Conventions** (opentelemetry.io/docs/specs/semconv): 토큰(`gen_ai.usage.input_tokens` 등) 표준을 정의하는 **규약**이지, 그 자체가 대시보드나 최적화 솔루션이 아니다. 위 여러 도구가 구현하는 기반. → 규약이라 제외.
- **Arize OpenInference** (github.com/Arize-ai/openinference): LLM 계측 **명세**. Phoenix 등이 사용하는 표준으로, 독립 제품·대시보드가 아니라 제외.

## 4) 미해결 / 미확인 목록

- **Lunary** (lunary-ai/lunary 추정): 공식 저장소 조회가 404 — 저장소가 이동/개명됐을 가능성. 위치를 확정하지 못해 미확인. 재조사 시 정확한 org/repo 확인 필요.
- **Microsoft AutoGen** (microsoft/autogen): model client의 `RequestUsage`(prompt/completion tokens)로 토큰을 노출한다고 워크플로우가 공식 문서에서 수집했으나, 조사자가 직접 재확인하지 못함. 출처 후보: microsoft.github.io/autogen (core-user-guide model-clients).
- **Kong AI Gateway** (developer.konghq.com/ai-gateway): OSS 코어는 존재하나, 토큰·비용 세부 대시보드가 무료 self-host 티어 범위인지 상용(enterprise) 기능인지 경계를 확정하지 못함. → 미확인.
- **OpenLLMetry의 대시보드**: 이 저장소 자체는 토큰 대시보드를 제공하지 않고 외부 백엔드로 export한다는 점을 근거로 "계측 계층"으로만 분류. 실제 시각화는 연결 백엔드에 의존.
- **Langfuse / LiteLLM의 세부 기능 중 일부**: 3표 교차검증이 세션 한도로 중단돼, self-hosting 세부(모든 컴포넌트가 오픈소스인지 등)와 일부 예산·리포트 세부는 위 인용 범위까지만 확인. 심화 도입 전 해당 공식 문서 재확인 권장.

---

## 부록: 조사 경위 메모

- deep-research 워크플로우가 5개 각도(관측 플랫폼 / 게이트웨이·프록시 비용관리 / 프레임워크 네이티브 usage / OTel GenAI 계측 / 토큰 절감 특화)로 분해해 23개 공식 소스, 113개 주장을 수집했다.
- 3표 교차검증은 세션 사용량 한도로 8개 주장(Langfuse·Phoenix)까지만 완료됐고, 나머지 후보는 조사자가 공식 저장소·문서를 직접 열어 재확인했다(위 `[확인1]`).
- stars·push 시점은 2026-07-02 GitHub API 조회 기준이다.
