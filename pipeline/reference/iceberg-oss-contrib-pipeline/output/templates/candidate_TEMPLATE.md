<!--
파일명 규칙: iceberg-<module-slug>_기여_후보.md   (예: iceberg-core_기여_후보.md; :iceberg-core→iceberg-core, :iceberg-spark:spark-4.1_2.13→spark-4.1)
- 모듈 하나당 파일 하나, 후보가 여러 개면 "기여 후보 #N" 섹션을 반복
- 분석 후 제외한 후보는 "제외된 후보" 섹션에 이유와 함께 기록(선택)
- 모든 코드 위치/AS-IS/TO-BE/비교대상은 실제 소스에서 채운다. 빈칸·추측 금지.
-->

# iceberg-<module-slug> 기여 후보 분석

<!-- (선택) 분석했지만 제외한 후보가 있으면 작성. 없으면 섹션 삭제 -->
## 제외된 후보: <제외된 후보 한 줄 설명>

### 제외 이유
1. **<이유 1>**: <근거>
2. **<이유 2>**: <근거>

---

## 기여 후보 #1

**후보 유형**: <wrong-output | logic-error | github-backed | dead-ref-docs | api-contract | npe-guard-fallback | typo-fix>
<!-- typo-fix = 의미·동작 불변 철자/표현 교정(주석·Javadoc·*.md·비동작 문자열). 테스트 없음·PR-only(이슈 생략). 동작이 바뀌면 typo-fix 아님. -->

**spec 게이트**: <exempt:bug-fix|internal|docs|parity | gated:format-spec-change|rest-openapi-spec-change> — <한 줄 사유>
**공개 API 영향(revapi)**: <yes — REVAPI 모듈(api/core/parquet/orc/common/data), 호환성 검사 필요 | no> / **공개 API 추가**: <yes — 커미터 24h 대기 대상 | no>
**PR 제목(Module: Description)**: <예 "Core: Fix ...">

### 요약
<문제를 한 문장으로: 어떤 클래스/메서드에서 무엇이 잘못되어 어떤 결과가 발생하는지>

### 근거
- **코드 위치**: `<클래스명>.<메서드명>()` (`<상대경로>` line <N>)
- **문제 증거**:
  - <코드에서 직접 확인한 사실 1>
  - <코드에서 직접 확인한 사실 2>
- **비교 대상**: `<같은 repo 내 올바른 패턴 — 형제 엔진/파일포맷/카탈로그/메서드>` (line <N>) — <어떻게 올바른지>
- **GitHub 근거(있으면)**: <issue #N 또는 머지 PR #N의 incomplete-fix>

### 현재 구현 (AS-IS)
```java
// 문제가 되는 현재 코드를 그대로 붙여넣고, 문제 라인에 주석으로 표시
```
**문제점**:
1. <문제점 1>
2. <문제점 2>

### 제안 수정 (TO-BE)
```java
// 제안하는 수정 코드 1개만 (before/after 중복 금지)
```
**개선 사항**:
1. <개선 1>
2. <개선 2>

### NPE-guard-fallback 전용 입증 (후보 유형이 npe-guard-fallback일 때만 필수)
- **realistic-trigger**: <null/blank가 정상 호출경로에서 도달함을 코드로 증명 — 공개 API 시그니처·실제 호출자>
- **exhausted-higher**: <같은 스코프에서 prefer_classes를 탐색했고 없었음을 한 줄로>

### 리뷰 가치 평가
- **왜 머지될 만한지**: <명확한 버그인지 / 실제 발생 시나리오 / 기존 코드·스펙과의 일관성 / 커미터 1명이 바로 승인할 명료함>
- **왜 과도한 변경이 아닌지**: <변경 범위 제한(한 PR=한 주제) / 후방호환 / api breaking 아님 / 모듈 경계 준수>
- **테스트 계획**: <어떤 유닛 테스트(JUnit5+AssertJ, 클래스명 Test*)로 검증할지 — Docker 불필요>

<!-- 파일 끝: 오케스트레이터가 읽을 점수표 한 줄 (반드시 이 형식).
     각 항목과 합계는 반드시 "숫자"로 기입한다. "합계"라는 글자를 그대로 두지 마라(파서가 숫자만 읽음).
     분자=다섯 항목 합, 분모=항상 25. 예시: 점수: 명확성 4 / 영향 2 / 머지용이성 5 / 테스트가능성 5 / 리스크낮음 5 = 21/25 -->
점수: 명확성 <N> / 영향 <N> / 머지용이성 <N> / 테스트가능성 <N> / 리스크낮음 <N> = <합계숫자>/25
