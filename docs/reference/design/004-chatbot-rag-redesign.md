# api-chatbot Emerging Tech 전용 RAG 검색 개선 설계서

## 1. 개요

### 1.1 목적

`api-chatbot` 모듈의 RAG(Retrieval-Augmented Generation) 파이프라인에서 벡터 검색 대상을 **`emerging_techs` 컬렉션 전용**으로 개선한다. 최신 기술 동향 관련 채팅 요청이 Emerging Tech 문서를 정확하게 벡터 검색하여 답변할 수 있도록 한다.

### 1.2 범위

| 구분 | 내용 |
|------|------|
| 대상 모듈 | `api-chatbot`, `domain-mongodb` |
| 변경 범위 | VectorSearchService, VectorSearchUtil, IntentClassificationService, InputInterpretationChain, PromptServiceImpl, SearchOptions |
| 제외 범위 | ChatbotServiceImpl(오케스트레이션 구조 유지), LangChain4jConfig, AnswerGenerationChain, ResultRefinementChain, 세션/메시지 관련 Service |

### 1.3 현재 구현 상태 요약

| 항목 | 상태 |
|------|------|
| `VectorSearchServiceImpl` | `emerging_techs` 컬렉션 전용 벡터 검색 구현 완료 |
| `IntentClassificationService` | RAG_KEYWORDS에 AI/기술 동향 키워드 포함, RAG 우선 라우팅 |
| `InputInterpretationChain` | `emerging_techs` 컬렉션 전용 (bookmark 컬렉션 제거) |
| `PromptServiceImpl` | Emerging Tech 메타데이터(provider, publishedAt, title, url) 포함 프롬프트 |
| `SearchOptions` | `includeEmergingTechs` 플래그 전용 (includeBookmarks 제거) |

---

## 2. 현재 구현 분석

### 2.1 RAG 파이프라인 분석

```
사용자 입력 → IntentClassificationService (의도 분류)
  ├─ AGENT_COMMAND → AgentDelegationService
  ├─ WEB_SEARCH_REQUIRED → WebSearchService
  ├─ RAG_REQUIRED → RAG Pipeline
  └─ LLM_DIRECT → LLMService 직접 호출

RAG Pipeline:
  InputInterpretationChain (입력 해석, 검색 컬렉션 결정)
  → VectorSearchService (벡터 검색 실행)
  → ResultRefinementChain (중복 제거, Re-Ranking)
  → AnswerGenerationChain (프롬프트 구성 + LLM 호출)
```

파이프라인 구조 자체는 적절하다. `ChatbotServiceImpl.handleRAGPipeline()`에서 `InputInterpretationChain` → `VectorSearchService` → `ResultRefinementChain` → `AnswerGenerationChain` 순서로 호출하는 흐름은 유지한다.

### 2.2 VectorSearchService 분석

**현재 코드** (`VectorSearchServiceImpl.java`):

```java
public List<SearchResult> search(String query, Long userId, SearchOptions options) {
    Embedding embedding = embeddingModel.embed(query).content();
    List<Float> queryVector = embedding.vectorAsList();
    List<SearchResult> results = new ArrayList<>();

    if (Boolean.TRUE.equals(options.includeBookmarks()) && userId != null) {
        results.addAll(searchBookmarks(queryVector, userId.toString(), options));
    }
    // emerging_techs 검색 로직 없음

    return results.stream()
        .sorted((a, b) -> Double.compare(b.score(), a.score()))
        .limit(options.maxResults())
        .collect(Collectors.toList());
}
```

**문제점**:
- `options.includeEmergingTechs()` 값과 무관하게 `emerging_techs` 검색이 수행되지 않음
- `searchBookmarks()` 메서드만 존재하고, `searchEmergingTechs()` 메서드가 없음
- 쿼리 임베딩 생성(`embeddingModel.embed()`)은 한 번만 호출되므로 재사용 가능 (적합)

### 2.3 IntentClassificationService 분석

**현재 RAG_KEYWORDS**:
```java
private static final Set<String> RAG_KEYWORDS = Set.of(
    "대회", "contest", "뉴스", "news", "기사", "북마크", "bookmark",
    "검색", "찾아", "알려", "정보", "어떤", "무엇",
    "kaggle", "codeforces", "leetcode", "hackathon"
);
```

