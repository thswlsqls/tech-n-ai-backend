# langchain4j-embeddings-bge-small-en-v15-q 기여 후보 분석

## 제외된 후보: 강한 후보 없음 (완전 소진, 형제 모듈과 동일 결론)

### 제외 이유

1. **thin delegator, 독립 로직 없음**:
   - `BgeSmallEnV15QuantizedEmbeddingModel`은 생성자 2개(`no-arg`, `Executor` 인자)와 `model()`/`knownDimension()` 오버라이드뿐이다(총 59줄). 실제 임베딩 로직은 전부 base 모듈(`langchain4j-embeddings`)의 `AbstractInProcessEmbeddingModel`/`OnnxBertBiEncoder`에 있다.
   - `BgeSmallEnV15QuantizedEmbeddingModelFactory`는 `create()` 한 줄뿐(총 12줄).
   - base 모듈은 이미 2회 별도 run(20260614073239, 20260702061751)에서 깊게 발굴돼 소진됐고(무한루프 #5454·empty-embeddings #4406 머지, Javadoc typo 66a6d5f73 머지), 이번 스코프 밖이라 재조치 대상 아님.

2. **형제 모듈(bge-small-en, bge-small-en-q) 재발굴 시 동일 결론 예측이 실측으로 확인됨**:
   - 과거 run이 지적한 두 갭 각도(생성자 Javadoc "Threads are cached for 1 second." 누락, IT 테스트명-동작 불일치)를 직접 재확인 — 이 모듈은 **둘 다 이미 정상**이다(Javadoc line 23/35 존재, 테스트명 `should_embed_text_longer_than_510_tokens_by_splitting_and_averaging_embeddings_of_splits`가 line 44에 정확히 존재). all-minilm-l6-v2에서만 발견된 아웃라이어였지 형제 전체의 공통 결함이 아니었다는 기존 결론(run 20260703065153)이 이 모듈에도 그대로 적용됨.
   - `bge-small-en-q`(구조 확인된 clean sibling)와 `.java` 파일을 line-by-line diff한 결과, 차이는 전부 클래스명/패키지명/onnx 파일명/모델 설명(v1.5 vs 비-v1.5) 치환뿐이며 Javadoc·로직·null가드 어디에도 누락·불일치가 없다.

3. **api-contract 위반 없음 (Javadoc vs 공식 모델카드 대조)**:
   - BAAI 공식 모델카드(https://huggingface.co/BAAI/bge-small-en-v1.5, WebFetch로 직접 확인) 기준 임베딩 차원 384, 권장 최대 512토큰, 쿼리 prefix 문구 `"Represent this sentence for searching relevant passages:"` 전부 Javadoc과 정확히 일치.
   - pom.xml의 onnx 모델 sha256(`6c9c6101a956d62dfb5e7190c538226c0c5bb9cb27b651234b6df063ee7dbfe4`)을 실제 다운로드된 `target/classes/bge-small-en-v1.5-q.onnx` 파일의 `shasum -a 256` 결과와 대조 — 완전 일치(wrong-output 아님).
   - README(3개 링크: 원본 모델카드/ONNX 모델카드/ONNX 모델파일/토크나이저)도 정상 URL.

4. **github-backed 후보 없음**:
   - `gh search issues/prs`로 `BgeSmallEnV15Quantized`, `bge-small-en-v15-q`, `bgesmallenv15q` 검색 — 이 모듈을 직접 겨냥한 매치 0건.
   - 유일하게 매치된 것은 base 로더 이슈 #1073(OnnxBertBiEncoder classloader 크래시, 형제 전체 스택트레이스)로 스코프 밖(base 모듈 이슈이며 이미 기록됨).

5. **NPE-guard fallback 조건 불충족**:
   - `Executor` 인자는 이미 `ensureNotNull(executor, "executor")`로 가드됨. 추가 null 취약 지점 없음.

**결론**: strong-aspirant(≥22점) 0건, recommendable(18~21점) 0건. `_learnings.md`가 예측한 "남은 미탐색 wrapper도 동일 결론일 가능성 높음"이 이 모듈에서 실측 확인됨. 브랜치·worktree·PR·이슈 미생성.
