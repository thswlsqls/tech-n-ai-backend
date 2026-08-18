# 로컬 관측 스택 — 브라우저 접속 가이드

로컬 개발 환경에서 메트릭·로그·트레이스를 브라우저로 보기 위한 안내다.
여기서 다루는 것은 **루트 `docker-compose.yml`로 띄우는 로컬 도구들**이다.

> 운영(AWS) 관측은 별개다. 운영은 CloudWatch + X-Ray + Amazon Managed Grafana로
> 설계돼 있고, 그 내용은 [`../devops/results/08-observability.md`](../devops/results/08-observability.md)에 있다.
> 이 문서와 헷갈리지 말 것 — 여기 적힌 URL은 전부 **내 PC 로컬**이다.

---

## 1. 먼저 띄우기

관측 스택은 루트의 `docker-compose.yml` 한 파일에 데이터베이스·Kafka와 함께 들어 있다.
관측 도구만 골라 띄우거나, 전체를 한 번에 띄운다.

```bash
# tech-n-ai-backend 디렉터리에서 실행

# 전체 (DB·Kafka·관측 스택 모두)
docker compose up -d

# 관측 스택만
docker compose up -d prometheus pushgateway alertmanager jaeger loki promtail grafana

# 상태 확인
docker compose ps

# 로그 보기 (예: grafana)
docker compose logs -f grafana

# 내릴 때
docker compose down
```

> 메트릭을 보려면 관측 스택뿐 아니라 **관측 대상(API 서버, Jenkins)도 떠 있어야** 한다.
> Prometheus는 호스트의 `8081~8086`(API 6종)과 `8080`(Jenkins)을 긁어가는데, 그 포트에
> 아무것도 안 떠 있으면 해당 타깃은 그냥 `DOWN`으로 보인다(스택 자체 문제는 아니다).

---

## 2. 브라우저로 여는 주소

| 도구 | 로컬 주소 | 계정 | 무엇을 보나 |
|---|---|---|---|
| **Grafana** | http://localhost:3002 | `admin` / `admin` | 메트릭·로그·트레이스를 한곳에서 (시작점) |
| **Prometheus** | http://localhost:9091 | 없음 | 메트릭 원본, 스크랩 타깃 상태(`Status → Targets`) |
| **Alertmanager** | http://localhost:9094 | 없음 | 발화된 알림 확인 |
| **Pushgateway** | http://localhost:9095 | 없음 | batch-source가 밀어 넣은 단발성 메트릭 |
| **Jaeger** | http://localhost:16686 | 없음 | 분산 트레이스(요청이 서비스들을 거친 경로) |
| **Kafka UI** | http://localhost:9090 | 없음 | Kafka 토픽·메시지·컨슈머 랙 |
| **Jenkins** | http://localhost:8080 | 설치 시 설정 | 배치 잡 빌드·스케줄 (→ [4. Jenkins](#4-jenkins) 참고) |

> 포트가 헷갈리기 쉽다. compose에서 컨테이너 안쪽 포트와 호스트 포트를 바꿔 매핑한
> 것들이 있다 — 예: Grafana는 컨테이너 `3000`을 호스트 `3002`로, Prometheus는 `9090`을
> `9091`로, Kafka UI는 `8080`을 `9090`으로 내보낸다. **브라우저에는 위 표의 "로컬 주소"를
> 그대로** 쓰면 된다.

대부분의 경우 **Grafana(3002) 하나만** 열면 된다. Prometheus·Loki·Jaeger는 Grafana에
데이터소스로 이미 연결돼 있어서, 메트릭·로그·트레이스를 Grafana 안에서 함께 본다.
나머지 주소는 원본을 직접 들여다보거나 문제를 추적할 때 쓴다.

---

## 3. 데이터가 흐르는 방식

![로컬 관측 스택 다이어그램](../docs/monitoring/observability_stack.png)

> 위 그림 소스: [`../docs/monitoring/observability_stack.py`](../docs/monitoring/observability_stack.py)
> (mingrammer Diagrams). 아래 텍스트 버전은 그림이 안 보일 때를 위한 대체본이다.