**문제점**:
- AI/기술 관련 키워드가 부족: "AI", "인공지능", "LLM", "GPT", "모델" 등이 없음
- Provider 키워드 없음: "OpenAI", "Anthropic", "Google", "Meta", "xAI"
- 기술 동향 키워드 없음: "트렌드", "업데이트", "릴리즈", "출시", "기술 동향"
- "최신" 키워드가 `WEB_SEARCH_KEYWORDS`에 있어 RAG보다 웹 검색이 우선됨

### 2.4 InputInterpretationChain 분석

**현재 로직**:
```java
private SearchContext analyzeContext(String input) {
    SearchContext context = new SearchContext();
    String lowerInput = input.toLowerCase();

    if (containsAny(lowerInput, BOOKMARK_KEYWORDS)) {
        context.addCollection("bookmarks");
    }
    if (containsAny(lowerInput, EMERGING_TECH_KEYWORDS)) {
        context.addCollection("emerging_techs");
    }

    // 키워드 매칭 없으면 전체 검색
    if (context.getCollections().isEmpty()) {
        context.addCollection("bookmarks");
        context.addCollection("emerging_techs");
    }
    return context;
}
```

**문제점**:
- `EMERGING_TECH_KEYWORDS = Set.of("기술", "tech", "emerging")` -- 범위가 너무 좁음
- 기본값이 `bookmarks + emerging_techs` 모두 포함이지만, 실제 검색은 bookmarks만 수행됨
- Emerging Tech 전용으로 변경 시 기본값을 `emerging_techs`만으로 설정해야 함

### 2.5 베스트 프랙티스 준수 여부 검증

#### MongoDB Atlas Vector Search

| 항목 | 공식 권장사항 | 현재 상태 | 판정 |
|------|-------------|----------|------|
| `numCandidates:limit` 비율 | >= 20배 | 100:5 = 20배 | 적합 |
| Similarity 함수 | cosine (OpenAI 임베딩 정규화됨) | `VectorSearchIndexConfig`: cosine | 적합 |
| Pre-filter | `$vectorSearch`의 `filter` 파라미터 사용 | bookmarks에서 `userId` pre-filter 사용 | 적합 |
| `$vectorSearch` 위치 | aggregation pipeline 첫 번째 stage | `createBookmarkSearchPipeline()`에서 준수 | 적합 |
| Score 추출 | `$meta: "vectorSearchScore"` | `createScoreAddFieldsStage()`에서 사용 | 적합 |
| Filter 필드 인덱스 | Vector Search Index에 `type: "filter"` 정의 필요 | `VectorSearchIndexConfig`에 `provider`, `status` filter 정의됨 | 적합 |

> 출처: https://www.mongodb.com/docs/atlas/atlas-vector-search/vector-search-stage/
> 출처: https://www.mongodb.com/docs/atlas/atlas-vector-search/tune-vector-search/

#### LangChain4j RAG

| 항목 | 공식 권장사항 | 현재 상태 | 판정 |
|------|-------------|----------|------|
| ChatMemory | `TokenWindowChatMemory` 권장 (프로덕션) | `MessageWindowChatMemory` 사용 (창 크기 `chatbot.chat-memory.max-messages`) | 적합 — 아래 4.2 참고 |
| 쿼리 압축 | `CompressingQueryTransformer` 사용 권장 (멀티턴) | `InputInterpretationChain`에서 노이즈 제거만 수행, 대명사 해소 없음 | 개선 권장 |
| `storeRetrievedContentInChatMemory` | `false` 권장 (메모리 최적화) | RAG 결과가 ChatMemory에 저장되지 않음 (적합) | 적합 |
| Re-Ranking | `ScoringModel` 사용 권장 | Cohere rerank 옵션 지원 (기본 비활성) | 적합 |

> 출처: https://docs.langchain4j.dev/tutorials/rag/
> 출처: https://docs.langchain4j.dev/tutorials/chat-memory/

#### OpenAI Embeddings

