<!--
Apache Iceberg 버그 리포트 초안 템플릿.
이 구조는 리포의 .github/ISSUE_TEMPLATE/iceberg_bug_report.yml 의 핵심 필드를 미러링한 것이다.
- 영문으로 작성. 굵은 글씨 필드 구조를 유지한다.
- 안내용 <!-- ... --> 주석은 최종본에서 삭제한다.
- 본문(제출 코드블록 제외) ≤ contrib-config.yml doc_limits.issue_max_words.
- 무관한 환경 필드는 N/A. 한 줄=한 사실, 중복 금지.
- 단순 오타 수정이 아닌 버그/parity 후보는 이 초안을 PR 초안보다 먼저 만든다(동일 주제 open 이슈가 이미 있으면 새로 만들지 말고 PR에서 참조만). 단순 오타 수정은 PR-only라 이 템플릿을 쓰지 않는다.
-->

**Apache Iceberg version**
<e.g. 1.NN.0 (latest release) / main @ <short-sha>>

**Query engine**
<e.g. Spark / Flink / None — which engine surfaces the bug, N/A if engine-agnostic>

**Please describe the bug**
<One or two sentences: which class/method behaves wrongly and the observable wrong result.>
<Reference the offending code location: ClassName.method() (path line N).>
<Cite the correct sibling pattern (other engine/file-format/catalog) or spec if relevant.>

**Steps to reproduce**
<A minimal recipe, if possible. Expected vs actual behavior.>

**Additional context**
<MRE or the exact code path that triggers the bug. N/A if covered above.>

---
<!-- Branch: <type/slug> — 이 이슈에 딸린 PR이 작업 중인 브랜치(contributor가 커밋한 곳). 사람이 추적용으로 참고. -->
```bash
# 사람이 검토 후 실행 — 파이프라인은 제출하지 않는다
gh issue create -R apache/iceberg --title "<Module: one-line summary>" --body-file <this-file-body>
```
