# langchain4j-embeddings-bge-small-en-v15 기여 후보 분석

## 사전 확인 (재탐색 금지 각도 — 직접 재확인만 수행)

- **base 로직**: `BgeSmallEnV15EmbeddingModel.java`(59줄)·`BgeSmallEnV15EmbeddingModelFactory.java`(12줄)는
  전량 `AbstractInProcessEmbeddingModel`/`OnnxBertBiEncoder`(base 모듈)에 위임하는 thin delegator. 분기·계산·
  URL 구성이 없어 wrong-output/logic-error가 물리적으로 존재할 수 없음을 직접 코드로 재확인(사전 요약과 일치).
- **생성자 Javadoc "Threads are cached for 1 second."**: 두 생성자 Javadoc(line 32-39, 41-48) 모두 문장 보유,
  형제 9개와 동일. 정상.
- **IT 테스트명-동작 일치**: `should_embed_text_longer_than_510_tokens_by_splitting_and_averaging_embeddings_of_splits`
  이름으로 이미 정착된 이름을 쓰고 있어(all-minilm-l6-v2류 "should_fail_to_embed_511..." 같은 이름-동작 불일치 없음) 정상.
- **instruction-prefix Javadoc·Factory·SPI·null가드**: "Represent this sentence for searching relevant passages:"
  문구, `BgeSmallEnV15EmbeddingModelFactory.create()`, `META-INF/services/...EmbeddingModelFactory`(FQCN
  `dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModelFactory`와 정확히 일치),
  생성자 `ensureNotNull(executor, "executor")` 가드 전부 직접 재확인 — 형제 6개와 동일, 정상.
- **sha256**: `pom.xml`의 `828e1496d7fabb79cfa4dcd84fa38625c0d3d21da474a00f08db0f559940cf35`는 정확히 64자
  (유효한 SHA-256 hex). 형제 9개 pom.xml과 구조 동일, URL·outputFileName만 모델별로 다름 — 정상.

위 5개 각도는 사전 요약대로 전부 "이미 정상"으로 재확인되어 새 후보가 없다. 대신 아래에서 사전 요약에
없던 새 각도(IT 테스트 파일의 형제 10곳 대비 메서드 커버리지 diff)로 실제 결함을 발견했다.

---

## 기여 후보 #1

**후보 유형**: api-contract
**변경 유형**: non-typo (기존 assertion 변경 없이 새 `@Test` 메서드 1개 추가 — 동작 자체는 불변이지만 검증 로직이 새로 생기므로 "철자·표현 교정"이 아님)

### 요약
`BgeSmallEnV15EmbeddingModelIT`에 `EmbeddingModel.embedAll(List<TextSegment>)`과 그 `TokenUsage` 반환값을
검증하는 `should_embed_multiple_segments()` 테스트가 없다. 같은 `langchain4j-embeddings-*` 형제 wrapper
10개 중 8개(`bge-small-en`, `bge-small-en-q`, `bge-small-zh-v15`, `bge-small-zh-v15-q`, `all-minilm-l6-v2`,
`all-minilm-l6-v2-q`, `e5-small-v2`, `e5-small-v2-q`)는 전부 이 테스트를 갖고 있고, `bge-small-en-v15`와
`bge-small-en-v15-q` 두 곳만 없다(모듈 스코프상 `bge-small-en-v15`만 수정 대상).

### 근거
- **코드 위치**:
  - `BgeSmallEnV15EmbeddingModelIT`(`embeddings/langchain4j-embeddings-bge-small-en-v15/src/test/java/dev/langchain4j/model/embedding/onnx/bgesmallenv15/BgeSmallEnV15EmbeddingModelIT.java`, 91줄 전체) — `embedAll()`을 호출하는 테스트가 하나도 없다.
