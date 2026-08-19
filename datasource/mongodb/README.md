# datasource-mongodb 모듈

CQRS에서 **읽기(Query) 쪽**을 맡는 데이터 접근 모듈입니다. MongoDB Atlas에 연결하며 읽기에 최적화된 도큐먼트, 리포지토리, 집계 서비스, RAG 챗봇용 벡터 검색·지식 그래프 조회 유틸리티를 제공합니다.

라이브러리 모듈(`jar`)이며 `api-emerging-tech`·`api-chatbot`·`batch-graph`·`batch-eval` 등이 의존성으로 씁니다. 자바 패키지 루트는 `com.tech.n.ai.domain.mongodb` 입니다.

## 모듈 구조

```
datasource/mongodb/src/main/
├── java/com/tech/n/ai/domain/mongodb/
│   ├── config/
│   │   ├── MongoClientConfig.java        # 연결 문자열, 커넥션 풀, ReadPreference/WriteConcern
│   │   ├── MongoIndexConfig.java         # 시작 시 인덱스 자동 생성 (일반 + Vector Search)
│   │   └── VectorSearchIndexConfig.java  # Vector Search 인덱스 정의 상수 + Atlas CLI 헬퍼
│   ├── document/                         # EmergingTech, ConversationSession, ConversationMessage, ExceptionLog, TechGraphNode, TechGraphEdge
│   ├── enums/                            # EmergingTechType, PostStatus, SourceType, TechProvider, GraphNodeType, GraphRelationType
│   ├── key/                              # GraphKeys — 그래프 노드·엣지 키 생성 규칙
│   ├── repository/                       # 앞 4개 컬렉션의 MongoRepository
│   ├── service/                          # EmergingTechAggregationService, TechGraphReader + projection DTO
│   └── util/                             # VectorSearchOptions, VectorSearchUtil
└── resources/
    └── application-mongodb-domain.yml    # MongoDB Atlas 연결 설정
```

## 도큐먼트

컬렉션은 여섯 개입니다. 모두 MongoDB `ObjectId`를 `@Id`로 쓰고, Aurora의 TSID는 별도 문자열 필드로 보관합니다.

### EmergingTechDocument (`emerging_techs`)

AI/기술 업데이트 정보를 담는 핵심 컬렉션입니다.

- `provider`, `update_type`, `source_type`, `status`: 각각 `TechProvider`, `EmergingTechType`, `SourceType`, `PostStatus` enum의 문자열 값
- `title`, `summary`, `url`, `published_at`
- `external_id`: 중복 수집 방지용 외부 식별자(GitHub release ID 등). UNIQUE
- `embedding_text`, `embedding_vector`: 임베딩 텍스트와 1536차원 벡터(`List<Float>`, OpenAI `text-embedding-3-small` 기준)
- `metadata`: 중첩 객체(`version`, `tags`, `author`, `github_repo`, `additional_info`)
- 감사 필드: `created_at`/`updated_at`/`created_by`/`updated_by`

### ConversationSessionDocument (`conversation_sessions`)

`session_id`(Aurora TSID를 문자열로, UNIQUE), `user_id`, `title`, `last_message_at`, `is_active`, 타임스탬프.

### ConversationMessageDocument (`conversation_messages`)

`message_id`(Aurora TSID를 문자열로, UNIQUE), `session_id`, `role`(USER/ASSISTANT/SYSTEM), `content`, `token_count`, `sequence_number`, `created_at`.

### ExceptionLogDocument (`exception_logs`)

읽기/쓰기 예외 기록. `source`, `exception_type`, `exception_message`, `stack_trace`, `severity`, `occurred_at`, 그리고 중첩 `context`(`module`, `method`, `parameters`, `user_id`, `request_id`).

### TechGraphNodeDocument (`tech_graph_nodes`) · TechGraphEdgeDocument (`tech_graph_edges`)

`emerging_techs` 문서에서 뽑아낸 지식 그래프입니다. `batch-graph`가 채우고 `api-chatbot`이 읽습니다.

노드는 `key`(UNIQUE), `type`(`GraphNodeType` 라벨), `name`(처음 저장될 때 본 원본 표기), `external_ids`(이 노드가 나온 `emerging_techs` 문서들), 타임스탬프를 갖습니다. 엣지는 `key`(UNIQUE), `type`(`GraphRelationType` 라벨), `source_key`, `target_key`, `external_ids`, 타임스탬프를 갖습니다.

타입은 미리 정해둔 목록으로만 저장합니다.

| enum | 값 |
|------|-----|
| `GraphNodeType` | `Company`, `Model`, `Technology`, `Release`, `Capability` |
| `GraphRelationType` | `RELEASED`, `SUCCEEDS`, `SUPPORTS`, `USES`, `DEPENDS_ON` |

