# Spec-Impl Pipeline — Memory (closed-loop)

이 파이프라인이 반복할수록 똑똑해지는 단일 메커니즘이다. 별도 DB·통계엔진을 만들지 않는다(마크다운 한 장).
기록 규칙: 사실만, 한 줄씩 **append**. 기존 줄 수정 금지 — 단 §0의 pending→merged/closed 졸업만 예외(P0 폴링).

- §0 제출 추적: validate 스킬이 PR 제출 후 한 줄 추가. 다음 실행 P0가 `gh pr view`로 머지/리젝을 졸업시킨다.
- §1 구현 레지스트리: 이미 구현한 issue(중복 구현 방지). 오케스트레이터 P0/P7이 본다/적는다.
- §2 캘리브레이션: 어떤 스펙-구현 패턴이 머지/리젝됐나, 리뷰 피드백.
- §3 repo/모듈 메모: 구현 중 관찰한 모듈 특성·패턴.
- §4 프로세스 교훈: 빌드 실패·규약 위반 등.

---

## §0 제출 추적 (submission-tracking)
<!-- validate 스킬이 제출 후: `- [<module>] issue=#<N> PR=#<M> branch=<name> status=pending finalize=<date>` -->
<!-- 다음 실행 P0가 status를 merged|closed로 졸업 -->

## §1 구현 레지스트리 (implemented-registry)
<!-- 오케스트레이터 P7: `- [<module>] <issue-key> <요약> | branch=<name> | commit=<hash>` -->

## §2 캘리브레이션 (calibration)
<!-- P0 졸업/리뷰: `- [<module>] <패턴> → merged|rejected | issue=#<N>` -->

## §3 repo/모듈 메모 (repo-memo)
<!-- `- [<module>] <관찰>` -->

## §4 프로세스 교훈 (process-lesson)
<!-- `- <빌드 실패·규약 위반·반복 실수 교훈>` -->
