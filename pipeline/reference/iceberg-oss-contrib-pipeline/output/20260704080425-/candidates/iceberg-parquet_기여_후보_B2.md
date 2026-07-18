# iceberg-parquet 기여 후보 분석

## 제외된 후보 목록 (탐색했으나 채택하지 않음)

### 제외 1: issue #15347 "Disabling statistics across multiple columns"
1. **재현 불가**: committer `moomindani`가 main 브랜치에서 동일 시나리오(2개 컬럼 stats 비활성화)를 테스트로 재현했으나 실패하지 않음(둘 다 정상적으로 stats 비활성화됨). `mukund-thakur`도 이미 동등한 테스트가 코드베이스에 존재함을 확인.
2. **환경 이슈로 판단됨**: 코멘트 스레드에서 사용자 환경(Kafka Connect sink) 문제로 결론. good first issue 라벨이 붙어 있으나 코드 결함의 증거가 없음(false-positive).

### 제외 2: issue #16090 "Parquet per column compression"
1. **범위가 기능 요청**: 버그가 아니라 신규 기능(컬럼별 압축 코덱) 요청. 이미 유사한 `write.parquet.dictionary-encoding-enabled.column.*` 패턴이 PR #16713(머지됨, "Core: Parquet per column dictionary encoding")으로 존재하지만, 압축 코덱은 parquet-java 자체의 `CodecFactory` per-column API(#3526, #3396)에 의존하며 설계 논의가 필요.
2. **prefer_classes 미충족**: wrong-output/logic-error가 아니라 improvement이므로 이번 실행 후보에서 제외.

### 제외 3: issue #16600 "Parquet vectored I/O hardcoded ON + on-heap allocator causes executor OOM"
1. **실제 결함 확인**: `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java` line 1553에서 `optionsBuilder.withUseHadoopVectoredIo(true)`가 무조건 호출됨. 단, 바이트코드 검증 결과 parquet-hadoop 1.17.1의 `ParquetReadOptions.Builder`가 애초에 `useHadoopVectoredIo` 필드를 기본값 `true`로 초기화하고, `HadoopReadOptions.Builder(conf)`도 이 필드를 Hadoop `Configuration`에서 읽어오는 로직이 없음(`.set(key,value)`는 generic `properties` 맵에만 저장되고 boolean 필드로 파싱되지 않음)을 클래스 파일 역어셈블로 확인. 즉 이슈가 제안하는 "표준 Parquet 설정을 오버라이드한다"는 진단은 정확하지 않고, 실제로는 Iceberg가 이 옵션을 노출하는 새 read 프로퍼티를 추가해야 하는 기능 성격이 강함.
2. **설계 판단 필요·범위 과다**: 이슈 자체가 Fix A(기존 설정 존중)와 Fix B(오프힙 allocator 기본값 변경) 두 가지 상충 가능한 해법을 제시하며, committer(`steveloughran`)도 추가 정보를 요청한 채 합의가 없음. 새 테이블/읽기 프로퍼티 이름·기본값 결정이 필요해 "작고 집중된 PR" 기준을 넘음. 커미터 논의 선행이 필요한 사안으로 판단해 이번 실행에서는 보류.

### 제외 4: issue #16458 "Multiple custom decoders allocate heap memory ... without defensive caps"
1. **모듈 불일치**: 영향받는 클래스가 전부 `org.apache.iceberg.arrow.vectorized.parquet.*`로 `iceberg-arrow` 모듈 소속이며 `:iceberg-parquet` 모듈(디렉터리 `parquet/`)의 소스가 아님. 이번 실행 스코프 밖.
2. **committer가 보안 태그 해제**: `rdblue`/`RussellSpitzer`가 긴급 보안 이슈가 아니라고 확인하며 by-design에 가까운 정리("hardening이면 welcome"). 스코프 불일치와 무관하게 후순위.

### 제외 5: `ParquetReadSupport.java:118` TODO ("this breaks when columns are reordered")
1. **범위가 불명확하고 위험도 높음**: Avro 기반 레거시 read-support 경로의 스키마 정렬 가정에 대한 오래된 TODO로, 실제 재현 조건과 영향 범위(어떤 엔진·리더가 이 경로를 타는지)가 코드만으로 명확히 특정되지 않음. 실증 없이 수정하면 광범위한 read 경로에 회귀를 유발할 위험이 있어 채택하지 않음.

---

## 기여 후보 #1