| 항목 | 공식 권장사항 | 현재 상태 | 판정 |
|------|-------------|----------|------|
| 모델 | text-embedding-3-small | text-embedding-3-small 사용 | 적합 |
| 차원 | 1536 (기본값) | 1536 설정 | 적합 |
| Similarity | cosine (정규화된 벡터) | cosine 사용 | 적합 |
| Document/Query 구분 | 불필요 (동일 모델) | 구분 없이 사용 | 적합 |

> 출처: https://platform.openai.com/docs/guides/embeddings

---

## 3. 개선 설계

### 3.1 VectorSearchUtil - Emerging Tech 파이프라인

`VectorSearchUtil`에 Emerging Tech 전용 파이프라인 생성 메서드를 추가한다. 기존 `createBookmarkSearchPipeline()` 패턴을 따르되, `status: "PUBLISHED"` pre-filter를 기본 적용한다.

#### 상수 추가

```java
// 기존
public static final String COLLECTION_BOOKMARKS = "bookmarks";
public static final String INDEX_BOOKMARKS = "vector_index_bookmarks";

// 추가
public static final String COLLECTION_EMERGING_TECHS = "emerging_techs";
public static final String INDEX_EMERGING_TECHS = "vector_index_emerging_techs";
```

#### 메서드 시그니처

```java
/**
 * Emerging Tech 컬렉션 Vector Search 파이프라인 생성
 * status: "PUBLISHED" pre-filter 기본 적용
 *
 * @param queryVector 쿼리 벡터
 * @param options 검색 옵션
 * @return aggregation pipeline
 */
public static List<Document> createEmergingTechSearchPipeline(
        List<Float> queryVector,
        VectorSearchOptions options)
```

#### 구현 설계

```java
public static List<Document> createEmergingTechSearchPipeline(
        List<Float> queryVector,
        VectorSearchOptions options) {

    List<Document> pipeline = new ArrayList<>();

    // 1. status: "PUBLISHED" pre-filter 생성
    Document statusFilter = new Document("status", "PUBLISHED");
    Document combinedFilter;

    if (options.getFilter() != null && !options.getFilter().isEmpty()) {
        combinedFilter = new Document("$and", List.of(statusFilter, options.getFilter()));
    } else {
        combinedFilter = statusFilter;
    }

    // 2. VectorSearchOptions에 filter 적용
    VectorSearchOptions emergingTechOptions = VectorSearchOptions.builder()
        .indexName(options.getIndexName() != null ? options.getIndexName() : INDEX_EMERGING_TECHS)
        .path(options.getPath())
        .numCandidates(options.getNumCandidates())
        .limit(options.getLimit())
        .minScore(options.getMinScore())
        .filter(combinedFilter)
        .exact(options.isExact())
        .build();

    // 3. $vectorSearch stage
    pipeline.add(createVectorSearchStage(queryVector, emergingTechOptions));

    // 4. $addFields stage (score 추가)
    pipeline.add(createScoreAddFieldsStage());

    // 5. $match stage (minScore 필터링)
    if (options.getMinScore() > 0) {
        pipeline.add(createScoreFilterStage(options.getMinScore()));
    }

    return pipeline;
}
```

**설계 근거**:
- `createBookmarkSearchPipeline()`과 동일한 패턴으로 일관성 유지
- `status: "PUBLISHED"` pre-filter를 `$vectorSearch`의 `filter` 파라미터로 전달하여 검색 전에 필터링 (MongoDB 공식 권장: pre-filter가 post-filter보다 효율적)
- `provider` filter는 `options.getFilter()`로 선택적 전달 가능 (VectorSearchIndexConfig에 filter 인덱스 정의됨)
- 기존 `createVectorSearchStage()`, `createScoreAddFieldsStage()`, `createScoreFilterStage()` 메서드 재사용 (DRY)

> 출처: https://www.mongodb.com/docs/atlas/atlas-vector-search/vector-search-stage/

### 3.2 VectorSearchServiceImpl - 검색 로직 추가

#### search() 메서드 수정

