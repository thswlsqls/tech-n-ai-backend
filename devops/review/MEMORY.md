# devops 검증 메모리 인덱스

`review-devops` 스킬이 실행마다 갱신하는 인덱스다. 스킬은 검증을 시작하기 전에 이 파일을 읽고,
관련 knowledge 파일을 로드해 이미 검증된 사실의 재검증을 건너뛰고 거짓 양성을 다시 지적하지 않는다.

## knowledge (주제별 누적 지식)

- [aws-facts](knowledge/aws-facts.md) — AWS 공식 문서로 확인한 사실 (CloudFront 캐시 정책 ID, MSK JMX 포트) + 미확인 주장 4건
- [terraform](knowledge/terraform.md) — 버전 핀·envs 복사본 설계·매트릭스 일치, 거짓 양성 4건(permissions_boundary 등), 미해결 IaC 결함 11건(빈 IAM statement, monitoring_role_arn 등)
- [app-vs-devops](knowledge/app-vs-devops.md) — 포트·consumer 매핑 일치 확인, 코드↔인프라 불일치 6건(평문 DB 계정, k8s 식 게이트웨이 uri 등)

## runs (실행 보고서)

- [20260706063234](runs/20260706063234.md) — 첫 실행, devops 전체. 즉시 수정 7건(문서 4·terraform 3), 보고 17건, plan 불가(자격증명 없음)
