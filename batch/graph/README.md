# batch-graph 모듈

## 개요

`emerging_techs` 컬렉션의 문서에서 회사·모델·기술 같은 개체와 그 사이 관계를 뽑아 지식 그래프로 쌓는 Spring Batch 모듈입니다. 벡터 검색만으로는 답을 찾기 어려운 질문 — 이를테면 "이 모델의 이전 버전은 무엇인가"처럼 문서 여러 건을 엮어야 하는 것 — 을 노드와 엣지를 타고 가서 찾아보려고 만들었습니다.

추출에는 langchain4j community의 `LLMGraphTransformer`와 `gpt-4o-mini`를 씁니다. `emerging_techs`는 읽기만 하고, 쓰는 곳은 `tech_graph_nodes`·`tech_graph_edges` 두 컬렉션뿐입니다.

MySQL을 쓰지 않고 `:api-chatbot`에도 의존하지 않습니다. 실행에는 MongoDB Atlas 접속과 OpenAI API 키가 필요합니다.

## 배치 잡

`techGraphBuildJob` 하나이며 단일 스텝 Tasklet입니다.

## 그래프 스키마

노드 다섯 종, 관계 다섯 종으로 미리 정해 두었습니다.

| 노드 타입 | 예 |
|-----------|-----|
| `Company` | OpenAI, Anthropic |
| `Model` | GPT-4o, Claude |
| `Technology` | RAG, Function Calling |
| `Release` | 버전·릴리스 |
| `Capability` | 기능·능력 |

| 관계 타입 | 뜻 |
|-----------|-----|
| `RELEASED` | 냈다 |
| `SUCCEEDS` | 뒤를 잇는다 |
| `SUPPORTS` | 지원한다 |
| `USES` | 쓴다 |
| `DEPENDS_ON` | 의존한다 |

**`LLMGraphTransformer`에 `allowedNodes`·`allowedRelationships`를 넘겨도 출력이 목록 안에 있다는 보장은 없습니다.** 그 값들은 프롬프트를 만들 뿐이고, 응답을 읽을 때 목록 밖 타입도 그대로 통과시킵니다. 그래서 저장 직전에 `GraphTypeWhitelist`가 한 번 더 거릅니다. 목록 안 타입만 저장된다는 성질은 이 클래스가 만듭니다.

## 키와 멱등성

노드·엣지를 다시 찾는 유일한 수단은 `key`입니다(`GraphKeys`, `:datasource-mongodb`에 있어 배치와 조회가 같은 규칙을 씁니다).

- 노드 키: `타입 라벨 + "|" + 정규화한 이름` — 예: `Model|gpt-4o`
- 엣지 키: `출발 키 + "->" + 관계 라벨 + "->" + 도착 키` — 예: `Company|openai->RELEASED->Model|gpt-4o`

이름 정규화는 앞뒤 공백 제거, 연속 공백 한 칸으로 축약, 소문자 변환입니다. 저장은 `key`로 찾아 upsert하고, 그 노드가 나온 문서의 `external_id`는 `$addToSet`으로 더합니다. 그래서 같은 문서를 다시 돌려도 노드가 늘지 않습니다.

`key`의 unique 인덱스는 `@PostConstruct`가 아니라 **잡 시작 시점에** 만듭니다. 잡을 돌리지 않고 컨텍스트만 띄웠을 때 운영 Atlas에 쓰기가 나가는 것을 막기 위해서입니다.

다만 **추출 자체가 결정적이지 않습니다.** 같은 입력으로 두 번 돌리면 문서 일부가 다른 답을 내서 노드·엣지 개수가 달라집니다(실측 627건 기준 91건). 키 단위 멱등성은 지켜지지만 실행 결과가 완전히 같지는 않습니다.

## 실행

`local` 프로필로 돌립니다. 단가와 리포트 경로는 기본값이 없어 실행할 때 반드시 줍니다 — 단가는 모델·시점마다 다르고, 리포트 경로는 체크아웃 위치마다 다르기 때문입니다.

