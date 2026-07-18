<!-- Comment:
impl 파이프라인 P7이 저장하는 이슈 초안 템플릿. 제출은 /impl-validate가
`gh issue create --title "{type}: {제목}" --body-file <이 파일 본문>`으로 수행한다.
- 본문은 한국어. CLAUDE.md '사람이 검증하는 텍스트 작성 규칙' 준수(상투어·번역투 금지).
- 본문 단어수 ≤ impl-config.yml doc_limits.issue_max_words. 작성 후 wc -w로 확인.
- 안내용 주석과 하단 헬퍼 블록은 GitHub 본문에 넣지 않는다.
-->

# {type}: {한국어 제목}

## 목표
{무엇을 왜 — 1~2문장}

- 입력 문서: {경로 링크 또는 N/A(자연어 입력)}
- spec: pipeline/output/{work-key}/spec.md

## 완료 기준
<!-- spec 수용 기준과 1:1. PR이 이 항목들을 하나씩 입증한다. -->
- [ ] {완료 기준 1}
- [ ] {완료 기준 2}

## 범위 제외
{spec "범위 경계"의 제외 항목 — 없으면 "없음"}

<!-- ── 파이프라인 제출 보조 (GitHub 본문 제외, validate 스킬이 사용) ──
work-key: {work-key}
branch: {type}/{slug}
제출(실행 금지 — /impl-validate만 실행한다):
  gh issue create -R thswlsqls/tech-n-ai-backend --title "{type}: {제목}" --body-file <본문 추출 tmp>
-->
