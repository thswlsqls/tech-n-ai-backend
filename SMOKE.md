# PR eval harness 연결 확인용 임시 문서

이 파일은 `tools/pr-eval/` 하니스의 게시 경로를 실측하려고 만든 것이다.
확인이 끝나면 이 PR 과 브랜치를 지운다.

## 확인하려는 것

- 봇 계정을 read collaborator 로 두고 리뷰어로 지정할 수 있는가
- `public_repo` PAT 으로 리뷰를 게시할 수 있는가
- diff 밖 앵커를 API 가 거부하는가 무시하는가
- head 보다 과거인 SHA 에 줄 앵커가 붙는가

## 덧붙임

두 번째 커밋이다. eval_sha 가 head 보다 과거가 되도록 만든다.
