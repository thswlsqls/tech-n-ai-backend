# batch-eval 모듈

## 개요

RAG 챗봇의 검색과 답변 품질을 수치로 재는 Spring Batch 모듈입니다. 검색 방식을 바꿀 때 좋아졌는지 나빠졌는지를 감이 아니라 같은 질문 목록과 같은 지표로 비교하려고 만들었습니다.

`:api-chatbot`을 의존성으로 가져와 **운영과 같은 코드로 검색합니다**. 의도 분류 → 입력 해석 → 검색 → 결과 정제까지 운영이 타는 경로를 그대로 태우기 때문에, 여기서 잰 값이 실제 서비스 동작과 어긋나지 않습니다.

JobRepository로 `ResourcelessJobRepository`를 쓰므로 MySQL이 필요 없습니다. 실행에는 MongoDB Atlas 접속과 OpenAI API 키가 필요합니다.

## 배치 잡 목록

잡 이름 상수는 `job/EvalJobConfig.java`에 있고 셋 모두 단일 스텝 Tasklet입니다.

| 잡 이름 | 하는 일 | LLM 호출 |
|---------|---------|----------|
| `searchBaselineEvalJob` | 골든셋 질문을 검색만 태워 recall·MRR 등 검색 지표를 낸다 | 임베딩만 |
| `answerQualityEvalJob` | 답변까지 생성하고 다른 모델로 두 축을 채점한다 | 임베딩 + 생성 + 판정 |
| `goldenSetVerifyJob` | 골든셋이 가리키는 근거 문서가 실제 컬렉션에 있는지 확인한다 | 없음 |

## 골든셋

`resources/goldenset/emerging-tech-goldenset.json`에 질문 52건이 있습니다. 질문마다 기대 근거 문서의 `external_id` 목록과 유형이 붙어 있습니다.

유형은 여섯 가지입니다(`GoldenSetItemType`).

| 유형 | 뜻 |
|------|-----|
| `SINGLE_FACT` | 문서 하나로 답이 정해지는 질문 |
| `PROVIDER_SCOPED` | 특정 제공자로 범위를 좁힌 질문 |
| `TYPE_SCOPED` | 특정 업데이트 종류로 범위를 좁힌 질문 |
| `RECENCY` | 최신성 키워드가 들어간 질문 |
| `MULTI_HOP` | 답하려면 문서 여러 건을 엮어야 하는 질문 |
| `NO_EVIDENCE` | 근거 문서가 없는 게 정답인 질문 (채점에서 제외) |

## 지표

검색 지표는 질문별로 내고(`RetrievalScorer`) 유형별·전체로 묶습니다(`AggregateScorer`).

- **recall@k**: 상위 k건에 들어온 기대 근거 수 ÷ 기대 근거 수
- **hit@k**: 상위 k건에 기대 근거가 하나라도 있으면 참
- **MRR**: 첫 적중 순위의 역수. 적중이 없으면 0
- **falsePositive@k**: 상위 k건 중 기대 근거가 아닌 문서 수

같은 검색을 세 가지 순위로 각각 잽니다. `byVectorRank`는 벡터 후보 순위, `byMergedRank`는 그래프 결과까지 합친 순위, `byChainOutput`은 결과 정제 체인을 거쳐 실제로 답변에 넘어간 상위 목록 기준입니다. 검색이 문서를 찾아냈는데도 사용자에게 전달되지 않는 경우를 구분하려는 것입니다.

답변 품질은 `gpt-4o`가 두 축으로 채점합니다(`AnswerJudge`). 생성은 `gpt-4o-mini`라 자기 답을 후하게 보는 편향을 피합니다.

- **근거 기반성**: 답변 내용이 넘겨준 근거 문서에서 나왔는가
- **질문 응답성**: 질문이 물은 것에 답했는가

## 실행

`local` 프로필로 돌리며 잡 이름과 리포트 출력 경로를 인자로 줍니다. `eval.report.dir`은 기본값이 없습니다 — 체크아웃 위치마다 다른 값이라 코드에 박지 않았습니다.

```bash
# 검색 기준선
./gradlew :batch-eval:bootRun --args='--job.name=searchBaselineEvalJob --eval.report.dir=/path/to/reports'

# 답변 품질 (개발 중에는 question-limit으로 자른다)
./gradlew :batch-eval:bootRun --args='--job.name=answerQualityEvalJob --eval.report.dir=/path/to/reports --eval.answer-quality.question-limit=10'

# 골든셋 점검 (LLM 호출 없음)
./gradlew :batch-eval:bootRun --args='--job.name=goldenSetVerifyJob --eval.report.dir=/path/to/reports'
```