`key`는 upsert가 같은 대상을 다시 찾는 유일한 수단입니다. 같은 모델이 문서마다 다른 표기로 나와도 같은 키가 나와야 재실행에서 노드가 늘지 않습니다. 규칙은 `GraphKeys`에 있고, 그래프를 만드는 배치와 읽는 챗봇이 이 클래스를 함께 씁니다.

- 노드 키: `타입 라벨 + "|" + 정규화한 이름` — 예: `Model|gpt-4o`
- 엣지 키: `출발 키 + "->" + 관계 라벨 + "->" + 도착 키` — 예: `Company|openai->RELEASED->Model|gpt-4o`
- 이름 정규화: 앞뒤 공백 제거, 연속 공백 한 칸으로 축약, 소문자 변환

이 두 컬렉션의 `key` unique 인덱스는 아래 `MongoIndexConfig`가 만들지 않고 **`batch-graph`의 잡이 시작할 때** 만듭니다. 컨텍스트만 띄워도 운영 Atlas에 쓰기가 나가는 것을 막기 위해서입니다.

## 리포지토리

모두 `MongoRepository<문서, ObjectId>`를 상속하는 Spring Data MongoDB 인터페이스입니다.

- `EmergingTechRepository`: `findByExternalId`·`findByUrl`(중복 체크), `findByTitleContainingIgnoreCase`. 필터 조합 쿼리는 `MongoTemplate` 동적 Criteria로 처리.
- `ConversationSessionRepository`: `findBySessionId`, `findByUserIdOrderByLastMessageAtDesc` 등
- `ConversationMessageRepository`: `findByMessageId`, `@Query`로 `session_id` 기준 `sequence_number` 정렬 조회
- `ExceptionLogRepository`: source/type/시각 기준 조회

## 집계 서비스

`EmergingTechAggregationService`는 통계를 DB 안에서 처리해 전송량을 줄입니다(`MongoTemplate` Aggregation Pipeline).

- `countByGroup`: `provider`/`source_type`/`update_type` 등 기준 필드별 도큐먼트 수
- `aggregateWordFrequency`: `title + summary`를 서버에서 `$split`/`$unwind`/`$group`으로 토큰화·집계해 상위 N개 단어 빈도를 구함(불용어·2글자 미만·숫자 토큰 제외)
- `countDocuments`: 선택적 필터 + 기간으로 도큐먼트 수 조회

날짜 조건은 `published_at`이 `null`인 도큐먼트도 누락하지 않도록 `$or`로 묶고, 결과는 `GroupCountResult`/`WordFrequencyResult` projection DTO로 매핑합니다.

## 연결 설정 (MongoClientConfig)

`AbstractMongoClientConfiguration`을 상속하고 `@EnableMongoRepositories`로 리포지토리를 활성화합니다.

- **커넥션 풀**: 최대 100, 최소 10 (Atlas 티어에 맞춰 조정)
- **타임아웃**: connect 10초, read 30초, heartbeat 10초
- **ReadPreference** `secondaryPreferred` — 읽기 복제본 우선, 최종 일관성 허용
- **WriteConcern** `majority` + 5초 wtimeout — 동기화 데이터 일관성
- **재시도** `retryWrites`/`retryReads` on, **Stable API** V1

연결 문자열·DB 이름은 환경변수로 받습니다.

| 환경변수 | 설명 | 기본값 |
|----------|------|--------|
| `MONGODB_ATLAS_CONNECTION_STRING` | Atlas 연결 문자열(`mongodb+srv://...`) | (필수) |
| `MONGODB_ATLAS_DATABASE` | 데이터베이스 이름 | `tech_n_ai` |

`local`/`dev`/`prod` 모두 Atlas에 연결합니다. 로컬 Docker MongoDB를 쓰려면 URI만 바꾸면 됩니다.

## 인덱스 (MongoIndexConfig)

`@PostConstruct`에서 드라이버의 `createIndex()`를 직접 호출합니다. 멱등하므로 이미 있으면 건너뜁니다. 복합 인덱스는 ESR(Equality → Sort → Range) 순서로 설계했습니다.

| 컬렉션 | 인덱스 | 종류 |
|--------|--------|------|
| `exception_logs` | `source` + `occurred_at(desc)` | 복합 |
| `exception_logs` | `exception_type` + `occurred_at(desc)` | 복합 |
| `exception_logs` | `occurred_at` | TTL 90일 |
| `conversation_sessions` | `user_id` + `is_active` + `last_message_at(desc)` | 복합 |
| `conversation_sessions` | `last_message_at` | TTL 90일 |
| `conversation_messages` | `session_id` + `sequence_number` | 복합 |
| `conversation_messages` | `created_at` | TTL 365일 |
| `emerging_techs` | `url` | UNIQUE |
| `emerging_techs` | `provider` + `published_at(desc)` | 복합 |
| `emerging_techs` | `status` + `published_at(desc)` | 복합 |
| `emerging_techs` | `update_type` + `published_at(desc)` | 복합 |

## Vector Search (RAG 챗봇)