```java
@Override
public List<SearchResult> search(String query, Long userId, SearchOptions options) {
    // 1. 쿼리 임베딩 생성 (한 번만)
    Embedding embedding = embeddingModel.embed(query).content();
    List<Float> queryVector = embedding.vectorAsList();

    // 2. 컬렉션별 검색
    List<SearchResult> results = new ArrayList<>();

    if (Boolean.TRUE.equals(options.includeBookmarks()) && userId != null) {
        results.addAll(searchBookmarks(queryVector, userId.toString(), options));
    }

    // 추가: Emerging Tech 검색
    if (Boolean.TRUE.equals(options.includeEmergingTechs())) {
        results.addAll(searchEmergingTechs(queryVector, options));
    }

    // 3. 유사도 점수로 정렬 및 최종 결과 수 제한
    return results.stream()
        .sorted((a, b) -> Double.compare(b.score(), a.score()))
        .limit(options.maxResults())
        .collect(Collectors.toList());
}
```

#### searchEmergingTechs() 메서드 추가

```java
/**
 * Emerging Tech 벡터 검색 (status: PUBLISHED 필터 적용)
 *
 * MongoDB Atlas Vector Search의 $vectorSearch aggregation stage를 사용합니다.
 * status: "PUBLISHED" pre-filter가 기본 적용됩니다.
 */
private List<SearchResult> searchEmergingTechs(List<Float> queryVector, SearchOptions options) {
    try {
        // 1. VectorSearchOptions 생성
        VectorSearchOptions vectorOptions = VectorSearchOptions.builder()
            .indexName(VectorSearchUtil.INDEX_EMERGING_TECHS)
            .numCandidates(options.numCandidates() != null ? options.numCandidates() : 100)
            .limit(options.maxResults() != null ? options.maxResults() : 5)
            .minScore(options.minSimilarityScore() != null ? options.minSimilarityScore() : 0.7)
            .exact(Boolean.TRUE.equals(options.exact()))
            .build();

        // 2. aggregation pipeline 생성 (status: PUBLISHED 필터 포함)
        List<Document> pipeline = VectorSearchUtil.createEmergingTechSearchPipeline(
            queryVector, vectorOptions);

        // 3. 실행
        List<Document> results = mongoTemplate
            .getCollection(VectorSearchUtil.COLLECTION_EMERGING_TECHS)
            .aggregate(pipeline)
            .into(new ArrayList<>());

        // 4. SearchResult로 변환
        return results.stream()
            .map(doc -> convertToSearchResult(doc, "EMERGING_TECH"))
            .collect(Collectors.toList());

    } catch (Exception e) {
        log.error("Vector search for emerging techs failed: {}", e.getMessage(), e);
        return new ArrayList<>();
    }
}
```

**설계 근거**:
- `searchBookmarks()` 메서드와 동일한 패턴 (일관성)
- `userId` 파라미터가 불필요 (Emerging Tech는 전체 공개 데이터)
- collectionType을 `"EMERGING_TECH"`로 설정하여 SourceResponse에서 출처 구분 가능
- 예외 발생 시 빈 리스트 반환 (기존 패턴 유지, 장애 격리)

### 3.3 IntentClassificationService - 키워드 확장

#### RAG_KEYWORDS 확장

```java
private static final Set<String> RAG_KEYWORDS = Set.of(
    // 기존 (contest/news/bookmark 관련 제거 - Emerging Tech 전용이므로)
    "검색", "찾아", "알려", "정보", "어떤", "무엇",

    // Emerging Tech 키워드 추가
    "ai", "인공지능", "llm", "gpt", "claude", "gemini", "모델",
    "api", "sdk", "릴리즈", "release", "업데이트", "update",
    "openai", "anthropic", "google", "meta", "xai",
    "기술", "tech", "트렌드", "trend", "동향",
    "출시", "발표", "버전"
);
```

#### WEB_SEARCH_KEYWORDS에서 충돌 키워드 조정

"최신", "최근", "latest" 등의 키워드가 `WEB_SEARCH_KEYWORDS`에 있어 RAG보다 웹 검색이 우선되는 문제가 있다. Emerging Tech 전용 RAG이므로 기술 관련 최신 정보는 벡터 검색으로 처리하는 것이 적절하다.