- **문제 증거**:
  - `grep -c should_embed_multiple_segments`를 형제 wrapper 10개 IT 파일 전체에 돌린 결과, `bge-small-en-v15`와
    `bge-small-en-v15-q`만 0, 나머지 8개는 전부 1.
  - `git log --diff-filter=A`로 확인한 결과 `BgeSmallEnV15EmbeddingModelIT.java`는 마이그레이션 커밋
    `c3a491e0b`("Migrate in-process embedding modules into main repo (#4179)")에서 현재 형태 그대로 유입됐고,
    `should_embed_multiple_segments`는 같은 커밋에서 `bge-small-en` 등 8개 형제에는 포함됐지만 이 모듈에는
    처음부터 빠져 있었다(그 뒤로도 추가된 적 없음) — 리팩터링 중 실수로 누락된 것이 아니라 이관 시점부터
    형제 8개와 다른 파일이었다.
  - `AbstractInProcessEmbeddingModel.embedAll()`(`embeddings/langchain4j-embeddings/src/main/java/dev/langchain4j/model/embedding/onnx/AbstractInProcessEmbeddingModel.java:81,90`)은
    `EmbeddingModel` 인터페이스의 공개 계약(여러 `TextSegment`를 한 번에 임베딩하고 합산 `TokenUsage`를
    반환)을 구현한다 — 이 계약은 실제 RAG 인제스천 파이프라인에서 흔히 쓰이는 정상 경로이며, 형제 8곳은
    자기 모델 전용 토크나이저로 이 계약을 검증하는데 `bge-small-en-v15`만 검증하지 않는다.
  - **assertion 값 사전 검증**: 형제 8곳은 전부 입력 `"hi"`/`"hello"`(중국어 모델 제외)에 대해
    `tokenUsage.inputTokenCount()`가 정확히 `2`임을 검증한다(`bge-small-en`, `bge-small-en-q`,
    `all-minilm-l6-v2` 3개 서로 다른 토크나이저 모두 동일 값). 이 모듈의 토크나이저 파일
    `bge-small-en-v1.5-tokenizer.json`을 직접 파싱해 확인한 결과 `"hi"`(vocab id 7632)와 `"hello"`(vocab id
    7592)가 각각 단일 subword로 vocab에 존재한다(분할 불필요). `AbstractInProcessEmbeddingModel.java:65,81`의
    `tokenCount - 2`(`[CLS]`/`[SEP]` 특수 토큰 제외) 로직과 결합하면 `"hi"`→1토큰, `"hello"`→1토큰,
    합산 `inputTokenCount = 2`가 된다 — 형제 8곳과 동일한 값이 나올 것임을 코드·vocab으로 직접 확인했다
    (실제 IT 실행은 로컬 정책상 생략, 대신 정적 근거로 대체).
- **비교 대상**:
  - `BgeSmallEnEmbeddingModelIT.should_embed_multiple_segments()`(`embeddings/langchain4j-embeddings-bge-small-en/src/test/java/dev/langchain4j/model/embedding/onnx/bgesmallen/BgeSmallEnEmbeddingModelIT.java:37-58`):
    동일 모델 계열(BGE, CLS 풀링)의 정답 패턴. 입력 `"hi"`/`"hello"`, `inputTokenCount()==2` 검증.
  - `BgeSmallZhV15EmbeddingModelIT.should_embed_multiple_segments()`: 같은 "v15" 접미사 형제(중국어 모델),
    입력만 자국어로 바뀌고 구조는 동일.

### 현재 상태 분석
- **문제 발생 위치**:
  - `BgeSmallEnV15EmbeddingModelIT` 클래스 전체(line 14-91) — `should_embed()` 다음, `embedding_should_have_the_same_values...()` 이전 위치에 형제 8곳 모두 이 테스트가 존재한다.
- **현재 구현의 한계**:
  - `embedAll(List<TextSegment>)`과 `TokenUsage` 계산이 이 모델 전용 토크나이저 기준으로는 CI에서 전혀
    검증되지 않는다(형제 8곳은 검증됨).
  - 형제 10개 wrapper 모듈의 테스트 커버리지 패턴이 깨져 레포 전체 일관성이 떨어진다.

