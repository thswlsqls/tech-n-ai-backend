# `modules/ecs-service` — Fargate 서비스

> 09 §5.2 + 03 §2 spec 구현. ALB Target Group(blue/green) + ECS Service(ECS 자체 blue/green 배포) + 워크로드 SG + Auto Scaling + 자동 롤백 알람.

## 무엇을 만드는가

| 자원 | 개수 |
|---|---|
| `aws_security_group` (워크로드 SG) | 1 |
| `aws_lb_target_group` (blue/green, 이름 접미사 `-b`/`-g`) | 2 |
| `aws_lb_listener_rule` (blue 가중치 1, green 가중치 0 으로 forward) | 1 |
| `aws_ecs_task_definition` | 1 (이후 revision 은 CI 가 등록) |
| `aws_ecs_service` | 1 (deployment_controller=ECS, strategy=CANARY — `enable_blue_green = true` 일 때, false 면 ROLLING) |
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

`enable_blue_green = true`(기본값)면 `deployment_configuration.strategy = "CANARY"` 로 배포한다([문서](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/canary-deployment.html)). 예전 CodeDeploy 설정 `CodeDeployDefault.ECSCanary10Percent5Minutes` 와 같은 순서로 트래픽을 넘긴다.

1. ECS 가 새 task definition 으로 green 태스크를 띄워 green 대상 그룹에 등록한다.
2. green 이 health check 를 통과하면 ECS 가 리스너 규칙 가중치를 바꿔 운영 트래픽의 10% 를 green 으로 보낸다(`canary_percent = 10`).
3. 5분(`canary_bake_time_in_minutes = 5`) 동안 지켜본 뒤 나머지 90% 를 green 으로 넘긴다. 이 사이에 알람이 울리면 트래픽을 blue 로 되돌린다.
4. bake time(5분) 동안 blue 태스크를 그대로 둔다. 이 사이에 알람이 울려도 blue 로 되돌린다.
5. bake time 이 끝나면 blue 태스크를 내린다.

카나리 구간에서는 green 이 트래픽의 10% 만 받는다. 그래서 두 알람 모두 대상 그룹마다 따로 값을 낸 뒤 큰 값을 본다. 두 대상 그룹을 합쳐 5xx 비율을 내면 green 오류가 1/10 로 희석되어, green 요청의 5% 가 실패해도 합계는 0.5% 라 1% 임계를 넘지 못한다. 새 버전이 어느 대상 그룹에 붙는지는 배포마다 바뀌므로 green 대상 그룹 하나에만 알람을 걸지 않는다.

대신 트래픽이 적으면 알람이 쉽게 울린다. 카나리 구간에 green 으로 가는 요청이 분당 수십 건이면 5xx 한두 건으로도 1% 를 넘을 수 있다. 이렇게 울리면 배포가 롤백되므로, 트래픽이 적은 dev·beta 에서 롤백이 잦으면 `rollback_alarm_5xx_threshold` 를 올린다.

**비용.** green 태스크는 카나리 단계 전에 전체 수만큼 뜨고, blue 는 bake time 이 끝날 때까지 남는다. 그래서 배포마다 약 10분(카나리 5분 + bake time 5분) 동안 태스크 수가 두 배가 된다([문서](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-type-blue-green.html) "may double your resource usage during deployments"). 예전 CodeDeploy 설정도 카나리 5분 뒤 blue 를 5분 더 남겼으므로(`termination_wait_time_in_minutes = 5`) 겹치는 시간은 같다.
`enable_blue_green = false` 면 `ROLLING` 으로 배포하고 ECS 인프라 역할은 만들지 않는다.

서비스 6개가 ALB 리스너 하나를 같이 쓴다. ECS 는 서비스마다 `advanced_configuration.production_listener_rule` 로 받은 리스너 규칙 하나의 가중치를 바꾼다([문서](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/alb-resources-for-blue-green.html)). 다만 같은 리스너의 다른 규칙을 건드리지 않는다는 문장은 문서에 없으므로, 첫 배포 때 다른 서비스의 규칙이 그대로인지 확인한다.

리스너 규칙의 `action` 과 서비스의 `load_balancer`·`task_definition` 은 배포 때마다 ECS·CI 가 바꾸므로 `lifecycle.ignore_changes` 로 Terraform 이 되돌리지 않게 한다.

그래서 **서비스를 만든 뒤에는 `enable_blue_green` 을 바꾸지 않는다.** green 대상 그룹·리스너 규칙·인프라 역할을 넘기는 `advanced_configuration` 이 `load_balancer` 안에 있어 무시되고, `strategy` 와 인프라 역할만 바뀐다. 바꿔야 한다면 서비스를 다시 만든다.

## 자동 롤백 안전망 (D-2 — 라이프사이클 훅 미사용)

- ALB Target Group health check `/actuator/health/readiness` (HTTP 200)
- ALB 5xx 비율 알람 `<name>-alb-5xx-rate` — 두 대상 그룹 중 큰 값 기준, 1% 초과가 2분 연속
- Target Response Time p95 알람 `<name>-latency-p95` — 두 대상 그룹 중 큰 값 기준, 1.5s 초과가 3분 연속
- `aws_ecs_service` 의 `alarms { enable = true, rollback = true }` 와 `deployment_circuit_breaker { rollback = true }`

알람 중 하나가 ALARM 이 되거나 서킷 브레이커가 배포 실패를 판단하면, 먼저 걸린 쪽이 배포를 실패로 처리하고 마지막으로 성공한 배포로 되돌린다.

배포를 시작하는 순간 이미 ALARM 상태인 알람이 있으면, ECS 는 그 배포가 끝날 때까지 알람을 보지 않는다([문서](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-alarm-failure.html)). 장애를 고치려고 다시 배포하는 경우를 위한 동작이지만, 이때는 알람으로 자동 롤백이 일어나지 않으므로 배포 상태를 직접 지켜본다.

## 주의

- `container_image` 는 digest 형태(`@sha256:...`) 를 권장. 태그 형태는 immutable 가정이 깨질 수 있음.
- 모듈은 readonly_root_filesystem 을 false 로 둔다 — Spring Boot 가 `/tmp` 를 사용하기 때문. 보안 강화 시 `/tmp` mount 추가 후 true 가능.