```
[API 서버 8081~8086]  --/actuator/prometheus-->  ┐
[Jenkins 8080]        --/prometheus/----------->  ├─> [Prometheus 9091] ─┐
[batch-source]        --push-->  [Pushgateway] ─> ┘                      │
                                                                         ├─> [Grafana 3002]
[앱 로그 파일]   --> [Promtail] --> [Loki 3100] ─────────────────────────┤
[Jenkins 로그]   ┘                                                       │
                                                                         │
[앱 트레이스]  --OTLP 4317/4318--> [Jaeger 16686] ───────────────────────┘
```

- **메트릭**: 각 서비스가 노출하는 `/actuator/prometheus`(Spring Boot), `/prometheus/`(Jenkins)를
  Prometheus가 15초마다 긁어온다. 배치 잡처럼 잠깐 떴다 사라지는 프로세스는 Pushgateway로 밀어 넣는다.
  Prometheus 스크랩 대상은 [`prometheus/prometheus.yml`](prometheus/prometheus.yml)에 있다.
- **로그**: Promtail이 앱 로그(`logs/`)와 Jenkins 로그(`~/.jenkins/logs`)를 읽어 Loki로 보낸다.
  Loki는 보통 직접 안 열고 Grafana의 `Explore`에서 조회한다.
- **트레이스**: 앱이 OTLP(`4317` gRPC / `4318` HTTP)로 Jaeger에 트레이스를 보낸다.

Grafana 데이터소스(Prometheus·Loki·Jaeger)는
[`grafana/provisioning/datasources/datasources.yml`](grafana/provisioning/datasources/datasources.yml)로
**자동 연결**된다. 로그인 후 따로 추가할 필요 없다. 트레이스↔로그 연결도 걸려 있어서,
Jaeger 트레이스에서 같은 `traceId`의 로그로 바로 넘어갈 수 있다.

