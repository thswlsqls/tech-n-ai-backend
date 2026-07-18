# iceberg-spark-extensions-4.1 기여 후보 분석 (issues-and-merged 트랙)

## 결론: issues-and-merged 트랙 + 신규 커밋 1건 확인 결과 임계 통과 후보 없음

### 조사 범위
1. `gh issue list -R apache/iceberg --search "spark extensions"` (open, 15건)
2. `gh issue list -R apache/iceberg --label "good first issue"` (open, 12건 전수 확인)
3. `gh pr list -R apache/iceberg --state merged --search "spark extensions"` (30건)
4. 신규 커밋 `8c1ee9d9d` ("Core, Spark: Migrate Spark table properties to Spark module", #15875)의 `spark/v4.1/spark-extensions/` 변경분

### 확인 사실 (사유)

1. **good first issue 12건 중 spark-extensions 관련 없음.** 유일하게 Spark를 언급하는 항목은
   `#15916 "Docs: Clarify Spark branch write precedence over WAP branch"`이며 문서 개선 이슈로,
   실제 코드 결함이 아니고 스코프도 `spark/`가 아니라 `docs/` 텍스트다.

2. **`#16942` ("`createOrReplace` drops concurrent writers' snapshots on commit retry")** —
   `bug` 라벨이 붙은 open 이슈지만, 실제 결함 위치는 `core/src/main/java/org/apache/iceberg/BaseTransaction.java`의
   `commitReplaceTransaction` 재시도 경로다. `spark-extensions` 모듈 코드와 무관 — 모듈 스코프 밖.

3. **`PR #16626` ("Spark 4.1: Bind parameters in IcebergSparkSqlExtensionsParser", 머지됨 2026-06-05)** —
   이 모듈(`spark/v4.1/spark-extensions/src/main/scala/.../IcebergSparkSqlExtensionsParser.scala`)에
   직접 해당하는 유일한 최근 머지 PR이었으나, 실제 코드를 확인한 결과 **incomplete-fix 아님**:
   - `IcebergSparkSqlExtensionsParser`가 `parsePlanWithParameters`를 `parsePlan`과 동일한 패턴으로
     오버라이드해 `ParameterContext`를 그대로 위임(`delegate.parsePlanWithParameters`)한다(파일 전체 확인, line ~120-131).
   - Spark 4.1의 `ParserInterface` trait(`spark-catalyst_2.13-4.1.2.jar`에서 `javap`로 직접 확인)가 요구하는
     추상 메서드(`parsePlan`, `parseExpression`, `parseTableIdentifier`, `parseFunctionIdentifier`,
     `parseMultipartIdentifier`, `parseQuery`, `parseRoutineParam`) 전부가 이미 구현돼 있고 누락된 게 없다.
   - 즉 이 PR은 완결된 수정이며 후속 갭이 없다.

4. **`8c1ee9d9d`(#15875) spark-extensions 영향 재확인** — 이 커밋은 `spark-extensions` 모듈에서
   테스트 코드 3개 파일(`SparkRowLevelOperationsTestBase.java`, `TestMergeSchemaEvolution.java`)만
   건드렸고, `TableProperties.SPARK_WRITE_*` deprecated 상수 참조를 `SparkTableProperties.WRITE_*`로
   전부 치환했다. `grep -rn "TableProperties.SPARK_WRITE"` 결과 이 모듈 안에 남은 deprecated 참조가
   0건 — migration이 이 모듈 범위에서는 완결됐고, 놓친 참조나 미완성 처리 없음.

5. 그 외 머지 PR 30건(`spark extensions` 검색)은 대부분 `Spark 3.4`/`3.5`/`4.0` 백포트, 빌드/CI,
   의존성 버전 변경으로 이 모듈(v4.1 spark-extensions)과 무관하거나, 이미 완결된 변경이었다.

### 종합
prefer_classes(wrong-output/logic-error/github-backed/dead-ref-docs/api-contract) 관점에서
github-backed 후보를 최우선으로 찾았으나, 이 모듈에 해당하는 open 이슈·incomplete merged-PR이
발견되지 않았다. 억지로 NPE-guard fallback 후보를 만들지 않는다(config `candidate_quality` 원칙).
codebase-gaps 트랙은 이미 이전 run(20260621111212)에서 44개 Scala 파일 전수 조사로 완료된 상태다.

**추천 후보: 0건.**
