# 단계 03 — GraphRAG: 신기술 데이터를 지식 그래프로

## 한 줄 요약

수집한 신기술 문서에서 엔티티와 관계를 뽑아 그래프로 저장하고, 벡터 검색이 못 푸는
다중 홉 질문에 답한다.

## 왜 이 단계인가

지금 검색은 임베딩 유사도 기반이다. 유사도는 **비슷한 문서**를 찾아주지만
**연결된 사실**을 찾아주지 못한다. 이 차이가 드러나는 질문이 이 저장소의 도메인에 실제로 있다.

> "Anthropic이 최근 6개월에 낸 모델들이, 같은 기간 OpenAI 릴리스와 어떤 기능에서 겹치나?"

이 질문에 답하려면 (1) Anthropic의 릴리스를 기간으로 추리고 (2) 각 릴리스의 기능을
꺼내고 (3) OpenAI 쪽에서 같은 일을 하고 (4) 둘을 대조해야 한다. 네 단계를 건너뛰며
관계를 따라가는 작업인데, top-k 유사 문서로는 어느 단계도 제대로 되지 않는다.

그리고 `api-emerging-tech`가 다루는 데이터는 원래 **회사 – 모델 – 기술 – 릴리스 – 시점**이라는
관계 구조다. 억지로 그래프를 씌우는 게 아니라, 원래 그래프인 것을 문서로 눌러 담고
있었던 쪽에 가깝다. GraphRAG가 실제로 이기는 조건에 맞는다.

## 현재 코드 상태 — 확인된 사실

**그래프 DB는 없다.** `build.gradle`과 설정 파일 전체에서 neo4j 관련 의존성이나
설정이 검색되지 않는다.

**수집 경로**: `batch/source`의 Spring Batch 잡 3종(GitHub Release·RSS·웹 스크래핑)이
수집하고, `api-emerging-tech`의 내부 API를 거쳐 MongoDB에 저장한다.

**검색 경로**: `api-chatbot`의 `VectorSearchServiceImpl`이 MongoDB Atlas Vector Search로
`emerging_techs` 단일 컬렉션을 조회한다. 결과는 `ResultRefinementChain`에서
중복 제거 → (활성화 시) Cohere 재순위 → recency 가중 순으로 정제된다.

**합류 지점이 이미 있다.** `ChatbotServiceImpl.handleRAGPipeline()`은
`vectorSearchService.search(...)` → `refinementChain.refine(...)` → `answerChain.generate(...)`
순서다. 그래프 검색 결과를 여기 끼워 넣는 자리가 자연스럽게 존재한다.

## 쓸 수 있는 것 — 공식 문서 확인 결과 (2026-08-12)

`docs.langchain4j.dev/integrations/embedding-stores/neo4j` 기준.

| 클래스 | 역할 |
|--------|------|
| `LLMGraphTransformer` | 비정형 문서를 노드와 엣지 집합으로 변환. **DB 비종속** — 산출물을 Neo4j 외의 그래프 저장소에도 쓸 수 있다 |
| `KnowledgeGraphWriter` | `LLMGraphTransformer`의 산출물을 Neo4j 노드·관계로 저장 |
| `Neo4jText2CypherRetriever` | `ContentRetriever` 구현. 자연어 질문을 Cypher로 번역해 실행한다. 스키마는 `apoc.meta.data` 프로시저로 얻는다 |
| `Neo4jEmbeddingStore` | `EmbeddingStore` 구현. `dimension`과 드라이버 설정이 필수 |
| `Neo4jEmbeddingStoreIngestor` | 부모-자식 세그먼트 관계와 임베딩을 함께 저장하는 다단계 수집 파이프라인 |
| `Neo4jChatMemoryStore` | `ChatMemoryStore` 구현 |

아티팩트 (Maven Central 확인). **앞의 버전은 최신이고, 이 저장소가 지금 쓰는 1.10 라인에도
`1.10.0-beta18`이 있다** — 어느 쪽을 쓸지는 아래 제약·리스크의 "01단계는 선행이 아니다"를 보라.
- `dev.langchain4j:langchain4j-community-llm-graph-transformer` — 최신 1.18.0-beta28
- `dev.langchain4j:langchain4j-community-neo4j-retriever` — 최신 1.18.0-beta28
- `dev.langchain4j:langchain4j-community-neo4j` — 임베딩 스토어
- `dev.langchain4j:langchain4j-community-neo4j-spring-boot4-starter` — Boot 4용 starter도 배포됨

