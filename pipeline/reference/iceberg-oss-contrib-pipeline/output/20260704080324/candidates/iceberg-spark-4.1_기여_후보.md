# iceberg-spark-4.1 (:iceberg-spark:iceberg-spark-4.1_2.13) 기여 후보 분석

focus: codebase-gaps — 2026-06-21 이후 spark/v4.1/spark/ 에 병합된 신규 커밋 6개의 델타만 조사(과거 run 20260621111212에서 이 모듈은 형제버전 parity 54개 파일 전수 조사·issues 트랙 모두 완료됨, 재탐색 제외).

## 결론

신규 커밋 6개 모두 실제 diff를 읽고 검토했으나, 임계(≥18) 통과 후보 없음.

### 커밋별 확인 내용

1. **035fc1e40 (Spark 4.1: Map geo Spark types, #16851)** — `PruneColumnsWithoutReordering.java`, `SparkTypeToType.java`, `TypeToSparkType.java`에 GEOMETRY/GEOGRAPHY 타입 변환·프루닝·CRS/알고리즘 검증 추가.
   - `TypeToSparkType.convertAlgorithm`/`SparkTypeToType.convertAlgorithm`이 `EdgeAlgorithm.SPHERICAL` 외에는 전부 `UnsupportedOperationException`을 던지는 게 처음엔 "누락 아닌가" 의심스러웠으나, 실제 Spark 4.1.2 jar(`spark-sql-api_2.13-4.1.2.jar`)를 `javap`로 직접 확인한 결과 `EdgeInterpolationAlgorithm`의 구현체가 `SPHERICAL$` 단 하나뿐이었다. 즉 Spark 자체가 SPHERICAL만 지원하므로 이 throw는 버그가 아니라 정당한 기능 제약 반영.
   - `TestSparkSchemaUtil.java`에 라운드트립·mixed-SRID 거부·CRS 불일치 프루닝 거부 테스트가 이미 충분히 추가돼 있음(테스트 121줄 신규).
   - 다른 spark/v4.1/spark 파일(ORC/Avro reader·writer, `SparkFixupTypes`)은 GEOMETRY/GEOGRAPHY를 아직 다루지 않지만, 이는 Parquet 모듈에서만 WKB 판독/기록이 구현된 단계적 롤아웃(형제 커밋 744e81103 "Parquet: Read and write geometry and geography WKB values")과 일치하는 의도된 범위이지 이 모듈의 결함이 아님.

2. **874e4096e (Core, Spark: Ensure correct delete file sizes in rewrite table action, #15470)** — `RewriteTablePathSparkAction.java`가 position delete 물리 재작성(`rewritePositionDeletes`)을 매니페스트 재작성(`rewriteManifests`)보다 먼저 실행하도록 순서를 바꾸고, 재작성된 실제 파일 크기를 `Map<String, Long>`(source location → rewritten size)으로 브로드캐스트해 매니페스트 엔트리에 반영하도록 고쳤다.
   - core `writeDeleteFileEntry`가 조회 키로 쓰는 `file.location()`과 spark `rewritePositionDelete`가 맵에 채우는 키(`deleteFile.location()`)가 둘 다 "원본 source 경로"로 일치함을 직접 대조 확인 — 키 불일치 버그 없음.
   - Puffin 파일 하나에 여러 DV(deletion vector)가 걸린 경우 물리 파일은 `location` 기준으로 한 번만 재작성하고(`byLocation` dedup), 논리 엔트리별로는 같은 크기를 재사용하는 구조도 주석·구현이 일치.
   - `positionDeletesInManifest`가 라이브 여부·snapshotId 필터링 없이 매니페스트의 모든 POSITION_DELETES 엔트리를 수집하는 게 처음엔 회귀로 의심됐으나, core `writeDeleteFileEntry`(라인 527) 역시 `result.toRewrite().add(file.copy())`를 라이브/snapshotId 조건 밖에서 무조건 실행해 동일하게 전체 엔트리를 다루고 있어 기존 동작과 일치함을 확인. 회귀 아님.
   - 테스트 304줄 추가로 크기 불일치 시나리오를 검증하고 있어 커버리지도 충분.

3. **035fc1e40 자매 격인 fb6bb97e3(Spark 3.5/4.0)**과 **fa072cc6b(Spark 4.1, 우리가 제출)** — 이미 처리된 건이라 재확인만 하고 후보에서 제외(지시사항대로).

4. **8c1ee9d9d (Core, Spark: Migrate Spark table properties to Spark module, #15875)** — `SparkTableProperties.java` 신설, `SparkWriteConf`/`SparkTable`이 참조를 `TableProperties.SPARK_WRITE_*`에서 `SparkTableProperties.WRITE_*`로 교체. core `TableProperties.java`는 41줄 추가(제거 아님)로 확인 — 기존 상수를 남기고 이관했으므로 revapi breaking 아님. 새로 만든 `SparkTableProperties`도 상수 3개+1개로 단순 이관이라 결함 소지 없음.

5. **902ed1b80 (Revert "Spark tests cache rewrite input")** — `TestRewriteDataFilesAction.java` 테스트 코드만 되돌린 순수 revert. 프로덕션 코드 변경 없음.

억지 NPE-guard 후보를 만들지 않고 정직하게 "no suitable opportunities"로 마감한다.

### GitHub 이슈 확인
`gh issue list -R apache/iceberg --search "spark 4.1" --state open` 결과 2026-06-21 이후 생성된 신규 이슈 없음(최신 생성 이슈가 16663, 2026-06-02).