**후보 유형**: logic-error
**spec 게이트**: exempt:bug-fix — `format/`·`open-api/` 변경 없음, `parquet/` 내부 filter-pushdown 로직 버그 수정.
**공개 API 영향(revapi)**: no — `ParquetFilters`는 package-private 클래스, `getParquetPrimitive`는 private static 메서드. 시그니처·공개 API 변경 없음.
**공개 API 추가**: no
**PR 제목(Module: Description)**: `Parquet: Fix boolean equality filter pushdown throwing UnsupportedOperationException`

### 요약
`ParquetFilters.getParquetPrimitive()`가 `Number`/`CharSequence`/`ByteBuffer` 타입만 처리하고 `Boolean`을 처리하지 않아서, `BOOLEAN` 컬럼에 대한 `=`(EQ)·`!=`(NOT_EQ) 필터를 Parquet 파일 read builder에 전달하는 순간 `UnsupportedOperationException`이 던져져 정상적인 boolean 컬럼 필터 조회가 전부 실패한다.

### 근거
- **코드 위치**: `ParquetFilters.getParquetPrimitive()` (`parquet/src/main/java/org/apache/iceberg/parquet/ParquetFilters.java` line 218-235), 호출부 `ConvertFilterToParquet.predicate()`의 `case BOOLEAN` 분기(같은 파일 line 131-139).
- **문제 증거(직접 재현 확인)**:
  - line 224 주석 `// TODO: this needs to convert to handle BigDecimal and UUID`이 이미 이 메서드가 미완성임을 표시하지만, Boolean은 이 TODO에도 언급되지 않은 채 여전히 누락되어 있다.
  - line 226-232: `value instanceof Number`, `CharSequence`, `ByteBuffer` 세 분기만 존재하고 `Boolean` 분기가 없어 line 233-234의 `throw new UnsupportedOperationException("Type not supported yet: " + value.getClass().getName())`로 떨어진다.
  - `parquet/src/test/java/org/apache/iceberg/parquet/`에 임시 JUnit 테스트를 작성해 `./gradlew :iceberg-parquet:test`로 직접 재현: `ParquetFilters.convert(schema, Expressions.equal("b", true), true)` 호출 시
    ```
    java.lang.UnsupportedOperationException: Type not supported yet: java.lang.Boolean
        at org.apache.iceberg.parquet.ParquetFilters.getParquetPrimitive(ParquetFilters.java:234)
        at org.apache.iceberg.parquet.ParquetFilters$ConvertFilterToParquet.predicate(ParquetFilters.java:135)
    ```
    `Expressions.notEqual("b", false)`도 동일하게 line 137에서 같은 예외로 실패함을 확인(재현 후 임시 테스트 파일은 삭제, 리포에 남기지 않음).
  - `Literals.BooleanLiteral`(`api/src/main/java/org/apache/iceberg/expressions/Literals.java` line 228-229)이 `ComparableLiteral<Boolean>`을 상속하므로 `lit.value()`는 항상 `java.lang.Boolean`을 반환한다 — 정상적인 boolean 리터럴 predicate라면 반드시 이 분기를 탄다.
  - 예외는 `try/catch` 없이 `Parquet.java` line 1635 `builder.withFilter(ParquetFilters.convert(fileSchema, filter, caseSensitive))`에서 즉시 던져지며, 이는 파일을 여는 시점(read builder 구성 시점)에 발생하므로 필터를 무시하고 넘어가는 게 아니라 read 자체가 실패한다.
  - 호출 경로 확인: `ParquetFormatModel.ReadBuilder.filter()`(`parquet/src/main/java/org/apache/iceberg/parquet/ParquetFormatModel.java` line 371-372)가 `Parquet.ReadBuilder.filter()`로 위임하고, `data/src/main/java/org/apache/iceberg/data/GenericReader.java` line 103의 `.filter(task.residual())`이 이 경로를 통해 일반 테이블 스캔(Java Generic Read API 등)에서 residual 필터로 boolean 컬럼 조건을 넘길 수 있음을 확인 — 정상 호출 경로에서 도달 가능.
- **비교 대상**: 같은 메서드 내 `Number`(line 226-227) 처리 패턴 — `Number` 타입은 캐스팅만으로 그대로 반환한다. `Boolean` 역시 Parquet `BooleanColumn`이 기대하는 타입과 Java `Boolean`이 정확히 일치하므로(별도 변환 불필요) 동일한 캐스팅-반환 패턴을 적용하면 된다.
- **GitHub 근거**: 없음(신규 발견). 동일 파일의 다른 두 TODO는 이미 issue #16032(AlwaysFalse 미처리, PR #16110 선점)와 issue #16035(decimal/UUID, PR #16621 선점)로 다뤄지고 있으나, PR #16621의 diff를 직접 확인한 결과 `getParquetPrimitive`의 `Number`/`CharSequence`/`ByteBuffer` 분기 및 Boolean 미처리는 그대로 유지되어 있어 이 버그는 두 선점 이슈와 겹치지 않는다.

