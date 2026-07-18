<!-- Comment:
issue 단위 작업 상태·commit·PR 이력. 오케스트레이터(P1 analyzed → P5/P7 implemented)와
validate 스킬(validated → submitted)이 같은 파일을 단계마다 갱신한다.
상태 집합(impl-config.yml issue_state): analyzed → implemented → validated → submitted → merged|closed
- 이력 표는 append-only. 한 일이 생길 때마다 한 줄 추가(기존 줄 수정은 상태 졸업 시에만).
- 안내용 <!-- ... --> 주석은 첫 사용 후 지워도 된다.
-->

# State: <issue 제목> (issue #<number>)

- **issue**: #<number>
- **현재 상태**: analyzed
- **모듈**: <:iceberg-core 등>
- **브랜치**: <type/slug>   (구현 시작 후 기입)
- **worktree**: <WORKTREE_DIR/<branch-dash>>   (구현 시작 후 기입)
- **spec**: ./spec.md
- **PR 초안**: ./prs/<file>.md   (구현 후 기입)

## 상태 전이 이력
| 시각 | 상태 | 소유 | 비고 |
|------|------|------|------|
| <yyyy-MM-dd HH:mm> | analyzed | pipeline | spec.md 작성, 수용 기준 <N>개 |
<!-- 예시(채워질 줄):
| ... | implemented | pipeline | commit=<hash>, {module}:check <N> tests passed, revapi <pass/N-A>, PR 초안 작성 |
| ... | validated   | validate-skill | 5게이트 통과(또는 환경 제약 부분검증) |
| ... | submitted   | validate-skill | push origin <branch>, PR=<url> |
| ... | merged|closed | feedback-poll | 다음 실행 P0 폴링이 졸업 |
-->

## commit 이력
| commit | 제목 | 변경 파일 | 비고 |
|--------|------|-----------|------|
| <hash> | <Module: Description> | <files> | <테스트 추가 등> |

## PR 이력
| PR | 상태 | 비고 |
|----|------|------|
| (초안) | draft | ./prs/<file>.md |
<!-- 제출 후: | #<N> (<url>) | open→merged|closed | reviewDecision=<...> | -->
