<!-- Comment:
impl 파이프라인 P7이 저장하는 PR 초안 템플릿. 제출은 /impl-validate가 이슈를 먼저 만들고
받은 번호를 Closes에 채워 `gh pr create --body-file <본문>`으로 수행한다.
- 제목은 커밋 제목과 동일 형식: "{type} : [main] {한국어 설명}".
- 본문은 한국어. 주장 말고 실행 결과(숫자)를 적는다.
- 본문 단어수 ≤ impl-config.yml doc_limits.pr_max_words. 작성 후 wc -w로 확인.
- 안내용 주석과 하단 헬퍼 블록은 GitHub 본문에 넣지 않는다.
-->

# {type} : [main] {한국어 설명}

Closes #{이슈번호}
<!-- validate가 이슈 제출 후 실번호로 치환한다. placeholder를 지우지 말 것. -->

## 구현 요약
<!-- 무엇을 왜 바꿨는지. 한 줄 = 한 사실. CQRS 관통이면 쓰기→이벤트→읽기 반영 흐름을 남긴다. -->
- {변경 요약 1}
- {변경 요약 2}

## 테스트
<!-- 실제 실행한 커맨드와 숫자 결과. 영향 모듈 전부. -->
- `./gradlew {module}:test`: {N tests, 0 failures}
- 수용 기준 ↔ 테스트 매핑: {AC1 → TestClass.method, ...}

## 리뷰
{impl-reviewer 수(초점)와 결과 — confidence≥80 지적 수, 수정 여부}

## 수동 확인
{사용자가 직접 확인할 절차(로컬 docker compose 등). 없으면 "없음"과 이유}

<!-- ── 파이프라인 제출 보조 (GitHub 본문 제외, validate 스킬이 사용) ──
work-key: {work-key}
branch: {type}/{slug}
commit: {해시}
worktree: {경로}
staleness 체크(제출 전 실행 — 출력이 비면 안전, 있으면 rebase 먼저.
반드시 worktree 안에서 실행한다 — 본 트리의 HEAD는 main이라 항상 비어 보인다):
  git -C {worktree} fetch origin
  git -C {worktree} log --oneline HEAD..origin/main -- {변경 파일들}
제출(실행 금지 — /impl-validate만 실행한다):
  gh pr create -R thswlsqls/tech-n-ai-backend --base main --head {type}/{slug} \
    --title "{type} : [main] {설명}" --body-file <본문 추출 tmp>
-->
