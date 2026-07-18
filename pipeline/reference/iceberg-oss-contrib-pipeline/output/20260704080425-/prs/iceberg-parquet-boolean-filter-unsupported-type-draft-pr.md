# Parquet: Fix boolean filter pushdown UnsupportedOperationException

Closes #17092

## Summary

- `ParquetFilters.getParquetPrimitive()` handled `Number`, `CharSequence`, and `ByteBuffer` literals but not `Boolean`, so `EQ`/`NOT_EQ` predicates on boolean columns threw `UnsupportedOperationException` while building the Parquet read builder, failing the read before any rows were returned.
- Adds `Boolean` to the existing `Number` branch (direct cast, same pattern), since `FilterApi.eq(BooleanColumn, Boolean)` expects the literal as-is.
- No other literal types (decimal/UUID, handled separately in open PR #16621) are touched.

## Testing done

- Added `TestParquet#booleanEqualityFilterPushdown` and `TestParquet#booleanInequalityFilterPushdown`, each writing a boolean-column Parquet file and reading it back through `Parquet.read(...).filter(...)`, asserting the correct rows are returned instead of throwing.
- `./gradlew :iceberg-parquet:check` — 705 tests passed (0 failures/errors), spotlessCheck/checkstyle/revapi included.
- `./gradlew :iceberg-parquet:revapi` — passed (no public API change; `ParquetFilters` is package-private, `getParquetPrimitive` is private static).

<!-- ======================= 파이프라인 제출 보조(단어수 예산 제외, 본문 아님) ======================= -->
<!-- 아래 블록은 GitHub PR 본문에 넣지 않는다. 사람이 제출 전 실행하는 헬퍼다. -->
<!-- Branch: fix/parquet-boolean-filter-unsupported-type — contributor가 커밋한 작업 브랜치. 제출 시 이 브랜치를 fork(origin)에 push한다. -->

```bash
# 제출 전 실행 — 브랜치가 stale한지 확인 (2026-07-04 확인 결과: 비어있음, 안전)
git fetch upstream
git log --oneline HEAD..upstream/main -- parquet/src/main/java/org/apache/iceberg/parquet/ParquetFilters.java parquet/src/test/java/org/apache/iceberg/parquet/TestParquet.java
```
```bash
# 사람이 issue 번호 갱신 후 실행.
# 첫 기여면 ASF ICLA/CCLA 안내 참조 → https://www.apache.org/licenses/contributor-agreements.html
git push -u origin fix/parquet-boolean-filter-unsupported-type
gh pr create -R apache/iceberg --base main --head fix/parquet-boolean-filter-unsupported-type --title "Parquet: Fix boolean filter pushdown UnsupportedOperationException" --body-file <this-file-body>
```