> **대시보드는 기본 제공이 없다.** 데이터소스만 자동 연결돼 있고 미리 만든 대시보드 JSON은
> 커밋돼 있지 않다. 필요하면 Grafana에서 직접 만들거나 [grafana.com/dashboards](https://grafana.com/grafana/dashboards/)에서
> 가져와(Import) `/var/lib/grafana/dashboards`에 두면 자동 인식된다
> (provider 설정: [`grafana/provisioning/dashboards/dashboards.yml`](grafana/provisioning/dashboards/dashboards.yml)).

### OpenTelemetry는 어디에 쓰나

세 신호 중 **트레이스에만 OpenTelemetry(OTLP)를 쓴다.** 메트릭은 Prometheus, 로그는 Loki의
기본 경로를 따른다. 즉 "관측을 OTel 하나로 통일"한 게 아니라, 신호마다 가장 잘 맞는 경로를 골랐다.

| 신호 | 앱 쪽 계측·전송 | 수집 | OTel |
|---|---|---|---|
| **트레이스** | `io.micrometer:micrometer-tracing-bridge-otel` + `io.opentelemetry:opentelemetry-exporter-otlp` (+ `spring-boot-starter-opentelemetry`), OTLP gRPC `4317` / HTTP `4318`로 전송 | Jaeger v2 — OpenTelemetry Collector 기반 구성([`jaeger/jaeger-config.yml`](jaeger/jaeger-config.yml)의 `receivers.otlp` → `pipelines.traces`) | **사용** |
| **메트릭** | `io.micrometer:micrometer-registry-prometheus`로 `/actuator/prometheus` 노출 | Prometheus가 scrape | 미사용 |
| **로그** | JSON 파일에 `traceId`/`spanId` 포함 | Promtail → Loki | 미사용 (단 trace context는 전파) |

메트릭과 로그를 OTel 경로(OTLP 내보내기 / OTel Collector)로 보내지 않은 이유:

- **Jenkins는 OTLP를 못 낸다.** Prometheus 포맷만 `/prometheus/`로 노출하므로 긁어오는(scrape) 방식이 강제된다.
- **알림 규칙이 Prometheus에 묶여 있다.** [`prometheus/alert-rules.yml`](prometheus/alert-rules.yml)의 식이
  `up{job="jenkins"}`(타깃 생사), `spring_batch_job_seconds_count`, `jvm_memory_used_bytes` 같은
  Prometheus 메트릭과 PromQL에 의존한다. 타깃이 살아 있는지 알려주는 `up`은 scrape(pull) 모델에서만 공짜로 생긴다.
- **batch-source 메트릭은 Pushgateway**(Prometheus 생태계)로 들어온다.
- 로그는 Loki로 보내되, Promtail이 `traceId`/`spanId`를 structured metadata로 남겨
  Grafana에서 트레이스↔로그로 바로 넘어갈 수 있게 했다(OTel의 상관관계 이점은 그대로 챙긴다).

> 이 로컬 스택은 신호별 직접 경로를 쓰고 **중앙 OpenTelemetry Collector는 두지 않는다.**
> 운영(AWS)은 별개로 CloudWatch + X-Ray + Amazon Managed Grafana다(맨 위 머리말 참고).

---

## 4. Jenkins

Jenkins는 **docker-compose에 들어 있지 않다.** 호스트(내 PC)에 직접 설치해 운영하는 전제다.

### 왜 compose에 없나

compose의 서비스들은 개발 세션마다 `up`/`down` 하는 일회성 인프라다. 반면 Jenkins는
잡 설정·빌드 이력·크리덴셜을 `~/.jenkins`에 쌓아두고 `docker compose down`과 무관하게
계속 떠 있어야 한다. 그래서 컨테이너 대신 **OS 서비스 매니저(launchd/systemd)에 맡겨**
별도로 둔다. 관측 스택은 Jenkins를 **읽기 전용으로 로그만 가져다 보고**, 메트릭은
`localhost:8080`을 긁어갈 뿐 실행에는 관여하지 않는다.

### 설치·실행 (macOS)

```bash
brew install jenkins-lts
brew services start jenkins-lts      # launchd가 관리, 종료 시 자동 재시작
brew services info jenkins-lts       # 상태 확인
```

설치·초기 설정·클라우드(systemd) 배포까지의 자세한 절차는
[`../docs/jenkins/batch/01-jenkins-cicd-scheduling-design.md`](../docs/jenkins/batch/01-jenkins-cicd-scheduling-design.md)에 있다.

### Jenkins를 Grafana에서 보려면

- **로그**: Promtail이 `~/.jenkins/logs/*.log`를 읽는다(compose의 `promtail` 마운트). 별도 설정 불필요.
- **메트릭**: Prometheus가 `http://localhost:8080/prometheus/`를 긁는다. 이 경로가 뜨려면
  Jenkins에 **Prometheus metrics 플러그인**이 설치돼 있어야 한다. 없으면 Prometheus의
  `jenkins` 타깃이 `DOWN`으로 보인다.

> 운영용 CI/CD는 Jenkins가 아니라 **GitHub Actions**다([`../devops/results/07-cicd-overview.md`](../devops/results/07-cicd-overview.md)).
> 여기 Jenkins는 로컬에서 배치 잡 스케줄링을 다루기 위한 것으로, 둘은 별개다.

---

## 5. 애플리케이션 커스텀 미터

앱이 직접 등록한 미터 여섯 개다. Spring Boot의 기본 미터(JVM·HTTP)로는 챗봇과 에이전트가
안에서 무엇을 하는지 알 수 없어 따로 만들었다. 태그는 붙이지 않았고, 서비스 이름을 구분하는
`application` 태그만 [`../common/core/src/main/resources/application-common-core.yml`](../common/core/src/main/resources/application-common-core.yml)이
자동으로 붙인다. 값은 Prometheus에서 아래 이름으로 보인다(Micrometer가 점을 밑줄로 바꾼다).

### `chatbot.llm.duration` — LLM 호출 지연시간 (api-chatbot)

Prometheus 이름: `chatbot_llm_duration_seconds_count` / `_sum` / `_max`

- **누가 언제 보나**: 챗봇이 느리다는 얘기가 나올 때, 그리고 모델이나 프롬프트를 바꿔 배포한 직후에 백엔드 담당자가 본다.
- **어떤 값이면 이상인가**: `_sum / _count`로 낸 평균이 30초를 넘으면 이상이다. 모델 호출 타임아웃이 60초라 평균이 그 절반까지 올라갔다면 상당수 호출이 타임아웃으로 떨어지고 있다는 뜻이다.
- **넘으면 무엇을 하나**: 같은 시간대의 `chatbot_llm_errors_total`이 함께 올랐는지 보고, 올랐으면 OpenAI 장애를, 아니면 프롬프트가 길어졌는지(근거 문서 개수·`chatbot.rag.max-context-tokens`)를 확인한다.

### `chatbot.llm.errors` — LLM 호출 실패 건수 (api-chatbot)

Prometheus 이름: `chatbot_llm_errors_total`

- **누가 언제 보나**: 평소에는 알림이 대신 본다. `ChatbotLlmHighFailureRate` 알림이 오면 백엔드 담당자가 확인한다.
- **어떤 값이면 이상인가**: 최근 5분 실패 비율(`rate(chatbot_llm_errors_total[5m]) / rate(chatbot_llm_duration_seconds_count[5m])`)이 10%를 넘으면 이상이다. 실패한 호출도 지연시간 미터에 세므로 이 나눗셈이 곧 실패율이다.
- **넘으면 무엇을 하나**: 앱 로그에서 `Failed to generate LLM response`를 찾아 원인(인증 실패·요금 한도·타임아웃)을 가른다. 키나 한도 문제면 설정을, 제공자 장애면 복구를 기다린다.

### `chatbot.llm.input.tokens` — LLM 호출당 입력 토큰 수 (api-chatbot)

Prometheus 이름: `chatbot_llm_input_tokens_sum` / `_count` / `_max`

제공자가 응답에 실어 보낸 실측값이다. `LLMService`를 거치는 호출만 세므로, 세션 제목 생성처럼 `ChatModel`을 직접 부르는 경로는 여기에 안 잡힌다.

- **누가 언제 보나**: OpenAI 청구액이 예상보다 클 때, 그리고 프롬프트 템플릿이나 근거 문서 개수를 바꿔 배포한 직후에 백엔드 담당자가 본다. `_sum`은 그 기간에 `LLMService`를 거친 호출의 입력 토큰 합계라, 실제 청구량의 하한으로 본다.
- **어떤 값이면 이상인가**: `_sum / _count`로 낸 호출당 평균이 2,000을 넘으면 이상이다. 지금 RAG 프롬프트는 근거 문서까지 합쳐 한 건 평균 660토큰이라 평균이 세 배로 뛰었다면 프롬프트에 뭔가 과하게 붙고 있는 것이다. `_max`가 4,000(`chatbot.token.max-input-tokens`)에 가까워지면 RAG·웹 검색 경로가 그 한도에 걸릴 수 있다는 신호다. 다만 두 숫자가 재는 범위가 다르다. 한도 검사는 `PromptService`가 만든 프롬프트 문자열 하나를 자체 추정식으로 센 값이고, 이 미터는 챗 메모리에 실린 이전 대화까지 포함한 요청 전체에 대해 제공자가 돌려준 실측값이다. 일반 대화 경로와 웹 검색 결과가 0건일 때의 대체 응답 경로는 이 검사를 아예 거치지 않는다.
- **넘으면 무엇을 하나**: `chatbot.rag.max-search-results`(5건)와 `chatbot.rag.max-context-tokens`(3,000)를 확인해 프롬프트에 붙는 근거 문서를 줄인다.
- **결측 확인**: `chatbot_llm_duration_seconds_count - chatbot_llm_errors_total - chatbot_llm_input_tokens_count`가 사용량이 안 실려 온 호출 수다. 실패한 호출은 지연시간 미터에는 세지만 토큰은 기록하지 않으니 실패 건수를 먼저 빼야 한다. 값이 없으면 0으로 채우지 않고 건너뛰기 때문에 남은 차이로 결측이 드러난다. 실패 건수를 뺀 뒤에도 차이가 계속 늘면 제공자 응답에서 사용량이 빠지고 있다는 뜻이라 `_sum`을 청구량으로 믿으면 안 된다.

### `chatbot.llm.output.tokens` — LLM 호출당 출력 토큰 수 (api-chatbot)

Prometheus 이름: `chatbot_llm_output_tokens_sum` / `_count` / `_max`

- **누가 언제 보나**: 답변이 중간에 잘린다는 제보가 들어왔을 때, 그리고 입력 토큰과 함께 비용을 볼 때 본다. 출력 토큰은 단가가 입력보다 비싸서 합계를 따로 본다.
- **어떤 값이면 이상인가**: `_max`가 2,000(`LangChain4jConfig`의 `maxTokens`)에 붙어 있으면 이상이다. 모델이 하고 싶은 말을 다 못 하고 한도에서 잘렸다는 뜻이다.
- **넘으면 무엇을 하나**: 잘린 답변이 실제로 나오는지 대화 로그로 확인하고, 맞으면 답변을 짧게 쓰도록 프롬프트를 고치거나 `maxTokens`를 올린다.

### `chatbot.search.results` — RAG 검색 결과 건수 (api-chatbot)

Prometheus 이름: `chatbot_search_results_count` / `_sum` / `_max`

- **누가 언제 보나**: "답변에 출처가 안 붙는다", "엉뚱한 답을 한다"는 제보가 들어왔을 때 본다.
- **어떤 값이면 이상인가**: `_sum / _count`로 낸 질문당 평균이 1건 밑으로 떨어지면 이상이다. 한 번에 최대 5건(`chatbot.rag.max-search-results`)까지 가져오게 돼 있는데 평균이 1건도 안 된다면 검색이 사실상 비어서 돌아오고 있다는 뜻이다.
- **넘으면 무엇을 하나**: MongoDB Atlas의 Vector Search 인덱스가 살아 있는지, 임베딩 모델 호출이 실패하고 있지 않은지, 유사도 하한(`chatbot.rag.min-similarity-score`)이 너무 높지 않은지 순서대로 확인한다.

### `agent.tool.calls` — 에이전트 실행당 Tool 호출 횟수 (api-agent)

Prometheus 이름: `agent_tool_calls_count` / `_sum` / `_max`

- **누가 언제 보나**: 에이전트 실행이 오래 걸리거나 OpenAI 비용이 튀었을 때 본다.
- **어떤 값이면 이상인가**: `_sum / _count`로 낸 실행당 평균이 15회를 넘으면 이상이다. 한 실행에서 허용하는 최대 Tool 호출이 30회(`EmergingTechAgentImpl.MAX_TOOL_INVOCATIONS`)인데 평균이 그 절반이면 같은 Tool을 반복해 부르는 루프가 섞여 있다고 본다.
- **넘으면 무엇을 하나**: 해당 시간대 에이전트 로그에서 `루프 감지`·`최대 Tool 호출 횟수 초과` 메시지를 찾아 어느 Tool이 반복되는지 보고, 그 Tool의 응답이나 프롬프트 지시를 고친다.

---

## 6. 자주 겪는 문제

| 증상 | 확인할 것 |
|---|---|
| Grafana는 떴는데 그래프가 비어 있음 | 관측 **대상**(API 서버·Jenkins)이 떠 있는지. Prometheus `Status → Targets`에서 `UP`인지 확인 |
| Prometheus 타깃이 전부 `DOWN` | 컨테이너 안에서 호스트로 나가는 길이 `host.docker.internal`이다. Docker Desktop이 아닌 환경에선 동작이 다를 수 있다 |
| Jenkins 메트릭만 `DOWN` | Jenkins에 Prometheus metrics 플러그인이 있는지, `http://localhost:8080/prometheus/`가 브라우저에서 열리는지 |
| 로그가 Loki에 안 보임 | 앱이 `logs/` 아래에 JSON 로그 파일을 쓰고 있는지, `~/.jenkins/logs`에 로그가 있는지 |
| 포트 충돌로 컨테이너가 안 뜸 | 위 표의 호스트 포트(3002·9091·9094·9095·16686·9090·3100 등)를 이미 다른 게 쓰고 있는지 `lsof -i :<포트>`로 확인 |

설정 파일 위치:

- Prometheus: [`prometheus/prometheus.yml`](prometheus/prometheus.yml), 알림 규칙 [`prometheus/alert-rules.yml`](prometheus/alert-rules.yml)
- Alertmanager: [`alertmanager/alertmanager.yml`](alertmanager/alertmanager.yml)
- Loki: [`loki/loki-config.yml`](loki/loki-config.yml) · Promtail: [`promtail/promtail-config.yml`](promtail/promtail-config.yml)
- Jaeger: [`jaeger/jaeger-config.yml`](jaeger/jaeger-config.yml)
- Grafana provisioning: [`grafana/provisioning/`](grafana/provisioning/)