`Neo4jText2CypherRetriever`가 `ContentRetriever`를 구현한다는 점이 중요하다.
LangChain4j의 검색 추상화를 그대로 따르므로 기존 파이프라인에 끼우기 쉽다.

## 범위

### 포함

**(a) 그래프 스키마 정의 — 이 단계에서 가장 중요한 결정**
`LLMGraphTransformer`에 뽑을 노드 타입과 관계 타입을 제한해서 준다. 제한하지 않으면
LLM이 문서마다 제각각인 라벨을 만들어내고, 그래프가 아니라 라벨 쓰레기통이 된다.
초안: 노드 `Company`, `Model`, `Technology`, `Release`, `Capability` /
관계 `RELEASED`, `SUCCEEDS`, `SUPPORTS`, `COMPETES_WITH`, `DEPENDS_ON`.
실제 데이터를 보고 확정한다.

**(b) 그래프 구축 경로**
수집된 문서에서 엔티티·관계를 뽑아 그래프 DB에 넣는다. `batch/source`의 수집 잡 뒤에
붙이거나, 별도 잡으로 기존 MongoDB 문서를 훑어 채운다. **후자로 시작하는 편이 낫다** —
수집 잡을 건드리지 않고 되돌리기 쉽다.

**(c) 그래프 검색 경로**
`GraphSearchService`(가칭)를 `VectorSearchService` 옆에 두고, `handleRAGPipeline`에서
두 결과를 합친다. 합치는 방식은 열린 질문이다.

**(d) 다중 홉 질문에 대한 개선 측정**
02단계 골든셋의 "다중 홉" 유형이 이 단계의 대조군이다. 도입 전 기준선 대비
얼마나 올랐는지가 판정 근거다.

### 제외
- 기존 벡터 검색 경로 제거. 두 방식은 잘하는 질문 유형이 다르므로 병행한다.
- 그래프 시각화 화면.
- 사용자 대화 기록을 그래프로 옮기는 작업(`Neo4jChatMemoryStore`). 지금 저장소는
  대화를 Aurora + MongoDB에 저장하고 있고, 이걸 흔들 이유가 없다.
- 실시간 그래프 갱신. 배치로 시작한다.

## 후보 완료 기준

- [ ] 노드·관계 타입이 문서로 확정돼 있고, 그 타입만 추출되도록 제한이 걸려 있다.
- [ ] 기존 `emerging_techs` 문서에서 그래프를 채우는 배치가 동작하고, 재실행해도
      중복 노드가 생기지 않는다.
- [ ] 다중 홉 질문 예시 3건 이상이 **그래프 경로를 실제로 타서 근거를 돌려준다**(질문별로
      어느 경로를 탔는지와 반환된 근거를 기록). "답이 나왔다"가 아니라 그래프가 답에
      기여했는지로 본다 — 답의 품질 판정은 아래 항목이 맡는다.
- [ ] 02단계 골든셋의 다중 홉 유형에서 기준선 대비 지표가 개선됐다(수치로 제시).
- [ ] 벡터 검색만으로 잘 답하던 질문 유형에서 회귀가 없다.
- [ ] 그래프 DB 접속 정보가 코드에 없고 환경 변수로 주입된다.
- [ ] 그래프 검색에 쓰는 DB 계정이 읽기 전용이다.
- [ ] 영향 모듈 테스트가 그린이다.

## 진행 순서 초안

1. **데이터 실사.** `emerging_techs` 문서를 실제로 열어 어떤 엔티티가 반복해서
   나오는지 손으로 본다. 스키마를 상상으로 정하면 추출이 거의 다 실패한다.
   판정: 표본 20건에서 정의한 노드·관계 타입으로 대부분이 표현되는지 확인.
2. **추출 시험.** 문서 10~20건에 `LLMGraphTransformer`를 돌려 결과를 눈으로 검사한다.
   같은 회사가 다른 이름으로 여러 노드가 되는 문제(엔티티 정규화)가 여기서 드러난다.
   판정: 표본에서 중복 엔티티 비율과 잘못된 관계 비율을 세어 기록.
   **이때 문서당 토큰과 금액도 함께 기록한다** — 아래 판정 기준이 요구하는 전건 환산액이
   이 숫자에서 나오고, 어차피 도는 실행이라 따로 드는 일이 없다. 쓴 모델 이름도 같이 적는다.