### 현재 구현 (AS-IS)
```java
@SuppressWarnings("unchecked")
private static <C extends Comparable<C>> C getParquetPrimitive(Literal<?> lit) {
  if (lit == null) {
    return null;
  }

  // TODO: this needs to convert to handle BigDecimal and UUID
  Object value = lit.value();
  if (value instanceof Number) {
    return (C) lit.value();
  } else if (value instanceof CharSequence) {
    return (C) Binary.fromString(value.toString());
  } else if (value instanceof ByteBuffer) {
    return (C) Binary.fromReusedByteBuffer((ByteBuffer) value);
  }
  throw new UnsupportedOperationException(  // <- Boolean 리터럴은 항상 여기로 떨어짐
      "Type not supported yet: " + value.getClass().getName());
}
```
**문제점**:
1. `BOOLEAN` 타입 컬럼에 대한 `EQ`/`NOT_EQ` predicate가 `ConvertFilterToParquet.predicate()`의 `case BOOLEAN`(line 131-139)에서 이 메서드를 호출하지만, `Boolean` 값을 처리하는 분기가 없어 항상 예외가 발생한다.
2. 예외가 파일 오픈(read builder 구성) 시점에 던져지므로, "필터 무시하고 전체 스캔"으로 안전하게 우회되지 않고 쿼리 자체가 실패한다.

### 제안 수정 (TO-BE)
```java
Object value = lit.value();
if (value instanceof Number || value instanceof Boolean) {
  return (C) lit.value();
} else if (value instanceof CharSequence) {
  return (C) Binary.fromString(value.toString());
} else if (value instanceof ByteBuffer) {
  return (C) Binary.fromReusedByteBuffer((ByteBuffer) value);
}
```
**개선 사항**:
1. `Boolean` 리터럴을 캐스팅 없이 그대로 반환해 `FilterApi.eq(BooleanColumn, Boolean)`/`FilterApi.notEq(...)`가 기대하는 타입과 일치시킨다.
2. `Number` 분기와 동일한 패턴(직접 캐스팅)을 재사용하므로 추가 변환 로직·의존성이 필요 없다.

### 리뷰 가치 평가
- **왜 머지될 만한지**: 정상적인 `WHERE bool_col = true`류 predicate가 Parquet 기반 generic reader 경로(`GenericReader`, `ParquetFormatModel`)에서 항상 `UnsupportedOperationException`으로 실패하는 명백한 결함이며, 실제 JUnit 테스트로 재현을 완료했다. 수정은 기존 `Number` 처리와 동일한 1줄짜리 조건 추가로 커미터가 바로 이해·승인할 수 있는 명료함을 갖는다.
- **왜 과도한 변경이 아닌지**: `getParquetPrimitive` 메서드 내부 조건문 1줄만 수정하며, 다른 타입(decimal/UUID/AlwaysFalse) 처리는 손대지 않는다(각각 선점된 별도 이슈/PR 영역). package-private·private 메서드만 변경하므로 공개 API·revapi 영향 없음, 새 의존성 없음.
- **테스트 계획**: `parquet/src/test/java/org/apache/iceberg/parquet/TestParquet.java`(또는 신규 `TestParquetFilters.java`, JUnit5+AssertJ)에 boolean 컬럼을 포함한 스키마로 Parquet 파일을 쓰고 `Parquet.read(...).filter(Expressions.equal("b", true))`/`Expressions.notEqual("b", false)`로 실제 파일을 읽어 예외 없이 올바른 레코드만 반환되는지 검증하는 테스트를 추가한다(Docker 불필요, 로컬 파일 기반).

점수: 명확성 5 / 영향 4 / 머지용이성 5 / 테스트가능성 5 / 리스크낮음 5 = 24/25