**현재 구현 코드 (AS-IS)**:
```java
@Test
void should_embed() {

    EmbeddingModel model = new BgeSmallEnV15EmbeddingModel();

    Embedding first = model.embed("hi").content();
    assertThat(first.vector()).hasSize(384);

    Embedding second = model.embed("hello").content();
    assertThat(second.vector()).hasSize(384);

    double cosineSimilarity = CosineSimilarity.between(first, second);
    assertThat(RelevanceScore.fromCosineSimilarity(cosineSimilarity)).isGreaterThan(0.96);
}

// ↑ 바로 다음에 와야 할 should_embed_multiple_segments()가 형제 8곳과 달리 존재하지 않음

@Test
void embedding_should_have_the_same_values_as_embedding_produced_by_sentence_transformers_python_lib() {
```

### 제안되는 기여 범위
- **수정 대상 코드 범위**:
  - `BgeSmallEnV15EmbeddingModelIT.java`에 `@Test` 메서드 1개, import 4개 추가만(`should_embed()`와
    `embedding_should_have_the_same_values_as...()` 사이).
- **수정 내용**:
  - `import static java.util.Arrays.asList;`, `dev.langchain4j.data.segment.TextSegment`,
    `dev.langchain4j.model.output.Response`, `dev.langchain4j.model.output.TokenUsage`, `java.util.List` 추가.
  - `bge-small-en`의 `should_embed_multiple_segments()`를 입력값 그대로("hi"/"hello", `inputTokenCount==2`)
    이식(같은 CLS 풀링·유사 vocab 구조이므로 값도 동일).

**개선된 구현 코드 (TO-BE)**:
```java
@Test
void should_embed_multiple_segments() {

    EmbeddingModel model = new BgeSmallEnV15EmbeddingModel();
    TextSegment first = TextSegment.from("hi");
    TextSegment second = TextSegment.from("hello");

    Response<List<Embedding>> response = model.embedAll(asList(first, second));

    List<Embedding> embeddings = response.content();
    assertThat(embeddings).hasSize(2);

    assertThat(embeddings.get(0)).isEqualTo(model.embed(first).content());
    assertThat(embeddings.get(1)).isEqualTo(model.embed(second).content());

    TokenUsage tokenUsage = response.tokenUsage();
    assertThat(tokenUsage.inputTokenCount()).isEqualTo(2);
    assertThat(tokenUsage.outputTokenCount()).isNull();
    assertThat(tokenUsage.totalTokenCount()).isEqualTo(2);

    assertThat(response.finishReason()).isNull();
}
```

**개선 사항**:
1. `embedAll()`/`TokenUsage` 계약이 이 모델 전용 토크나이저로도 CI에서 검증된다.
2. 형제 wrapper 10개(base 포함 11개) 중 이 모듈만 비어 있던 테스트 커버리지 구멍이 메워진다.