3. **저장소 결정.** Neo4j를 띄울지, MongoDB `$graphLookup`으로 갈지 확정한다(열린 질문 1).
4. **전체 구축 배치.** 재실행 안전성(idempotency)을 갖춘다.
5. **검색 경로 연결.** 그래프 결과를 `handleRAGPipeline`에 합류시킨다.
6. **측정.** 02단계 잡을 돌려 기준선과 비교한다.

**5와 6 사이에 문제가 하나 있다 — 지금 그대로면 6이 5의 결과를 못 본다.**
`handleRAGPipeline`은 `ChatbotServiceImpl`의 private 메서드인데
(`api/chatbot/.../ChatbotServiceImpl.java:160`), 02의 평가 잡은 이 클래스를 올리지 않고
체인을 직접 이어 붙인다(`02-eval-observability.md:138-144`, `02a-batch-eval-module.md:115-123`).
즉 계획한 자리에 그래프를 합류시키면 평가 잡은 벡터 결과만 재고, 이 단계는 자기 완료 기준을
판정할 수치를 얻지 못한다.

**5를 하기 전에 합류 지점을 정한다.** ① 평가 잡이 태우는 `ResultRefinementChain` 앞에
합류시켜 잡이 그대로 볼 수 있게 하거나, ② `handleRAGPipeline`을 유지하되 평가 잡의 진입점을
그쪽으로 넓힌다(02의 범위가 늘어난다). ①이면 이 문서의 범위 안에서 끝나고, ②면 02와 함께
정해야 한다.

## 판정 기준

- **"그래프가 만들어졌다"는 성공이 아니다.** 다중 홉 질문의 답이 좋아져야 성공이다.
  노드 수·관계 수 같은 숫자를 성과로 제시하지 않는다.
- **품질·비용·지연을 같이 놓고 판단한다.** 04단계가 같은 자리에 건 것과 같은 규칙이다
  (`04-agentic-orchestration.md:179`). 이 단계는 비용이 가장 크므로 더 필요하다. 개선폭 옆에
  그래프 DB 상시 비용과 문서당 추출 호출 비용을 나란히 적는다. 진행 순서 2의 표본 20건
  추출에서 이미 도는 실행으로 전건 환산액이 나오므로 새 작업이 아니다.
- **개선폭이 잡음과 구분되는지 먼저 본다.** 02가 정한 방법을 그대로 쓴다 — 점 추정치만 보고
  판정하지 않고, 개선된 항목 수와 악화된 항목 수를 함께 적는다
  (`02-eval-observability.md:574-577`). 다중 홉 유형은 02가 최소 10건으로 잡은 축이라
  신뢰구간이 넓다. 항목 두어 건이 뒤집힌 것을 개선이라고 부르지 않는다.
- **미달이면 무엇을 하는지 먼저 정해 둔다.** 이 단계는 인프라가 하나 느는 일이라 되돌리는
  비용도 크다. 착수 전에 "이 정도 개선이면 유지하고, 이 밑이면 접는다"를 숫자로 적고
  `02-baseline.md` 옆에 남긴다. 끝나고 정하면 이미 쓴 비용이 판단을 흔든다.
- 추출 품질을 표본으로 사람이 확인한 기록이 있어야 한다. LLM이 뽑은 관계를
  검사 없이 신뢰하지 않는다.
- 벡터 경로 회귀 확인이 빠지면 미완으로 본다. 새 경로를 추가하면서 기존 경로의
  결과 순서가 바뀌는 일이 흔하다.

## 열린 질문 (P3 후보)

1. **Neo4j를 새로 띄울 것인가, MongoDB `$graphLookup`으로 갈 것인가.**
   이 단계에서 가장 비싼 결정이다.
   - Neo4j: LangChain4j 통합이 갖춰져 있고(`Neo4jText2CypherRetriever` 등) 그래프 질의가
     제대로 된다. 대신 인스턴스가 하나 늘고 `devops/terraform/`에 모듈을 추가해야 한다.
     `Neo4jText2CypherRetriever`는 스키마 조회에 APOC 플러그인(`apoc.meta.data`)을 요구하므로
     매니지드 서비스를 쓸 때 APOC 사용 가능 여부를 먼저 확인해야 한다.
   - MongoDB `$graphLookup`: 인프라가 안 늘고 이미 Atlas를 쓰고 있다. 대신 관계 질의
     표현력이 떨어지고 LangChain4j 통합이 없어 직접 만들어야 한다.
     `LLMGraphTransformer`는 DB 비종속이라 추출 부분은 어느 쪽이든 재사용된다.
   - 검증용으로 `$graphLookup`부터 해보고, 표현력이 부족하다는 게 확인되면 Neo4j로
     가는 순서도 가능하다.