**방안**: `containsWebSearchKeywords()` 체크 시, RAG 키워드와 동시에 매칭되면 RAG를 우선하도록 로직 수정.

```java
@Override
public Intent classifyIntent(String preprocessedInput) {
    String lowerInput = preprocessedInput.toLowerCase().trim();

    // 0. @agent 프리픽스 감지
    if (lowerInput.startsWith(AGENT_COMMAND_PREFIX)) {
        return Intent.AGENT_COMMAND;
    }

    // 1. RAG 키워드 + Web 검색 키워드 동시 체크
    boolean hasRagKeywords = containsRagKeywords(lowerInput);
    boolean hasWebSearchKeywords = containsWebSearchKeywords(lowerInput);

    // RAG 키워드가 있으면 RAG 우선 (기술 관련 최신 정보는 벡터 검색)
    if (hasRagKeywords) {
        return Intent.RAG_REQUIRED;
    }

    // Web 검색만 해당되면 Web 검색
    if (hasWebSearchKeywords) {
        return Intent.WEB_SEARCH_REQUIRED;
    }

    // 2. 질문 형태 체크
    if (isQuestion(lowerInput) && !containsLlmDirectKeywords(lowerInput)) {
        return Intent.RAG_REQUIRED;
    }

    // 3. 기본값: LLM 직접 처리
    return Intent.LLM_DIRECT;
}
```

**설계 근거**:
- Emerging Tech 전용 RAG이므로, 기술 관련 키워드가 있으면 벡터 검색을 우선
- "최신 AI 모델 알려줘" 같은 질문이 웹 검색이 아닌 RAG으로 분류되어야 함
- 기술과 무관한 실시간 정보(날씨, 주가 등)는 기존대로 웹 검색

### 3.4 InputInterpretationChain - 기본 검색 대상 변경

#### 기본 컬렉션을 `emerging_techs`로 변경

```java
private SearchContext analyzeContext(String input) {
    SearchContext context = new SearchContext();
    String lowerInput = input.toLowerCase();

    if (containsAny(lowerInput, BOOKMARK_KEYWORDS)) {
        context.addCollection("bookmarks");
    }
    if (containsAny(lowerInput, EMERGING_TECH_KEYWORDS)) {
        context.addCollection("emerging_techs");
    }

    // 키워드 매칭 없으면 Emerging Tech 기본 검색
    if (context.getCollections().isEmpty()) {
        context.addCollection("emerging_techs");
    }

    return context;
}
```

#### EMERGING_TECH_KEYWORDS 확장

```java
private static final Set<String> EMERGING_TECH_KEYWORDS = Set.of(
    "기술", "tech", "emerging",
    "ai", "인공지능", "llm", "gpt", "claude", "gemini",
    "openai", "anthropic", "google", "meta", "xai",
    "릴리즈", "release", "업데이트", "update", "트렌드", "trend"
);
```

**설계 근거**:
- RAG_REQUIRED로 분류된 질문은 대부분 기술 동향 관련이므로, 기본값을 `emerging_techs`로 설정
- 명시적으로 "북마크" 키워드가 있을 때만 bookmarks 검색 포함
- EMERGING_TECH_KEYWORDS를 확장하여 더 많은 질문이 emerging_techs로 매핑되도록 함

### 3.5 PromptServiceImpl - 프롬프트 개선

#### RAG 프롬프트 템플릿 개선

현재 프롬프트는 범용적이어서 검색된 문서의 메타데이터(provider, title, publishedAt)를 활용하지 못한다. Emerging Tech 컨텍스트에 맞게 개선한다.

