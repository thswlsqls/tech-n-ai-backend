# terraform 트리 검증 지식

## 검증 완료
- 버전 핀: envs·bootstrap 은 `required_version = "~> 1.15.0"` + aws `~> 5.100`, modules 는 `>= 1.9` + `~> 5.100` (커밋 79b5e57). results/09·07c 문서를 2026-07-06 에 이 값으로 맞춤 — 근거: devops/terraform/envs/prod/providers.tf, modules/network/versions.tf (검증일 2026-07-06)
- 네 개 루트(envs/dev·beta·prod, bootstrap) 모두 `terraform init -backend=false && terraform validate` 통과, `terraform fmt -check -recursive` 무변경 (검증일 2026-07-06, Terraform v1.15.6)
- envs 공통 `.tf` 10개(cluster/frontend/kms_extra/main/observability/outputs/secrets/services/sns/task_roles)는 dev/beta/prod 글자 단위 동일. 다른 파일은 backend.tf/providers.tf/variables.tf 3개뿐 (검증일 2026-07-06)
- results/00-cross-cutting-matrix.md §1 KMS(환경별 5키+bootstrap 2키)·§3 SG·§4 Secrets·§6 알람 명명은 코드와 일치 (검증일 2026-07-06)
- reminder #1(prod ALB HTTPS)은 코드로 해소(커밋 a980621), lock 파일 커밋은 해소(커밋 a5e2f85) — reminder 문서에 반영함

## 거짓 양성 (다시 지적하지 말 것)
- `bootstrap/variables.tf` 의 `permissions_boundary_managed` 미사용 변수 — 실제로는: Permission Boundary 향후 부착용 자리표시로 README·roles.tf:283 주석·reminder #2 에 문서화된 의도된 선언 (판정일 2026-07-06)
- envs 세 환경의 공통 `.tf` 가 복사본인 것 — 실제로는: "환경별 디렉토리" 패턴의 의도된 설계. 공유 root module 화는 ADR 사안 (판정일 2026-07-06)
- results/09 §7 예시 스니펫의 리소스 참조명(`aws_ecs_cluster.this` 등)이 실제(`main`)와 다른 것 — 실제로는: §7 서두에 "설명용·단순화" 명시 (판정일 2026-07-06)
- beta tfvars 가 enable_aurora/enable_elasticache 를 생략하고 default(true)에 의존하는 것 — 실제로는: 결과가 prod 명시값과 동일, 표기 취향 차이 (판정일 2026-07-06)

## 미해결 (plan 검증 불가 또는 승인 필요 — 우선 확인 대상)
- [높음] `envs/*/task_roles.tf` `api_agent_perms` 의 `MskClusterIam` statement: `enable_msk=false`(dev 기본)면 `compact()` 가 빈 resources 를 만들어 정책 문서가 Resource 없는 statement 가 됨 → dev apply 실패 가능. 주석의 "미활성 시 wildcard/dummy" 의도대로 `resources = coalescelist(compact([...]), ["*"])` 로 고치면 됨 — 이유: plan diff 발생, 자격증명 없어 미적용
- [높음] `modules/aurora-mysql/main.tf:149` `monitoring_interval = is_prod ? 60 : 0` 인데 `monitoring_role_arn` 변수·역할이 모듈에 없음 → prod apply 실패 가능(Enhanced Monitoring 은 role 필수) — 이유: plan diff, 역할 추가 설계 필요
- [높음] `envs/prod/terraform.tfvars:42` `alb_certificate_arn` 이 자리표시 ARN 인데 빈 문자열이 아니라 HTTPS 분기가 켜짐 → 인증서 발급 전 prod apply 실패 — 이유: 실제 인증서 발급 대기 (reminder #1 에도 기록)
- [중간] autoscaling `min_count/max_count` 를 gateway·auth 만 전달, 나머지 4개 서비스는 모듈 기본 min=2/max=10 → dev desired=1 을 min=2 가 끌어올림, 비용 의도와 모순 — 이유: plan diff
- [중간] `envs/*/providers.tf` default_tags 불일치: Owner 태그가 dev 에만 있음 — 이유: plan diff(태그 갱신), 의도 확인 필요
- [중간] results/08-observability.md §5.4 `DatapointsToAlarm=3/5` 설계 vs modules/observability 는 `evaluation_periods=3` 만(3연속) — `datapoints_to_alarm=3, evaluation_periods=5` 추가 여부 결정 필요 — 이유: 알람 동작 변경
- [낮음] `envs/*/providers.tf` 의 `aws.us_east_1` alias 를 어떤 모듈도 소비하지 않음 + `modules/cloudfront-spa` 를 어떤 env 도 호출하지 않음(프런트는 Amplify) — 사장 코드, 삭제 여부는 사용자 결정
- [낮음] `modules/network/flow_logs.tf` Flow Logs 로그 그룹만 KMS 미설정 (다른 로그 그룹은 logs CMK)
- [낮음] `bootstrap/ecr.tf:143-157` repository policy Deny 가 코드에 정의처 없는 `techai-bootstrap-admin` 역할 ARN 을 참조 (수동 생성 가정)
- [낮음] `modules/ecs-service/main.tf:236` 컨테이너 healthCheck 가 `wget` 의존 — 앱 이미지에 wget 포함 여부 확인 필요
- [낮음] `aws_security_group_rule` → `aws_vpc_security_group_*_rule` 마이그레이션 (reminder #4, provider 6.0 때 일괄)
