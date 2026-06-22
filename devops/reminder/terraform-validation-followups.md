# Terraform 검증 후속 항목 (2026-06-22)

`devops/terraform` 전체를 AWS 공식 문서와 정적 검사 도구(terraform validate·fmt, tflint, trivy)로
검증한 결과 중, **이번에 코드로 고치지 않고 남겨둔 항목**을 모아둔다.
검증·개선에 쓴 단계별 프롬프트는 `devops/prompts/20260622114702/`에 있다.

이번에 반영한 변경은 두 개뿐이다.
- `fix`: `bootstrap/state.tf` lifecycle rule 에 빈 `filter {}` 추가 (validate 경고 해소)
- `chore`: `terraform fmt` 로 37개 파일 포맷 정규화

아래는 판단·정책 결정이 필요하거나, 지금 당장은 손대지 않기로 한 것들이다.

---

## 1. 출시 전 반드시 처리 — prod ALB HTTPS 전환 (우선순위 높음)

**무엇**: `envs/beta/cluster.tf`·`envs/prod/cluster.tf`가 `envs/dev/cluster.tf`와 완전히 같다.
세 환경 모두 ALB가 HTTP 80 만 받고, HTTPS(443)·ACM 인증서가 없으며, `enable_deletion_protection = false`다.
파일 주석도 "(dev)" 그대로 남아 있다.

**왜 지금 안 고쳤나**: 의도된 보류다. `envs/prod/README.md`에 "도메인 보유 후 HTTPS 전환"으로 적혀 있다.
ACM 인증서의 DNS 검증은 도메인이 있어야 하므로, 도메인 확보 전에는 HTTPS 리스너를 만들 수 없다.

**언제·무엇을 해야 하나** (도메인 확보 시점):
1. ACM 인증서 발급 — ALB 용은 서울 리전(`ap-northeast-2`), CloudFront 용은 `us-east-1`
2. prod ALB 에 HTTPS(443) 리스너 추가, HTTP(80) → HTTPS 리다이렉트로 변경
3. prod ALB SG 인바운드를 80 에서 443 으로 교체
4. prod ALB `enable_deletion_protection = true` 로 변경
5. cluster.tf 의 "(dev)" 주석을 환경에 맞게 정리

근거 문서: `envs/prod/README.md` 의 "도메인·HTTPS 보강 (도메인 보유 후)" 절.

---

## 2. Terraform apply Role 에 Permission Boundary 부착 검토

**무엇**: `bootstrap/roles.tf` 의 `gha-terraform-apply-{env}` Role 이 `PowerUserAccess` 관리형 정책 +
IAM 관리 권한(`resources = ["*"]`)을 가진다. 권한 범위를 좁히는 permission boundary 가 붙어 있지 않다.

**현재 상태**: `bootstrap/variables.tf` 에 `permissions_boundary_managed` 변수가 **선언만** 되어 있고
어디에도 연결되지 않았다 (tflint 가 미사용 변수로 잡음). boundary 를 붙일 자리를 만들어 둔 흔적으로 보인다.

**결정할 것**: boundary 정책을 정의하고 apply Role 에 부착할지. Terraform apply 는 본래 넓은 권한이 필요해
무작정 줄이면 apply 가 깨질 수 있으므로, boundary 정책 설계가 선행돼야 한다.

근거 문서: AWS IAM Best Practices — <https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html>

---

## 3. prod ElastiCache 인증을 RBAC 로 전환 검토

**무엇**: `envs/prod/terraform.tfvars` 가 cache `auth_mode` 를 지정하지 않아 기본값 `auth_token` 을 쓴다.
`modules/elasticache-valkey/variables.tf` 의 변수 설명은 prod 에 RBAC(User Group)를 권장한다.

**결정할 것**: prod 만 `auth_mode = "rbac"` 로 바꿀지. RBAC 로 가려면 User Group·User 리소스를 추가로
만들어야 한다 (현재 모듈은 `rbac_user_group_ids` 를 입력으로만 받는다).

근거 문서: ElastiCache RBAC — <https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/Clusters.RBAC.html>

---

## 4. 천천히 정리해도 되는 것 (낮은 우선순위)

- **`aws_security_group_rule` → `aws_vpc_security_group_*_rule` 마이그레이션**
  8개 파일이 옛 방식을 쓴다. 공식 deprecation 은 아니고 provider 가 새 리소스를 권장하는 수준이라
  지금 동작에는 문제없다. AWS Provider 6.0 으로 올릴 때 함께 처리하는 게 낫다. 리소스 주소가 바뀌어
  `terraform state mv` 가 필요하므로 한 번에 일괄로 한다.

- **미사용 선언 정리** (tflint 가 잡은 것)
  - `bootstrap/variables.tf` — `permissions_boundary_managed` (위 2번과 연결, 쓰게 되면 해소)
  - `modules/cloudfront-spa/variables.tf` — `default_ttl_seconds`
  - `envs/{dev,beta,prod}/services.tf` — `local.all_workload_sg_ids`
  기존 코드라 이번엔 건드리지 않았다. 의도된 자리표시인지 확인 후 지울지 결정한다.

- **`.terraform.lock.hcl` 버전 관리 여부**
  검증 중 `terraform init` 으로 `bootstrap/`·`envs/dev/` 에 lock 파일이 생겼다. 보통 lock 파일은
  레포에 커밋해 provider 버전을 고정하는 게 권장된다. 팀 정책에 맞게 커밋할지 `.gitignore` 에 둘지 정한다.

---

## 검증에서 문제없이 확인된 영역 (참고)

다시 볼 필요 없는, 깨끗하게 확인된 부분이다.

- 네트워크: 4티어 서브넷×3AZ, private-data 인터넷 격리, VPC Endpoint 9종(private DNS), VPCE SG 443 제한
- 데이터 암호화·인증: Aurora(CMK·IAM DB 인증·관리형 비밀번호), Valkey(전송+저장 암호화·토큰을 Secrets Manager 저장), MSK(TLS·IAM SASL·KRaft)
- 컴퓨팅: Fargate ARM64, `assign_public_ip=false`, deployment circuit breaker, CPU·메모리 오토스케일
- 배포: Blue/Green 자동 롤백이 배포 실패 + 알람으로 활성, 5xx·p95 알람 실제 연결
- CloudFront: OAC, 보안 헤더 풀세트(HSTS preload), HTTPS 리다이렉트, SPA 404→index, OAC 한정 버킷 정책
- 관측성: 로그 그룹 retention + KMS, CPU·메모리·실행 태스크 수 알람 → SNS(KMS 암호화) 연결
- 버전 고정: Terraform·AWS provider 5.100·random 이 전 모듈에서 일관
- Aurora 엔진: v3(8.0 호환, 3.07.1) 은 지원 중 — EOL 대상인 v2(5.7) 아님
