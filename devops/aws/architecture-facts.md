# Architecture Facts (Terraform 기준)
> 근거: devops/terraform 코드 원문. 추측 없이 코드에 적힌 사실만 기록.

> **환경 구조 주의**: `envs/dev`, `envs/beta`, `envs/prod`의 `.tf` 파일들(`main.tf`, `cluster.tf`, `services.tf`, `frontend.tf`, `secrets.tf`, `task_roles.tf`, `kms_extra.tf` 등)은 **byte 단위로 동일**하다 (`diff`로 확인). 환경별 차이는 오직 각 env의 `terraform.tfvars`와 `variables.tf`의 default 값에서만 발생한다. 그래서 `envs/prod/main.tf`의 머리말 주석이 "envs/dev — 개발 환경 조립 계층"으로 적혀 있는데(`envs/prod/main.tf:1`), 이는 dev에서 복사하며 주석을 안 고친 흔적이다. 코드 자체의 동작과는 무관하다.
>
> tfvars가 비워둔 변수는 그 env `variables.tf`의 default가 적용된다. 특히 **dev의 `terraform.tfvars`는 project/environment/region/vpc_cidr/azs 5개만 지정**하므로(`envs/dev/terraform.tfvars:1`), 나머지 dev 값은 전부 default다.

---

## 1. 컴퓨팅 (ECS / Fargate / ALB / CodeDeploy)

### ECS Cluster
- 이름 `${project}-${environment}` (예: `techai-prod`), Container Insights `enabled`, ECS Exec logging `DEFAULT`. (`envs/prod/cluster.tf:4`, `:7`, `:13`)

### Fargate / 태스크 공통
- launch type = `FARGATE`, network mode `awsvpc`, `requires_compatibilities = ["FARGATE"]`. (`modules/ecs-service/main.tf:347`, `:321`, `:322`)
- CPU architecture = **ARM64** (Graviton), OS family `LINUX`. (`modules/ecs-service/main.tf:329`, `:330`)
- Task ENI는 Private-App 서브넷에 배치, `assign_public_ip = false`. (`modules/ecs-service/main.tf:352`, `:354`)
- 메인 컨테이너 헬스체크: `wget --spider http://localhost:{port}{health_check_path}`, interval 30 / timeout 5 / retries 3 / startPeriod 60. (`modules/ecs-service/main.tf:236`)
- `readonlyRootFilesystem = false` (Spring Boot가 /tmp 사용). (`modules/ecs-service/main.tf:244`)

### 서비스 목록 (모든 env 공통 — services.tf 동일)
ECS 모듈 호출은 6개. **`batch-source`는 ECS 서비스로 배포되지 않는다** (services.tf에 없음 — `placeholder_image_for` map에도 batch-source 제외, `envs/prod/services.tf:19`). batch-source는 ECR 리포로만 존재(§6 참고).

| service | container port | cpu | memory | desired_count | autoscaling min/max | listener priority | path | 출처(file:line) |
|---|---|---|---|---|---|---|---|---|
| api-gateway | 8081 | 512 | 1024 | `var.ecs_desired_count` | `var.ecs_autoscaling_min/max_count` | 1000 (fallback) | `/*` | `envs/prod/services.tf:39`~`:63` |
| api-auth | 8083 | 512 | 1024 | `var.ecs_desired_count` | min/max 변수 | 100 | `/auth/*` | `envs/prod/services.tf:81`~`:105` |
| api-emerging-tech | 8082 | 512 | 1024 | `var.ecs_desired_count` | (min/max 미지정 → 모듈 default 2/10) | 110 | `/emerging-tech/*` | `envs/prod/services.tf:123`~`:142` |
| api-chatbot | 8084 | 1024 | 2048 | `var.ecs_desired_count` | (모듈 default 2/10) | 120 | `/chatbot/*` | `envs/prod/services.tf:162`~`:185` |
| api-bookmark | 8085 | 256 | 512 | `var.ecs_desired_count` | (모듈 default 2/10) | 130 | `/bookmark/*` | `envs/prod/services.tf:203`~`:222` |
| api-agent | 8086 | 512 | 1024 | `var.ecs_desired_count` | (모듈 default 2/10) | 140 | `/agent/*` | `envs/prod/services.tf:242`~`:261` |

