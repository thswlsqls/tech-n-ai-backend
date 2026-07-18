**Apache Iceberg version**
main @ 3038fde68

**Query engine**
None — engine-agnostic, reproducible via the Java Generic Read API (`Parquet.read(...).filter(...)`).

**Please describe the bug**
`ParquetFilters.getParquetPrimitive()` (`parquet/src/main/java/org/apache/iceberg/parquet/ParquetFilters.java` line 219) only handles `Number`, `CharSequence`, and `ByteBuffer` literal values, so any `EQ`/`NOT_EQ` predicate on a `boolean` column falls through to the exception on line 233. `Literals.BooleanLiteral` (`api/.../expressions/Literals.java`) always returns a `java.lang.Boolean`, so this path is unavoidable for any boolean predicate. The exception is thrown while building the Parquet read builder (`Parquet.java` line 1635), before any rows are read — the read fails outright instead of falling back to a full scan.

**Steps to reproduce**
1. Write a Parquet file for a schema containing a `required(N, "b", Types.BooleanType.get())` column.
2. Read it back with `Parquet.read(input).project(schema).filter(Expressions.equal("b", true)).build()`.
3. Observe `java.lang.UnsupportedOperationException: Type not supported yet: java.lang.Boolean` at `ParquetFilters.getParquetPrimitive`, called from `ConvertFilterToParquet.predicate()`'s `case BOOLEAN` branch.

Expected: the reader returns only matching rows. Actual: the read throws before returning any rows.

**Additional context**
Same failure occurs for `Expressions.notEqual("b", false)`. `Number` values in the same method are returned via a direct cast; `Boolean` needs the identical treatment since `FilterApi.eq(BooleanColumn, Boolean)` expects a `Boolean` as-is.

---
<!-- Branch: fix/parquet-boolean-filter-unsupported-type -->
```bash
# 사람이 검토 후 실행 — 파이프라인은 제출하지 않는다
gh issue create -R apache/iceberg --title "Parquet: boolean equality filter pushdown throws UnsupportedOperationException" --body-file <this-file-body>
```