```bash
# 표본 20건으로 먼저 확인
./gradlew :batch-graph:bootRun --args='--job.name=techGraphBuildJob \
  --graph.build.document-limit=20 \
  --graph.build.select=spread \
  --graph.build.input-price-per-1m-usd=0.15 \
  --graph.build.output-price-per-1m-usd=0.60 \
  --graph.build.report.dir=/path/to/reports'

# 전량
./gradlew :batch-graph:bootRun --args='--job.name=techGraphBuildJob \
  --graph.build.document-limit=0 \
  --graph.build.input-price-per-1m-usd=0.15 \
  --graph.build.output-price-per-1m-usd=0.60 \
  --graph.build.report.dir=/path/to/reports'
```

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `--graph.build.document-limit` | 20 | N건만 돈다. 0이면 전량 |
| `--graph.build.select` | `recent` | `recent`는 최신순 앞에서 N건, `spread`는 provider·update_type이 섞이게 골고루 N건 |
| `--graph.build.input-text` | `embedding-text` | 추출에 넣을 텍스트. `embedding-text` 또는 `title-summary` |
| `--graph.build.model-name` | `gpt-4o-mini` | 추출 모델 |
| `--graph.build.reset` | false | 그래프 컬렉션 두 개를 비우고 시작한다 |
| `--graph.build.input-price-per-1m-usd` | (필수) | 입력 100만 토큰당 단가 |
| `--graph.build.output-price-per-1m-usd` | (필수) | 출력 100만 토큰당 단가 |
| `--graph.build.report.dir` | (필수) | 리포트를 떨굴 디렉터리 |

`--graph.build.select=recent`로 최신순만 자르면 표본이 한쪽으로 쏠립니다(20건 뽑았더니 OPENAI 16건, BLOG_POST 15건). 표본으로 품질을 볼 때는 `spread`를 씁니다.

`--graph.build.reset=true`는 타입 목록이나 이름 규칙을 바꿔 옛 노드가 남을 때만 씁니다. upsert는 없어진 것을 지우지 않기 때문입니다.

`GraphBuildJobListener`가 잡 시작 전에 API 키를 확인합니다. 키 없이 돌면 호출이 전부 실패하면서 노드 0개짜리 실행이 정상 결과처럼 남기 때문입니다.

문서 하나가 실패해도 잡은 멈추지 않고 그 문서 행에 사유를 남깁니다. 수백 건을 도는 실행이 문서 하나 때문에 통째로 날아가면 그때까지 쓴 API 비용이 사라집니다.

## 리포트

`graph.build.report.dir` 아래에 JSON과 Markdown 두 벌이 떨어집니다. 문서별 추출 결과와 실패 사유, 토큰 사용량, 단가를 곱한 실행 비용이 들어갑니다.

## 모듈 구조

```
batch/graph/src/main/java/com/tech/n/ai/batch/graph/
├── BatchGraphApplication.java
├── config/
│   ├── BatchGraphConfig.java            # ResourcelessJobRepository 등 배치 부팅 설정
│   └── GraphExtractionModelConfig.java  # 추출 전용 OpenAiChatModel
├── extract/
│   ├── GraphExtractor.java              # LLMGraphTransformer 조립과 호출
│   ├── GraphTypeWhitelist.java          # 목록 밖 타입 제거
│   └── GraphTokenUsageRecorder.java     # 토큰·비용 집계
├── job/
│   ├── GraphBuildJobConfig.java
│   ├── GraphBuildJobListener.java       # API 키 확인 → reset → unique 인덱스 생성
│   └── GraphBuildTasklet.java           # 문서 선택·추출·저장
├── report/                              # 리포트 레코드와 JSON·Markdown 출력
└── write/GraphWriter.java               # key 기준 upsert
```

## 의존성

`:datasource-mongodb`, `:common-core`. 추출에 `langchain4j-community-llm-graph-transformer`를 쓰며, 버전은 루트 `build.gradle`의 `langchain4j-bom`·`langchain4j-community-bom`이 정합니다.

## 그래프를 쓰는 쪽

`api-chatbot`의 `GraphSearchService`가 이 컬렉션을 읽어 RAG 검색 결과에 붙입니다. `chatbot.rag.graph.enabled`가 기본 꺼짐이라 평가 잡에서 켜서 씁니다. 자세한 내용은 [`api/chatbot/README.md`](../../api/chatbot/README.md)에 있습니다.

측정은 [`batch-eval`](../eval/README.md)이 맡습니다.