- 모듈 default: `desired_count=2`, `autoscaling_min=2`, `autoscaling_max=10`, `cpu=512`, `memory=1024` (`modules/ecs-service/variables.tf:56`, `:150`, `:156`).
- api-gateway, api-auth만 `autoscaling_min_count`/`max_count`를 명시 전달(`var.ecs_autoscaling_*`). 나머지 4개는 전달 안 함 → 모듈 default 2/10 사용. (`envs/prod/services.tf:62`, `:104`)
- api-chatbot은 `rollback_alarm_latency_p95_seconds = 5.0` 별도 지정(LLM 호출 지연 고려). (`envs/prod/services.tf:185`)
- 공통 환경변수: `SPRING_PROFILES_ACTIVE={env}`, `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true`, `AWS_REGION`. (`envs/prod/services.tf:11`)
- 시드 컨테이너 이미지: `{account}.dkr.ecr.{region}.amazonaws.com/techai/{module}:initial` (실제 배포는 GitHub Actions가 digest로 교체). (`envs/prod/services.tf:18`~`:21`)
- `secrets_arn_map`은 services.tf에서 전달하지 않음 → 빈 map (시크릿 주입은 별도 워크플로). (주석 `envs/prod/services.tf:4`)

### ALB + 라우팅
- ALB는 env당 1개, `internal=false`, type `application`, Public 서브넷, `drop_invalid_header_fields=true`. `enable_deletion_protection`은 `var.alb_enable_deletion_protection`(prod tfvars=true, dev/beta=default false). (`envs/prod/cluster.tf` `aws_lb.main`)
- **HTTPS 토글**: `var.alb_certificate_arn`이 비어있지 않으면 HTTPS(443) 리스너 생성 + HTTP(80)→443 301 리다이렉트 + ALB SG 443 인바운드를 켠다. prod tfvars는 ACM ARN을 지정해 HTTPS, dev/beta는 빈 값이라 HTTP(80) 단독. (`envs/prod/cluster.tf` `local.alb_https_enabled`; `prod/terraform.tfvars`)
- Listener: **prod = HTTPS 443** (보안정책 `var.alb_ssl_policy` 기본 `ELBSecurityPolicy-TLS13-1-2-2021-06`, cert = `var.alb_certificate_arn`), 서비스 path 규칙이 443 리스너에 부착되고 80 리스너는 443 으로 리다이렉트. **dev/beta = HTTP 80**, default action = 404 fixed-response. (`envs/prod/cluster.tf` `aws_lb_listener.https`/`aws_lb_listener.http`)
- 라우팅은 **path-based** (위 표의 path), 우선순위로 매칭. host header 조건은 사용 안 함(빈 리스트). (`modules/ecs-service/main.tf:143`~`:168`)
- Target Group은 서비스마다 blue/green 2개, `target_type=ip`, protocol HTTP, health check path 기본 `/actuator/health/readiness` (matcher 200, healthy 2 / unhealthy 3 / interval 15 / timeout 5). (`modules/ecs-service/main.tf:81`, `:110`; default path `modules/ecs-service/variables.tf:99`)

### CodeDeploy Blue/Green
- `enable_blue_green` default true → `deployment_controller.type = CODE_DEPLOY`. (`modules/ecs-service/main.tf:364`, `modules/ecs-service/variables.tf:174`)
- deployment config 기본 `CodeDeployDefault.ECSCanary10Percent5Minutes`. (`modules/ecs-service/variables.tf:180`)
- deployment style `BLUE_GREEN` + `WITH_TRAFFIC_CONTROL`, 성공 시 blue 종료(5분 대기). (`modules/ecs-service/codedeploy.tf:131`, `:160`)
- 자동 롤백: 이벤트 `DEPLOYMENT_FAILURE`, `DEPLOYMENT_STOP_ON_ALARM` + 알람 2종(ALB 5xx 비율 기본 임계 1%, Target p95 지연 기본 1.5s — chatbot은 5.0s). (`modules/ecs-service/codedeploy.tf:166`~`:177`; 임계 default `modules/ecs-service/variables.tf:186`, `:192`)
- CodeDeploy 서비스 Role은 모듈이 자체 생성 + `AWSCodeDeployRoleForECS` 부착. (`modules/ecs-service/codedeploy.tf:99`, `:120`)
- ECS 서비스에 `deployment_circuit_breaker { enable=true, rollback=true }`도 설정. (`modules/ecs-service/main.tf:367`)

