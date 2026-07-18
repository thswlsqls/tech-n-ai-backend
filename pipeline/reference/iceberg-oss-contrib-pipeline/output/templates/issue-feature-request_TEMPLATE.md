<!--
Apache Iceberg 기능/개선 제안 초안 템플릿.
이 구조는 리포의 .github/ISSUE_TEMPLATE/iceberg_improvement.yml 의 핵심 필드를 미러링한 것이다.
- 영문으로 작성. 굵은 글씨 필드 구조를 유지한다.
- 안내용 <!-- ... --> 주석은 최종본에서 삭제한다.
- 본문(제출 코드블록 제외) ≤ contrib-config.yml doc_limits.issue_max_words.
- format/ 또는 open-api/rest-catalog* 스펙 변경(spec_gate: gated)은 PMC 투표(찬성 3표) 영역 —
  먼저 dev@iceberg.apache.org 메일링 리스트 discussion 또는 improvement proposal을 여는 것이 원칙.
  아래 "Governance" 필드에 그 상태를 명시한다.
-->

**Feature Request / Improvement Proposal**
<The high-level problem this solves. Cite the current limitation: ClassName.method() (path line N).>

**Query engine**
<e.g. Spark / Flink / Core (engine-agnostic) — where this applies>

**Describe the solution you'd like**
<What you propose, kept small and focused (one concern). Mention backward compatibility:>
<- new interface methods carry a default implementation; deprecations use @Deprecated + @deprecated javadoc.>
<- prefer table/catalog properties over new public API where possible.>

**Describe alternatives you've considered**
<Other approaches and why this one. N/A if none.>

**Governance**
<Does this touch format/ or open-api/rest-catalog* (spec)? If so (gated), state that a PMC vote / dev@ discussion is needed first. Otherwise: normal PR.>

**Additional context**
- Affected module: <:iceberg-core | :iceberg-api | :iceberg-spark:spark-4.1_2.13 | ...>
- Public API impact (revapi): <yes — REVAPI module | no>
- Backward compatible: <yes | deprecation only>

---
<!-- Branch: <type/slug> — 이 이슈에 딸린 PR이 작업 중인 브랜치(contributor가 커밋한 곳). 사람이 추적용으로 참고. -->
```bash
# 사람이 검토 후 실행 — 파이프라인은 제출하지 않는다
gh issue create -R apache/iceberg --title "<Module: one-line summary>" --body-file <this-file-body>
```