```java
@Override
public String buildPrompt(String query, List<SearchResult> searchResults) {
    List<SearchResult> truncatedResults = tokenService.truncateResults(
        searchResults, maxContextTokens);

    StringBuilder prompt = new StringBuilder();
    prompt.append("당신은 최신 AI/기술 동향 전문가입니다. ");
    prompt.append("다음 기술 업데이트 문서들을 참고하여 질문에 답변해주세요.\n\n");
    prompt.append("질문: ").append(query).append("\n\n");
    prompt.append("참고 문서:\n");

    for (int i = 0; i < truncatedResults.size(); i++) {
        SearchResult result = truncatedResults.get(i);
        prompt.append(String.format("[문서 %d]", i + 1));

        // 메타데이터 추출 (Document 타입인 경우)
        if (result.metadata() instanceof org.bson.Document doc) {
            String title = doc.getString("title");
            String provider = doc.getString("provider");
            Object publishedAt = doc.get("published_at");

            if (title != null) prompt.append(" ").append(title);
            if (provider != null) prompt.append(" (").append(provider).append(")");
            if (publishedAt != null) prompt.append(" [").append(publishedAt).append("]");
        }
        prompt.append("\n");
        prompt.append(result.text()).append("\n\n");
    }

    prompt.append("위 문서들을 바탕으로 질문에 정확하고 간결하게 답변해주세요. ");
    prompt.append("답변에 출처(provider, 문서 제목)를 포함해주세요.");

    tokenService.validateInputTokens(prompt.toString());
    return prompt.toString();
}
```

**설계 근거**:
- 시스템 프롬프트에 역할 부여 ("최신 AI/기술 동향 전문가")로 답변 품질 향상
- 메타데이터(title, provider, publishedAt)를 프롬프트에 포함하여 LLM이 출처를 인용할 수 있도록 함
- 답변에 출처 포함을 지시하여 RAG의 신뢰성 향상
- `result.metadata()`가 `org.bson.Document` 타입인 경우에만 메타데이터 추출 (기존 `convertToSearchResult()`에서 `metadata(doc)` 저장)

### 3.6 SearchOptions 단순화

#### emergingTechsOnly 팩토리 메서드 추가

```java
@Builder
public record SearchOptions(
    Boolean includeBookmarks,
    Boolean includeEmergingTechs,
    Integer maxResults,
    Integer numCandidates,
    Double minSimilarityScore,
    Boolean exact
) {
    // 기존 메서드 유지

    /**
     * Emerging Tech만 검색하는 옵션
     */
    public static SearchOptions emergingTechsOnly() {
        return SearchOptions.builder()
            .includeBookmarks(false)
            .includeEmergingTechs(true)
            .maxResults(5)
            .numCandidates(100)
            .minSimilarityScore(0.7)
            .exact(false)
            .build();
    }
}
```

**설계 근거**:
- 기존 `defaults()`, `bookmarksOnly()` 패턴과 일관된 팩토리 메서드
- `includeBookmarks`/`includeEmergingTechs` 플래그를 유지하여 향후 확장성 보장
- `numCandidates:limit` 비율 = 100:5 = 20배 (MongoDB 공식 권장 준수)

---

## 4. 멀티턴 RAG 베스트 프랙티스 검증

### 4.1 CompressingQueryTransformer 적용 검토

