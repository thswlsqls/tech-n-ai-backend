# iceberg-parquet 기여 후보 분석

## 이번 라운드 결론: 새 codebase-gaps 후보 없음

`:iceberg-parquet` 모듈은 과거 run(20260621111142)에서 codebase-gaps + issues-and-merged 두 focus로 이미 훑였고, 그 결과 5건(제출완료 1건, 선점 2건, 별도 검토중 1건, false-positive 1건)이 제외 목록으로 지정되어 재발굴이 금지된 상태다. 이번 라운드에서는 제외 목록과 겹치지 않는 새 파일·최근 커밋(geometry/geography WKB 매핑, #16982·#16765)까지 넓게 훑었으나, "정상 입력에 잘못된 결과를 내는" 수준의 명확한 로직 버그를 새로 찾지 못했다.

### 조사 범위
- 신규 기능: `TypeToMessageType`/`MessageTypeToType`의 GEOMETRY/GEOGRAPHY 로직(#16765), `BaseParquetReaders`/`BaseParquetWriter`의 WKB read/write 경로(#16982) — 대칭적으로 구현되어 있음을 확인(정상 동작, 후보 아님).
- 이전에 안 훑인 파일: `ParquetIO`, `ParquetWriteSupport`, `ParquetReadSupport`, `ParquetTypeVisitor`, `TypeWithSchemaVisitor`, `ParquetFilters`, `ParquetWriteAdapter`, `ParquetUtil`, `ParquetCodecFactory`, `ParquetFormatModel` — 직접 정독.
- 서브에이전트로 24개 파일(`ParquetValueReaders`/`Writers`, `ParquetVariantReaders`/`Writers`, `VariantReaderBuilder`/`WriterBuilder`, `VariantShreddingAnalyzer`, `ParquetSchemaUtil`, `PruneColumns`, `ApplyNameMapping`, `RemoveIds`, `ParquetMetrics`, `ParquetConversions`, `ParquetDictionaryRowGroupFilter`, `ParquetMetricsRowGroupFilter`, `ReadConf`, `ParquetWriter`, `ParquetReader`, `Parquet.java` 빌더 로직 등) 정독 및 형제 메서드 대조.

---

## 제외된 후보: TypeToMessageType.map()의 field() 중복 호출

**후보 유형**: 해당 없음 (기준 미달로 최종 후보에서 제외)

### 조사 내용
- **코드 위치**: `TypeToMessageType.map()` (`parquet/src/main/java/org/apache/iceberg/parquet/TypeToMessageType.java` line 154-168)
```java
public GroupType map(MapType map, Type.Repetition repetition, int id, String name) {
  NestedField keyField = map.fields().get(0);
  NestedField valueField = map.fields().get(1);
  Type keyType = field(keyField);
  Preconditions.checkArgument(keyType != null, "Cannot convert key Parquet: %s", keyField.type());
  Type valueType = field(valueField);
  Preconditions.checkArgument(
      valueType != null, "Cannot convert value Parquet: %s", valueField.type());

  return Types.map(repetition)
      .key(field(keyField))     // keyType을 계산해놓고 재사용하지 않고 field()를 다시 호출
      .value(field(valueField)) // valueType도 마찬가지
      .id(id)
      .named(AvroSchemaUtil.makeCompatibleName(name));
}
```
- **비교 대상**: 바로 위 형제 메서드 `list()` (line 142-152)는 `elementType`을 한 번 계산해 `.element(elementType)`에서 재사용한다. `map()`만 계산한 값을 null 체크에만 쓰고 버린 뒤 `field()`를 다시 호출한다.

### 제외 이유
1. **영향 없음(명확성/영향 미달)**: `field(NestedField)`는 순수 함수라 동일 입력에 대해 구조적으로 동일한 `Type`을 반환한다. 현재 트리의 어떤 `VariantShreddingFunction` 구현도 부작용이나 비결정성이 없어, 두 번 호출해도 결과나 동작에 관측 가능한 차이가 없다(중복 연산일 뿐, 오류 아님).
2. **prefer_classes 미해당**: wrong-output(정상 입력에 잘못된 결과)에 해당하려면 두 호출 결과가 달라지는 시나리오가 있어야 하는데, `variantShreddingFunc`가 호출 시마다 다른 값을 반환하는 실제 사용처가 없어 입증 불가능하다.
3. 억지로 "잠재적 버그"로 포장하지 않고 정직하게 제외한다(candidate_quality 취지: NPE-guard든 다른 약한 신호든 영향이 입증 안 되면 후보로 올리지 않는다).

---

(이번 라운드는 위 조사 항목 외에 기준(≥18/25)을 넘는 새 codebase-gaps 후보가 없다. 억지 후보를 만들지 않고 빈 결과로 보고한다.)