---
## 검증 (candidate-reviewer)
- **판정**: GO
- **변경 유형**: non-typo (logic-error, 동작 변경)
- **근거**:
  - AS-IS 대조: `ParquetFilters.java` line 218-235(`getParquetPrimitive`), line 130-139(`case BOOLEAN`)를 직접 열어 확인 — 후보가 인용한 줄 번호·코드 내용이 실제 main 브랜치와 정확히 일치. `Number`/`CharSequence`/`ByteBuffer` 세 분기만 있고 `Boolean` 분기 없음, TODO 주석도 BigDecimal/UUID만 언급.
  - 재현: `parquet/src/test/java/org/apache/iceberg/parquet/`에 임시 테스트(`TestZZZReproBooleanFilter`)를 작성해 `JAVA_HOME=temurin-21`로 `./gradlew :iceberg-parquet:test`를 직접 실행. `ParquetFilters.convert(schema, Expressions.equal("b", true), true)`와 `notEqual("b", false)` 둘 다 `UnsupportedOperationException: Type not supported yet: java.lang.Boolean`로 실패함을 확인(후보 주장과 100% 일치). 이후 제안된 한 줄 수정(`value instanceof Number || value instanceof Boolean`)을 실제로 적용해 같은 테스트가 예외 없이 통과함도 확인. 검증 후 테스트 파일 삭제, 소스 파일 원복(`git status` clean 확인).
  - 기존 테스트 커버리지 부재 확인: `TestParquet.java`에 boolean 타입 필터 pushdown을 다루는 테스트가 전혀 없음(grep 결과 `ParquetFilters` 언급 1곳, filter 관련 테스트는 다른 타입만 다룸) — 이 버그가 지금까지 테스트로 잡히지 않은 이유가 명확함.
  - revapi/API: `ParquetFilters`는 package-private 클래스, `getParquetPrimitive`는 private static — 공개 API 변경 없음. `parquet/`는 REVAPI 대상 모듈이지만 이 변경은 revapi 검사 대상 표면에 해당하지 않음.
  - 중복 확인: `gh search prs/issues --repo apache/iceberg "getParquetPrimitive"/"boolean filter parquet"/"Type not supported yet"` 등으로 확인, 이 버그를 다루는 별도 open PR/issue 없음. 후보가 제외 목록에 적은 issue #16032(PR #16110 선점)·#16035(PR #16621 선점)는 각각 AlwaysFalse·decimal/UUID를 다루며 겹치지 않음.
  - PR #16621 diff 직접 확인(`gh pr diff 16621`): `getParquetPrimitive`의 `Number`/`CharSequence`/`ByteBuffer` 분기는 그대로 유지되고 TODO 주석만 제거됨 — decimal/UUID는 별도 `decimalPred`/`uuidPred`로 분리 처리하지만 `Boolean`은 여전히 미처리 상태로 남아 있음(후보 주장 재확인됨). 단, #16621은 `ParquetFilters.convert()`의 시그니처를 `Schema`→`MessageType`으로 바꾸는 광범위한 리팩터라서, 이 후보 PR과 #16621이 모두 머지될 경우 같은 파일에서 사소한 rebase 충돌 가능성은 있음(치명적이지 않음, 기능 중복 아님).
  - PR 제목 `Parquet: Fix boolean equality filter pushdown throwing UnsupportedOperationException` — `Module: Description` 형식 준수.
  - 변경 크기: 조건문에 `|| value instanceof Boolean` 1개 추가, 다른 분기 미변경 — 매우 작고 집중됨.
  - 모듈 경계: `parquet/` 내부 filter-pushdown 로직만 수정, 엔진 개념 누출 없음.
  - 재현 가능한 실사용 경로: `case BOOLEAN`(line 131-139)에서 `EQ`/`NOT_EQ`가 무조건 `getParquetPrimitive`를 호출하므로 `WHERE bool_col = true`류의 일반적인 predicate가 항상 이 경로를 탄다. Boolean literal(`Literals.BooleanLiteral`)이 `java.lang.Boolean`을 반환하는 것도 API 코드로 확인됨(후보 인용 그대로).
- **CAUTION 시 필수 수정**: 해당 없음(GO).
- **spec 게이트**: exempt — `format/`·`open-api/rest-catalog*` 미변경, `parquet/` 내부 버그 수정.
- **revapi/공개 API 영향**: `parquet/`는 REVAPI 대상 모듈이나, `ParquetFilters`(package-private)·`getParquetPrimitive`(private static)만 수정하므로 공개 API 추가/변경 없음. revapi 통과 예상. 24h 대기 대상 아님.
- **probe-first**: 해당 없음(단발 버그 수정, 확산시킬 형제 엔진/파일포맷/카탈로그 패턴 아님). 단, contributor 구현 시 실제 파일 기반 read 테스트(임시 boolean 컬럼 Parquet 파일 write→filter read)로 커버리지를 남기고, PR #16621과의 잠재적 파일 충돌(같은 `ParquetFilters.java`)을 인지해 별도 PR로 독립 제출할 것을 권고.

## 마감 (contrib-validate): VALID — 제출됨 issue=https://github.com/apache/iceberg/issues/17092 pr=https://github.com/apache/iceberg/pull/17093
