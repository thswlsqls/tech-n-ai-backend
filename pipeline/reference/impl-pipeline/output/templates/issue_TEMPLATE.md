<!-- Comment:
run-task 5단계가 `gh issue create --body`에 쓰는 템플릿.
{NN}/{slug}/{task 제목}과 본문 내용은 해당 task 문서에서 채운다.
제목 형식은 config git.issue.title_format: "Task {NN}: {task 제목}".
안내용 주석은 본문에 넣지 않는다.
-->

## 목표
{task 문서 "목표" 절 요약 1~2문장}

- task 문서: [docs/tasks/task-{NN}-{slug}.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/tasks/task-{NN}-{slug}.md)
- prompt 문서: [docs/prompts/prompt-{NN}-{slug}.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/prompts/prompt-{NN}-{slug}.md)

## 완료 기준
<!-- task 문서 "완료 기준" + prompt "성공 기준"을 검증 가능한 체크리스트로. PR이 이 항목들을 하나씩 입증한다. -->
- [ ] {완료 기준 1}
- [ ] {완료 기준 2}

## 범위 제외
{task 문서 "범위 제외" — 다음 task로 미루는 것들}
