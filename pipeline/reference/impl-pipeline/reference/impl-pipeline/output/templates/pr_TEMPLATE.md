<!-- Comment:
Apache Iceberg의 실제 PR 관행을 미러링한 템플릿이다(.github에 PR 템플릿 없음, 루트 CONTRIBUTING.md는 스텁).
실제 PR 제목은 "Core: Add ...", "Spark: Fix ..." 처럼 'Module: Description' 형식이다.
implementer는 아래를 채우되, 안내용 <!-- ... --> 주석은 최종본에서 삭제한다.

[파이프라인 규칙]
- 본문은 영문으로 작성한다(제목·섹션·불릿·Testing done 모두). apache/iceberg 제출 산출물이다.
  하단 "제출 보조" 헬퍼 주석만 사람용이라 한글 허용(본문 아님, GitHub 제출 안 됨).
- 제목: 'Module: Description'. 모듈 접두사(Core/API/Spark/Flink/Docs/Build 등) + 무엇을 했는지.
- 본문(Summary) 단어수 ≤ impl-config.yml doc_limits.pr_max_words(기본 200). staleness·제출 블록 제외.
- 한 줄 = 한 사실. 이 파이프라인은 issue 입력이 전제이므로 Closes #<number>를 항상 채운다.
- AI 보조 작업이면 커밋 메시지에 Generated-by: <tool> 토큰을 넣는다(PR 본문엔 disclosure 블록 금지).
-->

# <Module: Description — 예 "Core: Add manifest list caching">

Closes #<issue-number>

## Summary

- <무엇을 왜 바꿨는지 — 한 줄=한 사실>
- <형제 구현/스펙과의 정합: 예 "matches the Parquet reader which already does X">
- <관련 링크: 머지 PR #N / 형제 코드 경로(있으면)>

## Testing done

<!-- 실제로 한 것만. 허위 금지. 수용 기준 ↔ 테스트 매핑을 한 줄로. -->
- Added <TestClass#method> covering <AC1: ...> (and <AC2: ...>).
- `./gradlew <:module>:check` — <N> tests passed.
- <REVAPI 모듈 변경 시> `./gradlew <:module>:revapi` — passed (backward compatible).
<!-- 단순 문서/주석 변경(동작 불변)이면 위 줄 대신: -->
- <docs/comment-only면> Docs/comment change, no behavior change — no test added. `./gradlew <:module>:spotlessCheck` passed.

<!-- ======================= 파이프라인 제출 보조(단어수 예산 제외, 본문 아님) ======================= -->
<!-- 아래 블록은 GitHub PR 본문에 넣지 않는다. validate 스킬이 VALID 판정 후 실행하는 헬퍼다. -->
<!-- Branch: <type/slug> — implementer가 커밋한 작업 브랜치. 제출 시 이 브랜치를 fork(origin)에 push한다. -->

```bash
# 제출 전 실행 — 브랜치가 stale한지 확인
git fetch upstream
git log --oneline HEAD..upstream/main -- <changed-files>   # 비어있음=안전, 출력=먼저 rebase
```
```bash
# validate 스킬이 VALID 판정 후 실행(파이프라인·implementer는 실행 금지).
# 첫 기여면 ASF ICLA/CCLA 안내 → https://www.apache.org/licenses/contributor-agreements.html
git push -u origin <type/slug>             # 작업 브랜치를 fork(origin)에 push
gh pr create -R apache/iceberg --base main --head <fork-owner>:<type/slug> --title "<Module: Description>" --body-file <this-file-body>
```