**LangChain4j 공식 권장사항** (출처: https://docs.langchain4j.dev/tutorials/rag/):
> `CompressingQueryTransformer`: 멀티턴 대화에서 대명사 해소를 수행한다.
> 예: "그것에 대해 더 알려줘" → "OpenAI GPT-4o 릴리즈에 대해 더 알려줘"

**현재 상태**:
- `InputInterpretationChain.cleanInput()`은 노이즈 패턴 제거만 수행
- 대명사 해소(pronoun resolution) 기능 없음
- 멀티턴 대화에서 "그것", "이것", "더" 같은 대명사를 해소하지 못함

**검토 결과**:
- 현재 `InputInterpretationChain`은 단순 노이즈 제거만 수행하며, LangChain4j의 `CompressingQueryTransformer`와 같은 LLM 기반 쿼리 압축 기능은 없음
- 멀티턴 RAG 품질을 높이려면 대명사 해소가 필요하지만, 이는 추가 LLM 호출을 수반하므로 비용/지연 트레이드오프가 있음
- **이번 설계 범위**: 대화 컨텍스트 압축은 별도 개선 과제로 분류. 현재는 Emerging Tech 벡터 검색 구현에 집중

### 4.2 ChatMemory 전략 검증

**LangChain4j 공식 권장사항** (출처: https://docs.langchain4j.dev/tutorials/chat-memory/):
> 프로덕션 환경에서는 `TokenWindowChatMemory` 사용을 권장한다.
> `TokenCountEstimator`를 사용하여 토큰 수 기준으로 메시지를 유지한다.

**현재 상태**:
- `ConversationChatMemoryProvider`가 `MessageWindowChatMemory`를 만들어 주고, 창 크기는 `chatbot.chat-memory.max-messages`(기본 10)로 정한다
- 설정에 있던 `chat-memory.max-tokens`와 `strategy: token-window`는 코드가 읽지 않는 값이라 지웠다. 실제 동작인 메시지 개수 기준에 맞춰 키를 하나로 정리한 것이다

**검증 결과**:
- 설정과 코드의 어긋남은 해소됐다. 남는 것은 `TokenWindowChatMemory`로 갈지 여부인데, 지금은 가지 않는다
- 이력을 채우는 쪽이 `ChatbotServiceImpl.loadHistoryToMemory()`이고 `MongoDbChatMemoryStore`의 쓰기가 no-op이라, ChatMemory에 저장소를 걸면 방금 `add()`한 현재 질문이 프롬프트에서 빠진다. 저장소를 걸지 않는 지금 구성이 이 문제를 피한다
- `OpenAiTokenCountEstimator` Bean은 그대로 있으므로 토큰 기준으로 바꿀 길은 열려 있다. 다만 바꾸려면 저장 순서와 실패한 턴의 처리까지 함께 정해야 해서 별도 과제로 둔다

### 4.3 검색 결과 품질 검증

| 항목 | 공식 권장값 | 현재 설정 | 판정 |
|------|----------|----------|------|
| `numCandidates:limit` 비율 | >= 20배 | 100:5 = 20배 | 적합 |
| Similarity 함수 | cosine (OpenAI 정규화 벡터) | cosine | 적합 |
| `minScore` 임계값 | 경험적 조정 (0.7-0.8 시작점) | 0.7 | 적합 |
| Embedding 차원 | 1536 (text-embedding-3-small 기본) | 1536 | 적합 |
| Pre-filter | `$vectorSearch` filter 파라미터 | status, provider 인덱스 정의됨 | 적합 |
| Re-Ranking | ScoringModel 사용 권장 | Cohere 옵션 지원 (기본 비활성) | 적합 |

> 출처: https://www.mongodb.com/docs/atlas/atlas-vector-search/tune-vector-search/
> 출처: https://platform.openai.com/docs/guides/embeddings

---

## 5. 수정 파일 목록 및 변경 요약

### 수정 대상 파일

| # | 모듈 | 파일 | 변경 내용 |
|---|------|------|----------|
| 1 | domain-mongodb | `VectorSearchUtil.java` | 상수 추가 (`COLLECTION_EMERGING_TECHS`, `INDEX_EMERGING_TECHS`), `createEmergingTechSearchPipeline()` 메서드 추가 |
| 2 | api-chatbot | `VectorSearchServiceImpl.java` | `searchEmergingTechs()` 메서드 추가, `search()` 메서드에 emerging_techs 분기 추가 |
| 3 | api-chatbot | `IntentClassificationServiceImpl.java` | `RAG_KEYWORDS` 확장, `classifyIntent()` 우선순위 조정 (RAG > Web Search) |
| 4 | api-chatbot | `InputInterpretationChain.java` | `EMERGING_TECH_KEYWORDS` 확장, 기본 검색 컬렉션을 `emerging_techs`로 변경 |
| 5 | api-chatbot | `PromptServiceImpl.java` | `buildPrompt()` Emerging Tech 메타데이터 포함 프롬프트 개선 |
| 6 | api-chatbot | `SearchOptions.java` | `emergingTechsOnly()` 팩토리 메서드 추가 |

### 변경하지 않는 파일

| 파일 | 이유 |
|------|------|
| `ChatbotServiceImpl.java` | 파이프라인 오케스트레이션 구조 유지 (변경 불필요) |
| `VectorSearchService.java` | 인터페이스 시그니처 변경 불필요 |
| `LangChain4jConfig.java` | LLM/Embedding 설정 유지 |
| `AnswerGenerationChain.java` | 답변 생성 구조 유지 |
| `ResultRefinementChain.java` | 결과 정제 구조 유지 |
| `SearchResult.java` | DTO 구조 유지 (collectionType으로 구분) |
| `SearchContext.java` | 기존 구조 유지 |
| `SearchQuery.java` | 기존 구조 유지 |
| `application-chatbot-api.yml` | RAG 설정값 유지 (동일한 numCandidates, limit, minScore 적용) |
| `VectorSearchOptions.java` | 기존 구조 유지 |
| `VectorSearchIndexConfig.java` | 인덱스 정의 이미 존재 |
| `EmergingTechDocument.java` | 스키마 변경 불필요 |
| `EmergingTechRepository.java` | 벡터 검색은 MongoTemplate aggregation 사용 |

---

## 6. 구현 체크리스트

- [ ] `VectorSearchUtil`에 `COLLECTION_EMERGING_TECHS`, `INDEX_EMERGING_TECHS` 상수 추가
- [ ] `VectorSearchUtil`에 `createEmergingTechSearchPipeline()` 메서드 구현
- [ ] `createEmergingTechSearchPipeline()`에서 `status: "PUBLISHED"` pre-filter 기본 적용
- [ ] `VectorSearchServiceImpl`에 `searchEmergingTechs()` 메서드 구현
- [ ] `VectorSearchServiceImpl.search()`에 `includeEmergingTechs` 분기 추가
- [ ] `IntentClassificationServiceImpl.RAG_KEYWORDS`에 AI/기술 관련 키워드 추가
- [ ] `IntentClassificationServiceImpl.classifyIntent()`에서 RAG 키워드 우선 체크
- [ ] `InputInterpretationChain.EMERGING_TECH_KEYWORDS` 확장
- [ ] `InputInterpretationChain.analyzeContext()` 기본값을 `emerging_techs`로 변경
- [ ] `PromptServiceImpl.buildPrompt()`에 Emerging Tech 메타데이터(title, provider, publishedAt) 포함
- [ ] `SearchOptions`에 `emergingTechsOnly()` 팩토리 메서드 추가
- [ ] `numCandidates:limit` 비율 20배 유지 확인 (MongoDB 공식 권장)
- [ ] cosine similarity 사용 확인 (OpenAI 공식 권장)
- [ ] `vector_index_emerging_techs` 인덱스가 MongoDB Atlas에 생성되어 있는지 확인
- [ ] 기존 bookmarks 검색 로직 정상 동작 확인 (회귀 방지)
- [ ] SOLID 원칙 준수 확인
- [ ] 오버엔지니어링 없이 최소 변경으로 구현되었는지 확인
- [ ] 컴파일 확인: `./gradlew :api-chatbot:build`

---

## 7. 참고 자료 (공식 문서 링크)

| 주제 | URL |
|------|-----|
| MongoDB Atlas Vector Search | https://www.mongodb.com/docs/atlas/atlas-vector-search/ |
| $vectorSearch Aggregation Stage | https://www.mongodb.com/docs/atlas/atlas-vector-search/vector-search-stage/ |
| Vector Search Index 설정 | https://www.mongodb.com/docs/atlas/atlas-vector-search/vector-search-type/ |
| Vector Search 성능 튜닝 | https://www.mongodb.com/docs/atlas/atlas-vector-search/tune-vector-search/ |
| MongoDB RAG 가이드 | https://www.mongodb.com/docs/atlas/atlas-vector-search/rag/ |
| LangChain4j RAG Tutorial | https://docs.langchain4j.dev/tutorials/rag/ |
| LangChain4j MongoDB Atlas Integration | https://docs.langchain4j.dev/integrations/embedding-stores/mongodb-atlas/ |
| LangChain4j Chat Memory | https://docs.langchain4j.dev/tutorials/chat-memory/ |
| OpenAI Embeddings Guide | https://platform.openai.com/docs/guides/embeddings |
| Spring Data MongoDB Reference | https://docs.spring.io/spring-data/mongodb/reference/ |