벡터 검색 인덱스는 API가 다릅니다(`createSearchIndexes`). MongoDB Atlas에서만 동작하며, 비-Atlas 환경에서는 경고 로그만 남기고 건너뜁니다.

- **인덱스** `vector_index_emerging_techs`: `embedding_vector`(1536차원, `cosine`) + 필터 필드 `provider`/`status`/`published_at`/`update_type`
- `MongoIndexConfig`가 시작 시 존재 여부를 확인하고, 정의가 바뀌었으면 `updateSearchIndex`로 갱신합니다.
- `VectorSearchIndexConfig`는 인덱스 정의 JSON 상수와 Atlas CLI 생성 명령 헬퍼(수동 생성용)를 제공합니다.

### 검색 파이프라인 유틸 (VectorSearchUtil)

`$vectorSearch` 파이프라인을 만드는 유틸리티입니다. **실제 실행은 `api-chatbot` 등 사용하는 서비스가 `MongoTemplate`으로 수행합니다.**

- `createEmergingTechSearchPipeline`: `emerging_techs` 검색. `status: "PUBLISHED"` pre-filter 기본 적용
- `createBookmarkSearchPipeline`: `bookmarks` 검색에 `user_id` 필터 적용(사용자별 격리)
- `createEmergingTechSearchPipelineWithFusion`: **Score Fusion** — 벡터 유사도와 최신성 점수를 가중 결합. 최신성은 `published_at` 기준 지수 감쇠(`e^(-λ × 경과일수)`)로 계산하고 미래 날짜의 음수는 0으로 막음

옵션은 `VectorSearchOptions`(빌더)로 전달합니다: `numCandidates`, `limit`, `minScore`, `filter`, `exact`(ANN/ENN), Score Fusion용 `vectorWeight`/`recencyWeight`/`recencyDecayLambda`.

## 그래프 조회 (TechGraphReader)

`api-chatbot`이 질문에서 만든 노드 키 후보로 시드 노드를 찾고, 엣지를 한 번 타서 이웃(1홉)까지 가져옵니다. `findMatches()` 하나이며 aggregation 한 번으로 끝냅니다(`$lookup`에 localField/foreignField와 pipeline을 함께 쓰는 문법은 MongoDB 5.0부터입니다).

- 결과는 시드(0홉)와 이웃(1홉)을 합친 `GraphNodeMatch` 목록입니다. 같은 키가 양쪽에 나오면 홉이 작은 쪽만 남깁니다.
- `maxSeeds`·`maxEdgesPerSeed`·`maxTimeMs`로 조회 범위와 서버 실행 시간을 제한합니다.
- `Company` 노드는 홉을 넓힐 때 뺍니다. `Company|openai` 하나가 문서 수백 건을 가리키는 허브라 확장에 쓰면 아무 문서나 딸려 옵니다. 0홉으로 직접 맞은 경우는 그대로 둡니다.
- 사용자 입력은 `$in` 배열과 이스케이프한 정규식 값 자리에만 들어갑니다. 정규식 이스케이프에 `Pattern.quote()`를 쓰지 않는데, `\Q...\E` 인용은 리터럴 안에 `\E`가 들어오면 그 자리에서 끊기기 때문입니다.

읽기 전용이라 이 클래스는 그래프 컬렉션에 쓰지 않습니다. 채우는 쪽은 `batch-graph`입니다.

## CQRS에서의 위치

Query 쪽입니다. Aurora(Command)의 쓰기가 Kafka 이벤트로 전파되어 여기 컬렉션에 반영됩니다(목표 지연 1초 이내, Redis 멱등 처리). Aurora와의 매핑은 TSID를 문자열로 보관하는 필드로 이뤄집니다.

- `ConversationSessionDocument.session_id` ↔ Aurora `conversation_sessions.session_id`
- `ConversationMessageDocument.message_id` ↔ Aurora `conversation_messages.message_id`

`bookmarks`/`emerging_techs`는 각 서비스의 동기화·수집 경로를 따릅니다. 이벤트 발행·소비는 API 서비스와 `common-kafka`가 담당합니다. `tech_graph_*`는 Aurora에 대응하는 테이블이 없고 `batch-graph`가 `emerging_techs`에서 파생시켜 만듭니다.

## 의존성

`common-core`와 `spring-boot-starter-data-mongodb`만 씁니다. 리액티브 MongoDB는 쓰지 않고 동기 드라이버만 사용합니다.

## 참고

- [Spring Data MongoDB](https://docs.spring.io/spring-data/mongodb/reference/) · [MongoDB Java Driver (sync)](https://www.mongodb.com/docs/drivers/java/sync/current/)
- [Atlas Vector Search](https://www.mongodb.com/docs/atlas/atlas-vector-search/) · [`$vectorSearch` stage](https://www.mongodb.com/docs/atlas/atlas-vector-search/vector-search-stage/) · [인덱스 가이드](https://www.mongodb.com/docs/drivers/java/sync/current/fundamentals/indexes/)