2. **그래프 결과와 벡터 결과를 어떻게 합치나.** 별도 경로로 분기할지(질문 유형에 따라
   하나만 선택), 둘 다 돌려서 근거를 합칠지. 후자면 점수 체계가 다른 두 결과를
   어떻게 한 줄에 세울지 정해야 한다.
3. **Text2Cypher를 쓸 것인가, 정해둔 질의 템플릿을 쓸 것인가.** Text2Cypher는 유연하지만
   LLM이 만든 Cypher를 실행하는 것이므로 예측 불가능하다. 자주 나오는 질문 몇 개를
   템플릿으로 고정하는 쪽이 안전하고 빠르지만 범위가 좁다.
4. **추출을 언제 돌리나.** 수집 잡 안에 넣으면 문서마다 LLM 호출이 추가돼 수집이
   느려지고 비싸진다. 별도 잡으로 빼면 그래프가 최신이 아닌 시간대가 생긴다.
5. **엔티티 정규화를 어디까지 하나.** "OpenAI"와 "Open AI"와 "오픈AI"를 같은 노드로
   묶는 작업. 안 하면 그래프가 조각나고, 제대로 하면 이것만으로 작업이 하나 더 생긴다.

## 제약·리스크

- **비용이 이 계획에서 가장 크다.** 인프라(그래프 DB)와 LLM 호출(문서당 추출)이 둘 다 는다.
  02단계의 기준선 없이 이 단계를 하면 비용을 정당화할 근거가 없다.
- **Text2Cypher는 LLM이 만든 질의를 DB에서 실행한다.** 읽기 전용 계정을 쓰고, 질의
  타임아웃과 결과 개수 상한을 반드시 건다. 이건 열린 질문이 아니라 확정 사항이다.
- 관련 모듈이 전부 beta다(`-beta28`). API가 바뀔 수 있다.
- CQRS 구조상 그래프는 **읽기 쪽**에 속한다. Aurora(쓰기)를 건드리지 않는다.
  MongoDB 옆에 저장소가 하나 느는 형태여야 하고, 쓰기 경로에 그래프 갱신을 끼워 넣지 않는다.
- **01단계는 선행이 아니다.** `langchain4j-community-neo4j-retriever`와
  `langchain4j-community-llm-graph-transformer` 둘 다 `1.10.0-beta18`이 배포돼 있다
  (2026-08-15 `repo1.maven.org` 확인). 이 저장소가 이미 쓰는
  `langchain4j:1.10.0` + `langchain4j-mongodb-atlas:1.10.0-beta18` 짝과 같은 형태다
  (`api/chatbot/build.gradle:13,16,22`). 지금 라인에서 시험해 볼 수 있다.
  1.10 라인의 beta와 최신 라인의 API가 같다는 보장은 없으므로, 버전을 올릴 계획이 있으면
  1.10에서 만든 코드를 그대로 옮길 수 있는지는 따로 확인한다.

## 참고 (2026-08-12 확인)

- `https://docs.langchain4j.dev/integrations/embedding-stores/neo4j` — 클래스 목록과 역할,
  `Neo4jText2CypherRetriever`의 `apoc.meta.data` 의존
- `https://repo1.maven.org/maven2/dev/langchain4j/langchain4j-community-neo4j-retriever/maven-metadata.xml` — latest 1.18.0-beta28
- `https://repo1.maven.org/maven2/dev/langchain4j/langchain4j-community-llm-graph-transformer/maven-metadata.xml` — latest 1.18.0-beta28

## impl input 생성 힌트

- `pipeline/inputs/tasks/task-04-graphrag-schema-and-build.md` (스키마 + 구축 배치)
- `pipeline/inputs/tasks/task-05-graphrag-retrieval.md` (검색 경로 연결 + 측정)

**한 번에 하지 말고 둘로 쪼갤 것.** 스키마·추출 품질이 확인되기 전에 검색 경로를 만들면
헛일이 된다. 앞 작업의 완료 기준에 "표본 검사 기록"을 넣고, 뒤 작업은 그 결과를 전제로 쓴다.

저장소 선택(열린 질문 1)은 task를 쓰기 **전에** 결정해두는 게 좋다. 이게 안 정해지면
설계 승인 단계에서 작업 전체가 멈춘다.
