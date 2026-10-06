## Terraform (IaC) — `devops/terraform/`
AWS 인프라는 Terraform으로 관리한다. 세 부분으로 나뉜다.
- `bootstrap/` — Terraform 상태 저장용 S3·KMS, ECR, GitHub Actions용 OIDC 역할처럼 다른 모든 것보다 먼저 있어야 하는 리소스. 한 번만 적용한다.
- `modules/` — 재사용하는 리소스 묶음: `network`, `aurora-mysql`, `elasticache-valkey`, `msk-serverless`/`msk-provisioned`(Kafka), `ecs-service`, `cloudfront-spa`, `amplify-app`, `s3-bucket`, `iam-role-workload`, `observability`.
- `envs/{dev,beta,prod}/` — 환경별로 위 모듈을 엮어 실제 인프라를 정의한다. 환경마다 상태가 분리돼 있다.

리팩토링할 때는 `terraform plan`이 아무 변경도 만들지 않는지(no-op)로 동작이 그대로인지 확인한다.

## AWS 아키텍처 다이어그램 — `devops/aws/{dev,beta,prod}/`
환경별로 네트워크 구성, 참조 아키텍처, 보안, 관측 다이어그램을 `.drawio`와 `.png`로 둔다. 인프라를 바꾸면 이 다이어그램도 같이 맞춘다.
