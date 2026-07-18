<!-- Comment:
이 파일은 Apache Iceberg의 실제 PR 관행을 미러링한 템플릿이다.
Iceberg는 .github에 PR 템플릿이 없고 루트 CONTRIBUTING.md는 스텁이다 — 실제 PR이 권위 출처다.
실제 PR 제목은 "Core: Fix ...", "Spark: Add ...", "Docs: Update ..." 처럼 'Module: Description' 형식이다.
contributor는 아래를 채우되, 안내용 <!-- ... --> 주석은 최종본에서 삭제한다.

[파이프라인 규칙]
- 본문은 영문으로 작성한다(제목·섹션 제목·불릿·Testing done 모두). 이 초안은 apache/iceberg에 제출되는 산출물이다. 하단의 "제출 보조" 헬퍼 주석만 사람용이라 한글 허용(본문 아님, GitHub 제출 안 됨).
- 제목: 'Module: Description'. 모듈 접두사(Core/API/Spark/Flink/Docs/Build 등) + 무엇을 했는지. 여러 모듈이면 "Core, Spark: ...".
- 본문(아래 "Summary") 단어수 ≤ contrib-config.yml doc_limits.pr_max_words(기본 200). staleness·제출 블록 제외.
- 한 줄 = 한 사실. 관련 이슈/머지 PR/형제 코드 링크 1줄(있으면).
- AI 보조 작업이면 커밋 메시지에 Generated-by: <tool> 토큰을 넣는다.
-->

# <Module: Description — 예 "Core: Fix manifest list caching">

<!-- Comment: Closes # 처리(세 경우).
     1) 이슈 초안을 함께 만든 비-오타 변경: 아래 줄을 두고, 사람이 issues/ 초안을 제출해 받은 번호로 <issue-number>를 채운다.
     2) 동일 주제 open 이슈를 참조: 그 번호를 바로 적는다.
     3) 단순 오타 수정(PR-only, 이슈 없음): 아래 줄 대신 사유 1줄로 대체. 예: "<!-- No issue: typo fix, issue not required -->" -->
Closes #<issue-number>

## Summary

- <무엇을 왜 바꿨는지 — 한 줄=한 사실>
- <형제 구현/스펙과의 정합: 예 "matches the Parquet reader which already does X">
- <관련 링크: 이슈 #N / 머지 PR #N / 형제 코드 경로>

## Testing done

<!-- 실제로 한 것만. 허위 금지. -->
- Added <TestClass#method> covering <positive/negative case>.
- `./gradlew <:module>:check` — <N> tests passed.
- <REVAPI 모듈 변경 시> `./gradlew <:module>:revapi` — passed (backward compatible).
- <coverage-gap이면> Passes before and after; fills a coverage gap (cite production boundary).
<!-- 단순 오타 수정(주석·Javadoc·*.md·비동작 문자열의 철자/표현 교정, dead {@link})이면 위 줄 대신 아래 한 줄만. 동작 변화가 없어 테스트를 추가하지 않는다(허위 "Added test" 금지). -->
- <typo-fix면> Typo/wording fix, no behavior change — no test added. `./gradlew <:module>:spotlessCheck` passed.

<!-- ======================= 파이프라인 제출 보조(단어수 예산 제외, 본문 아님) ======================= -->
<!-- 아래 블록은 GitHub PR 본문에 넣지 않는다. 사람이 제출 전 실행하는 헬퍼다. -->
<!-- Branch: <type/slug> — contributor가 커밋한 작업 브랜치(예 fix/manifest-caching). 제출 시 이 브랜치를 fork(origin)에 push한다. -->

```bash
# 제출 전 실행 — 브랜치가 stale한지 확인
git fetch upstream
git log --oneline HEAD..upstream/main -- <changed-files>   # 비어있음=안전, 출력=먼저 rebase
```
```bash
# 사람이 issue 번호 갱신(있으면) 후 실행.
# 첫 기여면 ASF ICLA/CCLA 안내 참조 → https://www.apache.org/licenses/contributor-agreements.html
git push -u origin <type/slug>             # 작업 브랜치를 fork(origin)에 push
gh pr create -R apache/iceberg --base main --head <type/slug> --title "<Module: Description>" --body-file <this-file-body>
```