주요 실행 파라미터입니다.

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `--eval.report.dir` | (필수) | 리포트를 떨굴 디렉터리 |
| `--eval.answer-quality.question-limit` | 0 | 골든셋 앞 N건만 돌린다. 0이면 전량 |
| `--eval.answer-quality.judge-call-limit` | 200 | 판정 모델 호출 상한 |
| `--eval.answer-quality.measure-judge-flip` | false | 같은 답변을 한 번 더 채점해 점수가 갈리는 비율을 잰다. 비용이 대략 두 배 |

검색 방식을 바꿔 가며 비교할 때는 `:api-chatbot`의 설정을 넘겨 켭니다.

```bash
--chatbot.rag.graph.enabled=true      # 지식 그래프 검색 (batch-graph가 만든 그래프를 쓴다)
--chatbot.rag.augment.enabled=true    # 근거가 약할 때 조건을 완화해 재검색
```

평가는 같은 질문에 같은 답이 나와야 비교가 되므로 `application-local.yml`이 생성 모델 `temperature`를 0으로 덮습니다.

`EmbeddingApiKeyGuardListener`가 잡 시작 시점에 OpenAI 키가 있는지 확인하고, 없으면 검색이 빈 결과로 흘러 0점 리포트가 남는 대신 잡을 세웁니다.

## 리포트

`eval.report.dir` 아래에 실행 시각을 붙인 JSON이 떨어집니다. 키 집합은 고정이며 값이 없어도 키를 빼지 않고 `null`·0·빈 배열을 넣습니다. 실행끼리 나란히 놓고 비교하려는 것입니다.

최상위 블록은 여섯 개입니다.

| 블록 | 내용 |
|------|------|
| `config` | 실행 당시 설정 스냅샷 (검색 결과 수, 유사도 문턱, 그래프·보강 켜짐 여부, 모델·차원, 코퍼스 문서 수) |
| `questions` | 질문 한 건씩의 검색 결과·지표·토큰·지연시간 |
| `aggregate` | 유형별·전체 집계 |
| `excluded` | 채점에서 뺀 질문과 그 이유 |
| `answerQuality` | 답변 품질 잡의 두 축 점수 (검색 잡에서는 `null`) |
| `schemaVersion` | 리포트 형식 버전. 현재 `4` |

블록이 늘거나 뜻이 바뀌면 `EvalReport.SCHEMA_VERSION`을 올립니다. 옛 리포트와 구분하기 위해서입니다.

## 모듈 구조

```
batch/eval/src/main/java/com/tech/n/ai/batch/eval/
├── BatchEvalApplication.java
├── config/
│   ├── BatchEvalConfig.java          # ResourcelessJobRepository 등 배치 부팅 설정
│   ├── ChatbotBridgeConfig.java      # api-chatbot의 검색 빈을 평가에서 쓰도록 배선
│   └── JudgeModelConfig.java         # 판정 전용 ChatModel (gpt-4o, temperature 0)
├── goldenset/                        # 골든셋 로딩과 유형 정의
├── job/
│   ├── EvalJobConfig.java            # 잡·스텝 정의
│   ├── SearchBaselineTasklet.java    # 검색 기준선
│   ├── AnswerQualityTasklet.java     # 답변 생성 + 채점
│   ├── GoldenSetVerifyTasklet.java   # 골든셋 점검
│   ├── QuestionRunner.java           # 질문 한 건을 운영 경로로 태우는 공통 실행기
│   └── EmbeddingApiKeyGuardListener.java
├── judge/                            # 판정 프롬프트와 결과 파싱
├── report/                           # 리포트 레코드와 파일 출력
└── scoring/                          # 질문별 지표와 집계
```

## 의존성

`:api-chatbot`(부트 잡이지만 `jar.enabled = true`라 라이브러리로도 쓸 수 있습니다), `:datasource-mongodb`, `:common-core`. langchain4j 버전은 루트 `build.gradle`의 `langchain4j-bom`이 정합니다.

## 참고

- 커스텀 미터로 나가는 LLM·검색 지표는 [`monitoring/README.md`](../../monitoring/README.md) 5절에 있습니다.
- 지식 그래프를 만드는 쪽은 [`batch-graph`](../graph/README.md)입니다.
