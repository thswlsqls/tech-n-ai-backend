# `modules/ecs-service` — Fargate 서비스

> 09 §5.2 + 03 §2 spec 구현. ALB Target Group(blue/green) + ECS Service(ECS 자체 blue/green 배포) + 워크로드 SG + Auto Scaling + 자동 롤백 알람.

## 무엇을 만드는가

| 자원 | 개수 |
|---|---|
| `aws_security_group` (워크로드 SG) | 1 |
| `aws_lb_target_group` (blue/green, 이름 접미사 `-b`/`-g`) | 2 |
| `aws_lb_listener_rule` (blue 가중치 1, green 가중치 0 으로 forward) | 1 |
| `aws_ecs_task_definition` | 1 (이후 revision 은 CI 가 등록) |
| `aws_ecs_service` | 1 (deployment_controller=ECS, strategy=BLUE_GREEN) |
| Auto Scaling Target + 2 Policy (CPU, Memory) | 1+2 |
| CloudWatch Alarm (5xx rate, p95 latency) — 자동 롤백 | 2 |
| CloudWatch Log Group | 1 (자동 생성 모드) |
| ECS 인프라 IAM Role (`AmazonECSInfrastructureRolePolicyForLoadBalancers`) | 1 (`enable_blue_green = true` 일 때) |

## 사용 예 — api-auth (8083)

```hcl
module "api_auth" {
  source = "../../modules/ecs-service"

  project     = "techai"
  environment = "dev"
  service_name = "api-auth"

  cluster_arn        = aws_ecs_cluster.main.arn
  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids

  # 단일 ECR 리포 (D-1) — digest 참조 권장
  container_image = "${var.ecr_registry}/techai/api-auth@${var.api_auth_digest}"
  container_port  = 8083
  cpu             = 512
  memory          = 1024
  desired_count   = 2

  task_role_arn      = module.task_role_api_auth.role_arn
  execution_role_arn = module.task_execution_role.role_arn

  alb_listener_arn       = aws_lb_listener.https.arn
  alb_security_group_id  = aws_security_group.alb.id
  listener_rule_priority = 100
  listener_path_patterns = ["/auth/*"]

  log_kms_key_arn = aws_kms_key.logs.arn

  environment_vars = [
    { name = "SPRING_PROFILES_ACTIVE",                 value = "dev" },
    { name = "MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED", value = "true" },
  ]

  secrets_arn_map = {
    AURORA_PASSWORD = module.aurora.master_user_secret_arn
    JWT_SIGNING_KEY = aws_secretsmanager_secret.jwt.arn
  }

  autoscaling_min_count = 2
  autoscaling_max_count = 6
}
```

## 워크로드 SG cross-reference (envs 계층에서)

본 모듈은 자기 SG 만 만들고, **다른 워크로드와의 인바운드 규칙은 envs 에서 별도로 추가**한다 (단일 정의처 원칙).

```hcl
# envs/dev/main.tf 일부
resource "aws_security_group_rule" "auth_from_gateway" {
  type                     = "ingress"
  from_port                = 8083
  to_port                  = 8083
  protocol                 = "tcp"
  source_security_group_id = module.api_gateway.security_group_id
  security_group_id        = module.api_auth.security_group_id
  description              = "api-gateway → api-auth 8083"
}
```

## 배포 방식 — ECS 자체 blue/green

`enable_blue_green = true`(기본값)면 `deployment_configuration.strategy = "BLUE_GREEN"` 으로 배포한다.

1. ECS 가 새 task definition 으로 green 태스크를 띄워 green 대상 그룹에 등록한다.
2. green 이 health check 를 통과하면 ECS 가 리스너 규칙 가중치를 바꿔 운영 트래픽을 한 번에 green 으로 넘긴다.
3. bake time(5분) 동안 blue 태스크를 그대로 둔다. 이 사이에 알람이 울리면 트래픽을 blue 로 되돌린다.
4. bake time 이 끝나면 blue 태스크를 내린다.

bake time 동안에는 blue·green 태스크가 함께 떠 있어 태스크 수가 잠시 두 배가 될 수 있다.
`enable_blue_green = false` 면 `ROLLING` 으로 배포하고 ECS 인프라 역할은 만들지 않는다.

리스너 규칙의 `action` 과 서비스의 `load_balancer`·`task_definition` 은 배포 때마다 ECS·CI 가 바꾸므로 `lifecycle.ignore_changes` 로 Terraform 이 되돌리지 않게 한다.

## 자동 롤백 안전망 (D-2 — 라이프사이클 훅 미사용)

- ALB Target Group health check `/actuator/health/readiness` (HTTP 200)
- ALB 5xx 비율 알람 `<name>-alb-5xx-rate` — blue·green 대상 그룹 합계 기준, 1% 초과가 2분 연속
- Target Response Time p95 알람 `<name>-latency-p95` — 두 대상 그룹 중 큰 값 기준, 1.5s 초과가 3분 연속
- `aws_ecs_service` 의 `alarms { enable = true, rollback = true }` 와 `deployment_circuit_breaker { rollback = true }`

알람 중 하나가 ALARM 이 되거나 서킷 브레이커가 배포 실패를 판단하면, 먼저 걸린 쪽이 배포를 실패로 처리하고 마지막으로 성공한 배포로 되돌린다.

## 주의

- `container_image` 는 digest 형태(`@sha256:...`) 를 권장. 태그 형태는 immutable 가정이 깨질 수 있음.
- 모듈은 readonly_root_filesystem 을 false 로 둔다 — Spring Boot 가 `/tmp` 를 사용하기 때문. 보안 강화 시 `/tmp` mount 추가 후 true 가능.