### Sidecar (ADOT / FireLens)
- 둘 다 옵션이며 **default false** (모든 env에서 tfvars가 켜지 않음 → 비활성). (`modules/ecs-service/variables.tf:209`, `:227`; prod `variables.tf:230`, `:236`)
- ADOT: image `public.ecr.aws/aws-observability/aws-otel-collector:latest`, cpu 64 / memReservation 128, 활성 시 메인 컨테이너에 `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317` 자동 주입. (`modules/ecs-service/main.tf:186`, `:248`)
- FireLens(Fluent Bit): image `public.ecr.aws/aws-observability/aws-for-fluent-bit:stable`, 활성 시 메인 logDriver를 `awsfirelens`로 전환. (`modules/ecs-service/main.tf:192`, `:278`)
- sidecar 미사용 시 메인 로그는 `awslogs` → CloudWatch Log Group `/aws/ecs/{env}/{service}`. (`modules/ecs-service/main.tf:199`, `:14`)

---

## 2. 데이터 (Aurora / ElastiCache / MSK / MongoDB Atlas)

### Aurora MySQL
공통 코드 사실: engine `aurora-mysql`, `engine_version` 기본 `8.0.mysql_aurora.3.07.1`, `engine_mode="provisioned"`(serverlessv2도 provisioned + scaling block 사용), Managed Master User Password(AWS가 Secrets Manager 시크릿 자동 생성), `storage_encrypted=true`(KMS `{env}-data`), CloudWatch logs export `["error","slowquery","audit"]`, 파라미터 그룹 family `aurora-mysql8.0`. (`modules/aurora-mysql/main.tf:72`~`:100`; default version `modules/aurora-mysql/variables.tf:36`)
- 인스턴스 수: serverlessv2면 1개(db.serverless), provisioned면 `instance_count`. writer=index0, reader=나머지. (`modules/aurora-mysql/main.tf:131`~:141`, `:156`)
- IAM DB auth: 모듈 default true. (`modules/aurora-mysql/variables.tf:147`)
- Multi-AZ: 명시 플래그 없음. data_subnet_ids(3 AZ)로 subnet group 구성 → 인스턴스가 AZ에 분산되는 방식. (`modules/aurora-mysql/main.tf:30`)
- Performance Insights: prod만 활성, monitoring_interval prod=60/그외 0, `apply_immediately = !prod`. (`modules/aurora-mysql/main.tf:149`, `:152`)

| 항목 | dev | beta | prod | 출처 |
|---|---|---|---|---|
| engine_mode | serverlessv2 (default) | serverlessv2 | provisioned | `dev/variables.tf:115`, `beta/terraform.tfvars:11`, `prod/terraform.tfvars:11` |
| serverless min/max ACU | 0.5 / 2.0 (default) | 0.5 / 4.0 | (해당 없음) | `dev/variables.tf:65`,`:71`; `beta/terraform.tfvars:12`~`:13` |
| instance_count | (무시) | (무시) | 3 (writer1+reader2) | `prod/terraform.tfvars:12` |
| instance_class | (무시) | (무시) | db.r7g.large | `prod/terraform.tfvars:13` |
| storage_type | aurora (default) | aurora (default) | aurora-iopt1 | `dev/variables.tf:133`; `prod/terraform.tfvars:14` |
| backup_retention | 1 (default) | 7 | 30 | `dev/variables.tf:139`; `beta/terraform.tfvars:14`; `prod/terraform.tfvars:15` |
| deletion_protection | false (default) | false | true | `dev/variables.tf:145`; `beta/terraform.tfvars:15`; `prod/terraform.tfvars:16` |
| skip_final_snapshot | true (default) | true | false | `dev/variables.tf:151`; `beta/terraform.tfvars:16`; `prod/terraform.tfvars:17` |
| performance_insights | false (default) | false | true | `dev/variables.tf:157`; `prod/terraform.tfvars:18` |

### ElastiCache Valkey
공통: engine `valkey`, engine_version 기본 `8.0`, port 6379, `at_rest_encryption_enabled` default true(KMS `{env}-data`), `transit_encryption_enabled` default true(TLS), num_node_groups 1, auth_mode `auth_token`(모든 env에서 명시 전달). (`modules/elasticache-valkey/main.tf:95`~`:111`; `envs/prod/main.tf:155`)
- replicas=0이면 failover/multi_az는 강제 비활성(코드 로직). (`modules/elasticache-valkey/main.tf:26`~`:28`)
- AUTH 토큰은 `random_password`(64자 영숫자)로 생성, Secrets Manager 저장은 envs에서. (`modules/elasticache-valkey/main.tf:74`)

| 항목 | dev | beta | prod | 출처 |
|---|---|---|---|---|
| node_type | cache.t4g.micro (default) | cache.t4g.small | cache.t4g.small | `dev/variables.tf:87`; `beta/terraform.tfvars:19`; `prod/terraform.tfvars:21` |
| replicas_per_node_group | 0 (default) | 1 | 1 | `dev/variables.tf:93`; `beta/terraform.tfvars:20`; `prod/terraform.tfvars:22` |
| multi_az_enabled | false (default) | true | true | `dev/variables.tf:99`; `beta/terraform.tfvars:21`; `prod/terraform.tfvars:23` |
| snapshot_retention | 0 (default) | 3 | 7 | `dev/variables.tf:105`; `beta/terraform.tfvars:22`; `prod/terraform.tfvars:24` |
| auth_mode | auth_token | auth_token | auth_token | `envs/*/main.tf:155` |

### MSK
- dev: `enable_msk` default **false** → MSK 미생성. (`dev/variables.tf:55`, dev tfvars가 안 켬)
- beta: `enable_msk=true`, `use_msk_provisioned=false` → **MSK Serverless**. (`beta/terraform.tfvars:25`~`:26`)
- prod: `enable_msk=true`, `use_msk_provisioned=true` → **MSK Provisioned**. (`prod/terraform.tfvars:29`~`:30`)
- MSK Serverless: IAM SASL 전용 인증, port 9098, VPC config subnet=Private-App. (`modules/msk-serverless/main.tf:61`, `:37`)
- MSK Provisioned: kafka_version `3.9.x.kraft`(prod tfvars `:31`), broker_count 3, instance_type `kafka.m7g.large`, EBS 500GB(default), 인증 IAM SASL(9098)+TLS(9094) 둘 다, in-transit `client_broker=TLS`, at-rest KMS `{env}-data`, Open Monitoring(Prometheus JMX/Node Exporter) 활성, broker logs → CloudWatch. config: `auto.create.topics=false`, RF=3, min.insync.replicas=2, num.partitions=6, retention 168h. (`prod/terraform.tfvars:31`~`:33`; `modules/msk-provisioned/main.tf:111`~`:203`)
- prod에서 MSK Provisioned의 client_subnets는 Private-Data 서브넷 사용. (`envs/prod/main.tf:185`)

### MongoDB Atlas
- Terraform이 직접 생성하지 않는 **외부 서비스**. 연결은 Secrets Manager 시크릿 `{project}/{env}/mongodb-uri`로 참조(X.509 인증 권장 주석, 초기값 placeholder). (`envs/prod/secrets.tf:61`~`:76`)
- 네트워크상 Private-Data 서브넷이 "MongoDB Atlas Endpoint"용으로 표기되나, Atlas endpoint 리소스 자체는 Terraform 코드에 없음. (`modules/network/main.tf:4`, outputs `:21`)

---

## 3. 프런트 (Amplify / CloudFront)

### Amplify
- 모든 env에서 `enable_amplify` default **false** → Amplify app/admin 미생성(시드 단계). (`prod/variables.tf:261`; tfvars 어디에도 true 없음)
- 모듈 사실(활성 시): platform `WEB_COMPUTE`(Next.js SSR), `enable_branch_auto_build=false`(GitHub Actions가 start-job 트리거), env 변수는 build_spec의 preBuild에서 SSM Parameter Store(`/{project}/{env}/{app}/...`)로 주입. (`modules/amplify-app/main.tf:114`, `:124`, `:32`)
- envs/frontend.tf: app 2개(`app`, `admin`) 호출, branch=`var.frontend_branch_name`(default `develop`), stage `DEVELOPMENT`, admin basic auth false. GitHub PAT은 Secrets Manager `{project}/{env}/github-pat-amplify`(KMS `{env}-auth`). (`envs/prod/frontend.tf:7`~`:77`)

### CloudFront
- `modules/cloudfront-spa`는 모듈로 **존재하지만, 어떤 env의 .tf에서도 호출되지 않는다** (envs에 `module "cloudfront"` 없음 — frontend.tf는 amplify만 호출). 확인 가능한 모듈 사양만 기록:
  - price_class `PriceClass_200`, IPv6 enabled, OAC(S3 origin 시, sigv4/always). (`modules/cloudfront-spa/main.tf:82`, `:29`)
  - 보안 응답 헤더 정책: CSP(`default-src 'self'; script-src 'self' 'unsafe-inline'; ...`), HSTS(max-age 31536000, includeSubdomains, preload), X-Frame-Options DENY, X-Content-Type-Options, Referrer-Policy strict-origin-when-cross-origin, XSS protection. (`modules/cloudfront-spa/main.tf:43`~`:71`)
  - WAF: `web_acl_id = var.waf_web_acl_arn` (default null → 미부착). (`modules/cloudfront-spa/main.tf:86`, `variables.tf:59`)
  - origin은 amplify 또는 s3 택1, SPA 404/403 → /index.html(S3 origin 시). (`modules/cloudfront-spa/main.tf:89`, `:127`)

---

## 4. 네트워크 (VPC / Subnet / NAT / VPC Endpoint / Flow Logs)

### VPC + 서브넷
- VPC `/16`, `enable_dns_hostnames/support=true`, IGW 1개. (`modules/network/main.tf:39`, `:49`)
- 4티어 서브넷(AZ당 1개, 3 AZ → 각 12개 서브넷 중 티어별 3개), `cidrsubnet`으로 결정적 산출: (`modules/network/main.tf:29`~`:32`)

| 티어 | 크기 | netnum | 예시(prod 10.30.0.0/16) | 용도 | 출처 |
|---|---|---|---|---|---|
| public | /24 | 0..2 | 10.30.0.0/24 등 | ALB, NAT GW | `modules/network/main.tf:29`, `:61` |
| private-app | /20 | 1..3 | 10.30.16.0/20 등 | ECS Fargate Task ENI | `modules/network/main.tf:30`, `:76` |
| private-data | /24 | 64..66 | 10.30.64.0/24 등 | Aurora, ElastiCache, MongoDB Atlas EP | `modules/network/main.tf:31`, `:90` |
| private-tgw | /26 | 280..282 | 10.30.70.0/26 등 | 향후 TGW용 | `modules/network/main.tf:32`, `:103` |

- public 서브넷 `map_public_ip_on_launch=false`. (`modules/network/main.tf:67`)
- AZ는 정확히 3개 검증(`length==3`), 모든 env가 ap-northeast-2 a/b/c. (`modules/network/variables.tf:30`; tfvars)

### NAT / 라우팅
- NAT 개수 로직: `enable_nat_gateway ? (single_nat_gateway ? 1 : len(azs)) : 0`. (`modules/network/main.tf:121`)
- dev: single_nat_gateway true(default) → NAT 1개. beta: true → 1개. prod: false → 3개. (`dev/variables.tf:167`; `beta/terraform.tfvars:7`; `prod/terraform.tfvars:7`)
- public 라우팅 → IGW(0.0.0.0/0). private-app 라우팅 → NAT(single이면 동일 NAT). private-data는 인터넷 라우트 없음(격리). private-tgw는 라우트 없음. (`modules/network/main.tf:162`, `:186`, `:201`, `:217`)
- 모든 env에서 `enable_nat_gateway=true`(envs/main.tf 하드코딩). (`envs/prod/main.tf:46`)

### VPC Endpoint
- `enable_vpc_endpoints` 모든 env에서 true(dev default true, beta/prod tfvars true). (`dev/variables.tf:173`; `beta/terraform.tfvars:8`; `prod/terraform.tfvars:8`)
- Gateway: **S3, DynamoDB** (private-app + private-data 라우트 테이블 attach). (`modules/network/vpc_endpoints.tf:49`, `:66`)
- Interface(9개, private-app 서브넷 ENI, private_dns_enabled, SG는 443 from private-app): `ecr.api`, `ecr.dkr`, `logs`, `kms`, `sts`, `secretsmanager`, `ssm`, `ssmmessages`, `ec2messages`. (`modules/network/vpc_endpoints.tf:88`~`:98`, `:101`)

### Flow Logs
- 모든 env `enable_flow_logs=true`(envs/main.tf 하드코딩). CloudWatch Log Group `/aws/vpc/{name}/flow-logs`, retention 90일(default), traffic_type ALL, aggregation 60s. (`envs/prod/main.tf:49`; `modules/network/flow_logs.tf:4`, `:54`; default `modules/network/variables.tf:63`)

---

## 5. 보안 (KMS / IAM / Secrets / Security Group)

### KMS 키
- bootstrap (환경 무관 공유):
  - `tfstate` — state S3 + DynamoDB Lock 암호화, rotation on, deletion window 30d. (`bootstrap/kms.tf:5`)
  - `ecr` — ECR 이미지 레이어, rotation on. (`bootstrap/ecr.tf:23`)
- env별 (각 env당 5개, rotation 모두 on, deletion window 30d):
  - `{env}-data` — Aurora·MongoDB·ElastiCache·MSK 데이터. (`envs/prod/main.tf:11`)
  - `{env}-s3-app` — 어플리케이션 S3. (`envs/prod/main.tf:22`)
  - `{env}-auth` — JWT 서명 + RDS IAM envelope. (`envs/prod/kms_extra.tf:4`)
  - `{env}-ai` — OpenAI/Cohere 키. (`envs/prod/kms_extra.tf:15`)
  - `{env}-logs` — CloudWatch Logs/Athena (CloudWatch Logs 서비스 정책 포함). (`envs/prod/kms_extra.tf:26`)
- **KMS 키 수: bootstrap 2 + env당 5** (dev 5, beta 5, prod 5). 모든 env가 동일 구조(kms_extra.tf 동일).

### IAM
- Task Execution Role: env당 1개 공유, `AmazonECSTaskExecutionRolePolicy` + 인라인(Secrets/SSM read `{project}/{env}/*`, KMS Decrypt data·s3-app). (`envs/prod/main.tf:81`, `:199`)
- Task Role (서비스별 6개, trust `ecs-tasks.amazonaws.com` + `aws:SourceAccount` 조건): (`envs/prod/task_roles.tf`)
  - api-gateway — SSM Parameter read만. (`:19`)
  - api-auth — Aurora master secret + jwt-signing-key read, `rds-db:connect`(dbuser api_auth), KMS Decrypt auth·data. (`:48`)
  - api-chatbot — openai-api-key·mongodb-uri read, KMS Decrypt ai. **Bedrock 권한 없음(D-12)**. (`:102`)
  - api-agent — `kafka-cluster:*`(Connect/Describe/Read/WriteData 등), mongodb-uri read. (`:144`)
  - api-bookmark — `rds-db:connect`(dbuser api_bookmark), elasticache-auth-token read, KMS Decrypt data. (`:195`)
  - api-emerging-tech — openai-api-key read, KMS Decrypt ai. (`:240`)
- Workload Role 모듈(`iam-role-workload`): trust service + 조건 + managed/inline 정책을 입력으로 받는 범용 모듈. (`modules/iam-role-workload/main.tf:22`)
- GitHub OIDC Role 4종 (bootstrap, `${project}-` 접두어): (`bootstrap/roles.tf`)
  - `gha-deploy-{env}` — sub `repo:{org}/{repo}:environment:{env}`. 권한: ECR push/pull(techai/*), ECS update/RegisterTaskDef, CodeDeploy create, PassRole(task/exec role), SSM/Secrets read, Amplify start-job, Signer sign. max session 3600. (`:24`, `:50`, `:161`)
  - `gha-terraform-readonly` — sub `pull_request`, `ReadOnlyAccess` managed + tfstate read 인라인. (`:181`, `:214`)
  - `gha-terraform-apply-{env}` — sub `environment:tf-{env}`, `PowerUserAccess` + IAM 관리 인라인 + tfstate RW. (`:248`, `:284`)
  - `gha-security-scan` — sub `ref:refs/heads/main`, ECR describe/pull + Inspector findings. (`:383`, `:412`)
- OIDC Provider: `token.actions.githubusercontent.com`, aud `sts.amazonaws.com`. (`bootstrap/oidc.tf:5`)

### Secrets Manager (env별)
| 시크릿 | KMS 키 | 출처 |
|---|---|---|
| `{project}/{env}/jwt-signing-key` | `{env}-auth` | `envs/prod/secrets.tf:11` |
| `{project}/{env}/openai-api-key` | `{env}-ai` | `envs/prod/secrets.tf:40` |
| `{project}/{env}/mongodb-uri` | `{env}-data` | `envs/prod/secrets.tf:61` |
| `{project}/{env}/elasticache-auth-token` (enable_elasticache 시) | `{env}-data` | `envs/prod/secrets.tf:83` |
| `{project}/{env}/github-pat-amplify` (enable_amplify 시) | `{env}-auth` | `envs/prod/frontend.tf:7` |
- Aurora master 비밀번호는 Managed Master User Password가 자동 생성(별도 secret 리소스 없음). 모든 초기값은 placeholder + `lifecycle.ignore_changes=[secret_string]`. (`envs/prod/secrets.tf:3`, `:31`)

### Security Group (inbound)
| SG | inbound | source | 출처 |
|---|---|---|---|
| ALB | 80/TCP (+443/TCP, HTTPS 토글 시) | 0.0.0.0/0 | `envs/prod/cluster.tf` `aws_security_group.alb` |
| Workload(서비스별) | container_port(8081~8086) | ALB SG | `modules/ecs-service/main.tf:67` |
| Aurora | 3306 | 워크로드 SG들(services.tf rule) | `modules/aurora-mysql/main.tf:53`; `envs/prod/services.tf:305` |
| Valkey | 6379 | 워크로드 SG들 | `modules/elasticache-valkey/main.tf:56`; `envs/prod/services.tf:317` |
| MSK Provisioned | 9098(IAM), 9094(TLS), 11001-11002(monitoring), self all | 워크로드 SG들 | `modules/msk-provisioned/main.tf:54`, `:66`, `:90` |
| MSK Serverless | 9098(IAM SASL) | 워크로드 SG들 | `modules/msk-serverless/main.tf:37` |
| VPCE | 443 | private-app 서브넷 CIDR | `modules/network/vpc_endpoints.tf:23` |
- services.tf의 데이터 SG 인바운드는 워크로드별로 선택 부여: aurora_consumers = auth/emerging-tech/bookmark/agent; cache_consumers = auth/chatbot/bookmark; msk_consumers = emerging-tech/bookmark/agent. (`envs/prod/services.tf:282`, `:290`, `:297`)

---

## 6. 상태·CI/CD (S3 + DynamoDB / GitHub OIDC / ECR)

### S3 state bucket
- 이름 default `{project}-tfstate-{account}-apne2`. versioning Enabled, SSE-KMS(tfstate 키, bucket_key on), BPA 4종, **Object Lock GOVERNANCE 30일**, lifecycle(noncurrent 90일 만료, multipart abort 7일), 버킷 정책(insecure transport deny + unencrypted put deny), `prevent_destroy`. (`bootstrap/state.tf:5`, `:11`~`:122`)

### DynamoDB lock
- `techai-tflock`(default), `PAY_PER_REQUEST`, hash_key `LockID`, PITR on, SSE(tfstate KMS), `prevent_destroy`. (`bootstrap/state.tf:125`)

### GitHub OIDC
- §5 IAM 참고. Provider + 4종 Role. (`bootstrap/oidc.tf`, `bootstrap/roles.tf`)

### ECR
- 리포 7개: api-gateway, api-emerging-tech, api-auth, api-chatbot, api-bookmark, api-agent, **batch-source**. 이름 `techai/{module}`. (`bootstrap/ecr.tf:8`)
- `image_tag_mutability=IMMUTABLE`, `scan_on_push=true`, encryption KMS(ecr 키), `prevent_destroy`. (`bootstrap/ecr.tf:46`, `:48`, `:52`, `:63`)
- lifecycle: untagged 7일 만료, tagged 60개 초과분 정리. (`bootstrap/ecr.tf:76`~`:99`)
- repo policy: gha-deploy-* push, ecs-tasks pull, bootstrap-admin 외 삭제 deny. (`bootstrap/ecr.tf:107`~`:159`)

---

## 7. 환경 차이 매트릭스 (dev / beta / prod)

| 항목 | dev | beta | prod | 출처 |
|---|---|---|---|---|
| ALB 프로토콜 | HTTP 80 | HTTP 80 | HTTPS 443 (+80→443 리다이렉트) | `*/cluster.tf`, `prod/terraform.tfvars` |
| VPC CIDR | 10.10.0.0/16 | 10.20.0.0/16 | 10.30.0.0/16 | `*/terraform.tfvars` |
| NAT 개수 | 1 (single) | 1 (single) | 3 (AZ별) | `dev/variables.tf:167`, `beta/terraform.tfvars:7`, `prod/terraform.tfvars:7` |
| Aurora 모드 | serverlessv2 0.5–2.0 ACU | serverlessv2 0.5–4.0 ACU | provisioned 3×db.r7g.large, iopt1 | tfvars/variables |
| Aurora backup/삭제보호 | 1d / false | 7d / false | 30d / true | tfvars/variables |
| ElastiCache | t4g.micro, replicas 0, single-AZ | t4g.small, replicas 1, Multi-AZ, snap 3 | t4g.small, replicas 1, Multi-AZ, snap 7 | tfvars/variables |
| MSK | off (enable_msk false) | Serverless | Provisioned 3×kafka.m7g.large 3.9.x.kraft | `dev/variables.tf:55`, `beta/terraform.tfvars:25`, `prod/terraform.tfvars:29` |
| ECS desired / min / max | 1 / 1 / 3 | 1 / 1 / 4 | 2 / 2 / 6 | `dev/variables.tf:184`, `beta/terraform.tfvars:29`, `prod/terraform.tfvars:36` |
| Amplify | off | off | off | `enable_amplify` default false (모든 env) |
| deletion_protection (Aurora) | false | false | true | tfvars/variables |
| KMS 키 수 | env 5 (+bootstrap 2 공유) | env 5 | env 5 | §5 |
| VPC endpoints | on | on | on | tfvars/variables |

주의: ECS desired/min/max는 tfvars의 `ecs_desired_count`/`ecs_autoscaling_min/max_count` 값이며, 이 값은 **api-gateway·api-auth에만** min/max로 전달된다. 나머지 4개 서비스의 autoscaling min/max는 모듈 default(2/10)다(§1).

---

## 8. 데이터 흐름 서술

- 인입: client → ALB(prod=HTTPS 443, dev/beta=HTTP 80, path-based) → 각 ECS 서비스(api-gateway 8081 fallback `/*`, api-auth 8083 `/auth/*`, api-emerging-tech 8082 `/emerging-tech/*`, api-chatbot 8084 `/chatbot/*`, api-bookmark 8085 `/bookmark/*`, api-agent 8086 `/agent/*`). prod는 HTTP 80 요청을 443으로 301 리다이렉트. ALB→Fargate 백엔드 레그는 모든 env에서 HTTP. (`envs/prod/services.tf`, `cluster.tf`)
- 프런트(Amplify/CloudFront)는 코드상 비활성/미호출이라 현재 흐름에 포함되지 않음(§3).
- ECS 서비스 → 데이터: Aurora(3306), Valkey(6379), MSK(9098/9094)로 SG 인바운드가 워크로드별 선택 부여. MongoDB Atlas는 외부 서비스로 시크릿 URI를 통해 연결. (`envs/prod/services.tf:282`~`:347`)
- 이벤트 흐름: api-agent가 `kafka-cluster:*` 권한으로 MSK에 produce/consume(topic `{project}.conversation.*`, group `{project}.*`). MSK는 prod=Provisioned, beta=Serverless, dev=없음. (`envs/prod/task_roles.tf:158`)
- 캐시 경로: api-auth/chatbot/bookmark가 Valkey 사용(auth_token 시크릿). (`envs/prod/services.tf:290`)
- CQRS: 코드 주석/네트워크 표기상 쓰기=Aurora, 읽기=MongoDB Atlas, 동기화=Kafka(MSK)로 설계됨. Terraform은 인프라(Aurora·MSK·MongoDB 시크릿)만 제공하고 애플리케이션 동기화 로직은 코드 범위 밖. (`modules/network/main.tf:4`; backend CLAUDE.md 설계와 정합)

---

## 9. 확인 불가 항목

- **CloudFront 실제 배포 여부**: `cloudfront-spa` 모듈은 정의돼 있으나 어떤 env의 `.tf`에서도 호출되지 않음. 실제 배포되는지 코드만으로는 확인 불가.
- **Amplify 실제 활성 여부**: `enable_amplify` default false이고 tfvars에서 true로 켜는 곳이 없음. 운영에서 별도 변수 주입으로 켜는지는 코드 외부 사안 → 확인 불가.
- **Aurora Multi-AZ 보장**: 명시적 multi-AZ 플래그가 없고, 3 AZ subnet group + (prod) instance_count 3에 의존. 인스턴스가 실제로 서로 다른 AZ에 배치되는지는 AWS 배치 정책에 따라 결정 → 코드만으로 100% 단정 불가.
- **MongoDB Atlas 클러스터/사양**: Terraform 관리 밖(외부). 연결 URI 시크릿만 존재.
- **batch-source 실행 방식**: ECR 리포만 있고 ECS 서비스 정의가 없음. 어떻게 실행되는지(Scheduled Task, 외부 Jenkins 등) Terraform 코드에 없음 → 확인 불가.
- **dev/beta의 Aurora·ElastiCache·MSK 인스턴스 실제 ARN·엔드포인트**: 시드 placeholder와 토글에 의존하며 apply 결과 산출물이라 코드만으로 값 확인 불가.
- **WAF / ACM 인증서**: 인증서 리소스 자체는 코드에 없다(외부에서 발급). ALB HTTPS는 발급된 ACM ARN을 `var.alb_certificate_arn`으로 주입받아 쓴다(prod tfvars). CloudFront용 WAF/cert는 여전히 모듈 변수로만 존재(default null).