### 리뷰 가치 평가
- **왜 유지보수자가 리뷰할 만한지**:
  - 형제 8곳과 코드가 사실상 동일(입력값·assertion 패턴 100% 일치)해 diff가 자명하고, 값도 vocab 파일과
    소스코드로 사전 검증됨(추측이 아님).
  - 마이그레이션 커밋(#4179) 시점부터 존재한 오래된 커버리지 갭이며, 최근 회귀가 아니라 "형제 패턴을 다시
    맞추는" 저위험 변경이다.
- **왜 과도한 변경이 아닌지**:
  - 기존 테스트·프로덕션 코드는 1바이트도 건드리지 않고 새 `@Test` 메서드 1개만 추가한다.
  - `small_focused_pr` 원칙에 부합(파일 1개, 순수 추가).

---

## 제외된 후보 (탐색 중 기각)

1. **`bge-small-en-v15-q`의 동일 갭**: `should_embed_multiple_segments` 부재를 동일하게 확인했으나 이는
   `langchain4j-embeddings-bge-small-en-v15-q`라는 별도 모듈로, 이번 태스크의 모듈 스코프(`bge-small-en-v15`)
   밖이라 후보에서 제외(다음 run에서 별도 스코프로 다룰 사안).
2. **base 모듈 open issue #1579/#1655**: 사전 요약대로 `AbstractInProcessEmbeddingModel.loadFromJar()`/
   `OnnxBertBiEncoder.loadModel()`(base 모듈) 원인이고 이 wrapper 모듈 코드 자체의 결함이 아니어서 재확인 없이 제외.
3. **prefer_classes 고가치 클래스 소진 확인(exhausted-higher)**: wrong-output/logic-error/dead-ref-docs는
   thin delegator 구조상 물리적으로 존재 불가(위 "사전 확인" 섹션에서 직접 재검증). github-backed는 gh
   검색(`gh search issues`, `gh search prs`, `gh issue list --search "bge-small-en-v15"`) 결과 이 테스트
   갭이나 이 모듈 코드를 겨냥한 open issue/PR 0건. api-contract(#1)를 채택해 NPE-guard보다 우선함.

## 점수표

| 후보 | 유형 | 명확성 | 영향 | 머지용이성 | 테스트가능성 | 리스크낮음 | 합계 | 판정 |
|---|---|---|---|---|---|---|---|---|
| #1 `should_embed_multiple_segments()` 테스트 커버리지 누락(형제 8/10 대비) | api-contract/non-typo | 5 | 3 | 5 | 3 | 5 | 21/25 | 추천 |

- **명확성 5**: `grep -c` 전수조사 + `git log --diff-filter=A`로 마이그레이션 시점부터의 누락을 직접 확인.
- **영향 3**: `embedAll()`은 정상 RAG 인제스천 경로에서 실사용되는 공개 API이지만, 내부 로직은 base
  모듈에 위임되어 이미 형제 8곳+base 모듈 테스트로 간접 검증됨 — "이 모델 특유의 실제 버그"가 숨어 있을
  가능성은 낮고, 테스트 커버리지 정합성 문제에 가깝다(NPE-guard는 아니므로 ≤2 강제 규정은 적용 안 됨).
- **테스트가능성 3**: 제안 자체가 `.onnx` 모델이 필요한 `*IT` 테스트 추가라 로컬 단위테스트로 즉시
  실행 검증은 불가(config상 IT 로컬 실행 금지). 대신 vocab 파일 파싱 + 소스코드(`tokenCount - 2` 특수토큰
  제외 로직) 정적 분석으로 assertion 값(`inputTokenCount==2`)을 사전 검증해 근거 신뢰도를 최대한 높였다.
- **머지용이성 5 / 리스크낮음 5**: 파일 1개, 기존 코드 변경 없이 형제 패턴 그대로 추가하는 순수 신규
  테스트 — 프로덕션 코드·기존 assertion에 영향 없음.

---
## 검증 (candidate-reviewer)
- **판정**: GO
- **근거**:
  1. 신규 통합/breaking change: 해당 없음 — 기존 모듈에 `@Test` 1개 순추가, 프로덕션 코드·기존 assertion 무변경. 실제 파일(`BgeSmallEnV15EmbeddingModelIT.java`, 91줄) 직접 열람으로 확인.
  2. **테스트 가능성**: `*IT`라 로컬 실행은 config(`integration_tests: skip`) 정책상 불가 — 이 점은 사실이며 CAUTION 요소이나, 다음 근거로 위험이 충분히 완화됨을 확인:
     - `AbstractInProcessEmbeddingModel.java:65,81`의 `tokenCount - 2`(CLS/SEP 제외) 로직을 직접 읽어 확인. `embedAll()`이 `TokenUsage(Integer inputTokenCount)` 단일 인자 생성자를 쓰는데, `TokenUsage.java:28-30,136-141`을 확인한 결과 `outputTokenCount=null`, `totalTokenCount=sum(input,null)=input`이 되어 후보가 제안한 `outputTokenCount().isNull()` / `totalTokenCount()==2` assertion과 정확히 일치함을 코드로 검증.
     - `bge-small-en-v1.5-tokenizer.json`을 직접 파싱(`python3 json.load`)해 `"hi"→vocab id 7632`, `"hello"→vocab id 7592`가 각각 접두사 없는 완전한 단일 서브워드로 vocab에 존재함을 확인 — WordPiece는 전체 단어가 vocab에 있으면 그대로 1토큰 매치하므로 분할되지 않는다. `tokenCount-2` 로직과 결합하면 `inputTokenCount==2`가 산술적으로 확정된다(추정이 아님).
     - `git log --oneline --diff-filter=A`로 `BgeSmallEnV15EmbeddingModelIT.java`가 `c3a491e0b`("Migrate in-process embedding modules into main repo (#4179)")에서 유입된 것을 확인. 형제 8개 IT 파일에 대해 `grep -c should_embed_multiple_segments`를 직접 재실행한 결과 `all-minilm-l6-v2(-q)`, `bge-small-en(-q)`, `bge-small-zh-v15(-q)`, `e5-small-v2(-q)` 8개는 전부 1, `bge-small-en-v15`/`bge-small-en-v15-q`만 0 — 후보 주장과 정확히 일치. 이 동일 코드는 이미 병합되어 메인 코드베이스에 존재하는 형제 8개 모듈에서 동일 베이스 클래스(`AbstractInProcessEmbeddingModel`)로 CI 통과 중인 패턴이라 실전 검증 전례가 있다(단, "최근 별도 PR에서 병합"이 아니라 마이그레이션 커밋에 원래부터 포함된 코드라는 차이는 있음).
     - `BgeSmallEnEmbeddingModelIT.should_embed_multiple_segments()`(line 36-57)를 직접 읽어 후보가 인용한 정답 패턴과 제안 코드가 입력값·assertion 구조까지 100% 일치함을 확인.
     - 종합: 로컬 미실행은 이 파이프라인의 모든 `*IT` 후보에 공통되는 정책적 제약이지 이 후보 특유의 결함이 아니며, 산술적으로 확정 가능한 값(추정 아님) + 8개 형제의 기존 CI 통과 전례로 실질 위험은 낮다고 판단해 CAUTION으로 격하하지 않음.
  3. 새 의존성: 없음 — 제안 import 4개(`java.util.Arrays.asList`, `TextSegment`, `Response`, `TokenUsage`, `List`) 전부 기존 프로젝트 클래스, `bge-small-en` 형제 파일의 import 목록과 대조 일치 확인.
  4. 고감도 영역: 미해당 — `sensitive_areas`(core ChatModel/AiServices/bom/root pom) 어디에도 속하지 않음.
  5. 변경 크기: 파일 1개 직접 열람 재확인 — 제안 위치(`should_embed()` 다음, `embedding_should_have_the_same_values...()` 이전)가 형제 파일(`bge-small-en`)의 실제 메서드 순서(`should_embed` → `should_embed_multiple_segments` → `embedding_should_have_the_same_values...`)와 정확히 일치. 메서드 1개 순추가만 확인.
  6. **중복**: `gh search prs`, `gh search issues`, `gh pr list --search "bge-small-en-v15"`를 직접 재실행 — "bge-small-en-v15"/"bgesmallenv15" 관련 열린 이슈·PR 0건(무관한 결과만 나옴: BOM 자동생성, all-minilm 리네임 PR #5700 등). 후보 주장과 일치, 중복 없음.
  7. **series probe-first**: `bge-small-en-v15-q`도 grep 결과 0 — 동일 갭이 실재함을 직접 재확인. 이번 건이 시리즈의 첫 제출이므로 probe-first 적용 — 병합 확인 전까지 `bge-small-en-v15-q`는 HOLD.
  8. NPE 과편향 가드: 해당 없음(api-contract 유형이지 null-guard 패턴이 아님) — `candidate_quality.npe_guard_is_fallback` 규정과 무관.
- **CAUTION 시 필수 수정**: 해당 없음(GO 판정).
- **probe-first**: HOLD `langchain4j-embeddings-bge-small-en-v15-q` — 동일한 `should_embed_multiple_segments()` 부재가 확인되었으나 이번 PR이 머지되는지 확인 후 후속 run에서 별도로 다룰 것.

## 마감 (contrib-validate): VALID — 제출됨 issue=(없음, test 유형 PR-only) pr=https://github.com/langchain4j/langchain4j/pull/5724
