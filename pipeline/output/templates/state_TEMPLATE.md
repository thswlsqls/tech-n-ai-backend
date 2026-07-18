<!-- Comment:
impl 파이프라인이 작업 하나를 실행하며 채우는 상태 문서다. 재실행 시 오케스트레이터가
이 문서를 읽고 끝난 단계를 건너뛴다(P0 재실행 안전의 기준점).
- 단계가 끝날 때마다 "단계 이력"에 한 줄 append. 기존 줄은 고치지 않는다.
- 상태 집합·소유자는 impl-config.yml work_state 참고.
- 안내용 주석은 첫 기록 후 지운다.
-->

# State: {work-key} — {제목}

- **입력**: {docs 경로 | task-NN | issue #N}
- **spec**: pipeline/output/{work-key}/spec.md
- **현재 상태**: analyzed
- **영향 모듈**: {:api-... (P2 확정)}
- **브랜치**: {type}/{slug} (P5 기입)
- **worktree**: {경로} (P5 기입)
- **run**: {RUN_ID} (초안이 저장된 run 폴더)
- **이슈**: #{번호} (validate 제출 후 기입)
- **PR**: #{번호} (validate 제출 후 기입)

## 단계 이력
| 시각 | 상태 | 결과 |
|------|------|------|
| {MM-DD HH:mm} | analyzed | spec 정규화, 수용 기준 {N}개, CQRS 영향 {요약} |
<!-- designed / implemented / reviewed / pushed / submitted / merged|closed 순으로 append.
     designed: 승인된 설계 요지 한 줄. implemented: 커밋 해시·모듈별 테스트 결과.
     reviewed: 리뷰어 수·confidence≥80 지적 수·수정 여부. pushed: push 브랜치·초안 경로.
     submitted: 이슈#·PR#(validate가 기록). merged|closed: 폴링 결과(다음 실행 P0가 기록). -->

## 검증 결과
- 자동: {모듈별 테스트 커맨드와 숫자 결과}
- 수동: {사용자가 확인할 항목 / 없음과 이유}

## 비고
- {가정·설계 결정·남은 수작업}
