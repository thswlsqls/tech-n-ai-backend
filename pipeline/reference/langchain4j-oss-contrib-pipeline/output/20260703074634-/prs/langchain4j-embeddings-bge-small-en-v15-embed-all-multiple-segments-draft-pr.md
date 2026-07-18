# PR Draft

**Title**: `test: Add should_embed_multiple_segments test coverage to BgeSmallEnV15EmbeddingModelIT`
**Branch**: `test/bge-small-en-v15-embed-all-multiple-segments`

---

## Issue
Closes #

## Change
`BgeSmallEnV15EmbeddingModelIT` had no test covering `EmbeddingModel.embedAll(List<TextSegment>)` and its `TokenUsage` result. Among the 10 sibling `langchain4j-embeddings-*` wrapper modules, 8 already have a `should_embed_multiple_segments()` test (`bge-small-en`, `bge-small-en-q`, `bge-small-zh-v15`, `bge-small-zh-v15-q`, `all-minilm-l6-v2`, `all-minilm-l6-v2-q`, `e5-small-v2`, `e5-small-v2-q`); only `bge-small-en-v15` and `bge-small-en-v15-q` are missing it. This gap has existed since the migration commit `c3a491e0b` (#4179) that first introduced this test file.

This PR adds `should_embed_multiple_segments()` to `BgeSmallEnV15EmbeddingModelIT`, mirroring the existing pattern from `BgeSmallEnEmbeddingModelIT` (same BGE/CLS-pooling model family): embeds two segments (`"hi"`, `"hello"`), asserts the returned embeddings match individual `embed()` calls, and asserts `TokenUsage.inputTokenCount() == 2` (verified against this module's tokenizer: both words map to a single vocab entry each, so `tokenCount - 2` for CLS/SEP yields 1 token each).

No production code, dependencies, or existing tests changed — only one new `@Test` method and its imports, placed between `should_embed()` and the sentence-transformers comparison test to match the sibling file's method order.

## General checklist
- [ ] There are no breaking changes (API, behaviour) — N/A, no production code changed, test-only addition
- [X] I have added unit and/or integration tests for my change
- [X] The tests cover both positive and negative cases — assertions cover embedding equality, token count, null output/finish-reason fields
- [ ] I have manually run all the unit and integration tests in the module I have added/changed, and they are all green — compiled successfully; `*IT` tests are bound to failsafe (integration-test phase) and were not executed locally (they require the ONNX model download)
- [ ] I have manually run all the unit and integration tests in the [core](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-core) and [main](https://github.com/langchain4j/langchain4j/tree/main/langchain4j) modules, and they are all green — not run; change is scoped to a single leaf module with no core/main impact
- [ ] I have added/updated the [documentation](https://github.com/langchain4j/langchain4j/tree/main/docs/docs) — N/A, docs added after approval per convention
- [ ] I have added an example in the [examples repo](https://github.com/langchain4j/langchain4j-examples) (only for "big" features) — N/A, not a feature
- [ ] I have added/updated [Spring Boot starter(s)](https://github.com/langchain4j/langchain4j-spring) (if applicable) — N/A

---

## Staleness check
```bash
git fetch upstream
git log --oneline HEAD..upstream/main -- embeddings/langchain4j-embeddings-bge-small-en-v15/src/test/java/dev/langchain4j/model/embedding/onnx/bgesmallenv15/BgeSmallEnV15EmbeddingModelIT.java
```
Result (checked 2026-07-03): no commits — branch is up to date with upstream/main for this file.

---

## Submission command (DRAFT ONLY — DO NOT RUN)
```bash
gh pr create --draft -R langchain4j/langchain4j --title "test: Add should_embed_multiple_segments test coverage to BgeSmallEnV15EmbeddingModelIT" --body-file <this-file-body>
```

## 산출 메타 (contrib-validate)
- 제목: test: Add should_embed_multiple_segments test coverage to BgeSmallEnV15EmbeddingModelIT
- 작업 브랜치: test/bge-small-en-v15-embed-all-multiple-segments
- 연결 이슈: 없음 (test 유형 — issue_required_for: [bug-fix, feature] 미해당, PR-only)
- 제출: pr=https://github.com/langchain4j/langchain4j/pull/5724 (2026-07-07, draft)
