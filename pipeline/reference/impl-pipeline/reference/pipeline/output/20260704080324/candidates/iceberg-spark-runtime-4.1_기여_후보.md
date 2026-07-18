# iceberg-spark-runtime-4.1 기여 후보 분석

## 결론: assembly 전용 모듈(소스 5개 파일), build.gradle shading 설정 v3.5/v4.0 대비 비교 완료, 임계 통과 후보 없음

### 조사 범위
- `spark/v4.1/spark-runtime/` 디렉터리 전체(LICENSE, NOTICE, runtime-deps.txt, `src/integration/java/.../TestRoundTrip.java`)
- `spark/v4.1/build.gradle`의 `iceberg-spark-runtime-4.1_2.13` 서브프로젝트 블록(193~327행)
- 형제 버전 `spark/v4.0/build.gradle`, `spark/v3.5/build.gradle`의 대응 블록과 `diff`
- 2026-06-21 이후 병합된 두 커밋: d3daeef03(#16954, Jackson CVE 정렬), b0977bbfc(#16907, datamodel-code-generator 버전 갱신)
- `gh issue list` 검색: "spark runtime shading", "shadow jar spark"

### 확인한 사실
1. **소스 없음**: 이 모듈은 `src/main/`이 없다. shadowJar로 iceberg-spark, iceberg-spark-extensions, iceberg-aws/azure/gcp/aliyun/nessie/snowflake/hive-metastore를 relocate해서 uber-jar를 만드는 assembly 전용 모듈이다. 로직 결함을 낼 만한 코드 자체가 없다.
2. **build.gradle 3버전 diff 결과**: v3.5/v4.0/v4.1의 `iceberg-spark-runtime-*` 블록(exclude 목록, `shadowJar` relocate 규칙 15개, LICENSE/NOTICE 포함, `integrationTest` 설정)이 완전히 동일하다. 차이는 `sparkMajorVersion`, `libs.versions.sparkXX`, `libs.versions.jacksonXXX`, antlr 버전(`libs.antlr.antlr413` 등) 문자열뿐 — 전부 각 Spark 버전에 맞는 정상적인 파라미터화다. relocation 누락·exclude 불일치·의존성 누락 같은 형제 parity 갭이 없다.
3. **두 최근 커밋**: d3daeef03과 b0977bbfc 모두 `runtime-deps.txt`의 버전 번호만 갱신한 자동/의존성 봇 성격의 커밋이다(Jackson 2.15→2.21, nessie 0.107→0.108). 코드 변경 없음, 남긴 갭 없음.
4. **TestRoundTrip.java**: Iceberg Getting Started 문서 예제를 그대로 따라가는 통합 테스트(`@TestTemplate`, `ExtensionsTestBase` 상속)로, 로직 결함이 보이지 않는다. 어차피 `integrationTest` 소스셋이라 config상 로컬 실행 대상도 아니다(`integration_tests: skip`).
5. **관련 GitHub 이슈**: 열린 이슈 #13571("Spark runtime small packaging issues: service files & annotation dependencies")이 이 모듈과 관련은 있으나,
   - 모든 Spark 버전(3.5/4.0/4.1)과 다른 엔진의 shaded runtime 모듈에 공통되는 shadowJar 플러그인 자체의 구조적 한계(META-INF/services 미재작성)이지 v4.1 고유 결함이 아니다. v4.1만 고치면 형제 일관성이 깨진다.
   - 이슈 본문 스스로 "Priority: Probably low... doesn't cause any behavior changes"라고 명시.
   - 동일 문제를 고치려던 PR #6577, #7209가 모두 병합되지 않고 CLOSED — 메인테이너가 이 방향의 변경을 받아들이지 않은 전례가 있어 머지 가능성이 낮다.

### 판단
`candidate_quality.prefer_classes`(wrong-output/logic-error/github-backed/dead-ref-docs/api-contract) 중 어느 것도 이 모듈 스코프 안에서 발견되지 않았다. 유일하게 걸리는 github-backed 후보(#13571)는 스코프 밖(전체 shaded 모듈 공통 이슈) + 과거 유사 PR 반려 이력 때문에 머지용이성·리스크 항목에서 점수가 떨어져 25점 만점 중 18점 임계를 넘기지 못한다(대략 명확성 3 / 영향 2 / 머지용이성 1 / 테스트가능성 2 / 리스크낮음 2 = 10/25 수준). 억지로 후보를 만들지 않는다.
