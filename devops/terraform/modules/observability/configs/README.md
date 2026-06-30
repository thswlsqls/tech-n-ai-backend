# observability/configs — ADOT Collector + FireLens 설정

> 08-observability.md 의 OTLP 표준 + 로그 파이프라인을 실 파일로 구현. ECS Task Definition 에 sidecar 로 부착.

> **현재 상태 — 미가동:** dev·beta·prod 모두 ADOT/FireLens 사이드카가 꺼져 있다
> (`enable_otel_sidecar`·`enable_firelens_sidecar` 기본값 false, 각 env tfvars 미설정).
> 그래서 분산 추적이 수집되지 않고, `masking.lua` 의 PII 마스킹도 적용되지 않는다 —
> 앱 로그가 awslogs 드라이버로 CloudWatch 에 마스킹 없이 그대로 적재된다.
> 마스킹을 실제로 켜려면 사이드카를 활성화하고 이 설정 파일들을 SSM/S3 로 주입해야 한다
> (아래 '설정 파일 배포 옵션' 참고).

## 파일

| 파일 | 용도 |
|---|---|
| `adot-collector.yaml` | ADOT Collector 설정 — OTLP 수신 → ECS 메타 → X-Ray + CloudWatch EMF 송신 |
| `firelens-fluentbit.conf` | FireLens (Fluent Bit) 메인 설정 — 파서·필터·CloudWatch Logs 출력 |
| `parsers.conf` | Spring Boot JSON 로그 + Nginx 액세스 로그 파서 |
| `masking.lua` | PII 마스킹 (이메일·전화번호·신용카드·RRN·JWT·AWS 키) |

## 통합 방법 — ECS Task Definition

본 ecs-service 모듈은 sidecar 자동 추가가 아직 미지원 (세션 5 보강 예정). 임시로 task definition JSON 을 직접 작성하면:

```json
{
  "family": "techai-dev-api-auth",
  "containerDefinitions": [
    {
      "name": "api-auth",
      "image": "<ECR_URI>/techai/api-auth@sha256:...",
      "essential": true,
      "portMappings": [{ "containerPort": 8083 }],
      "environment": [
        { "name": "OTEL_EXPORTER_OTLP_ENDPOINT", "value": "http://localhost:4317" },
        { "name": "OTEL_RESOURCE_ATTRIBUTES",   "value": "service.name=api-auth,deployment.environment=dev" }
      ],
      "logConfiguration": {
        "logDriver": "awsfirelens",
        "options": {
          "Name": "cloudwatch_logs"
        }
      },
      "dependsOn": [
        { "containerName": "log-router", "condition": "START" },
        { "containerName": "otel-collector", "condition": "START" }
      ]
    },
    {
      "name": "otel-collector",
      "image": "public.ecr.aws/aws-observability/aws-otel-collector:latest",
      "essential": false,
      "command": ["--config=/etc/ecs/otel-config.yaml"],
      "environment": [
        { "name": "AWS_REGION",  "value": "ap-northeast-2" },
        { "name": "ENVIRONMENT", "value": "dev" },
        { "name": "SERVICE_NAME", "value": "api-auth" }
      ],
      "secrets": [
        { "name": "AOT_CONFIG_CONTENT", "valueFrom": "arn:aws:ssm:...:parameter/techai/dev/otel/config" }
      ]
    },
    {
      "name": "log-router",
      "image": "public.ecr.aws/aws-observability/aws-for-fluent-bit:stable",
      "essential": true,
      "firelensConfiguration": {
        "type": "fluentbit",
        "options": {
          "config-file-type": "s3",
          "config-file-value": "arn:aws:s3:::techai-dev-firelens-config/fluent-bit.conf"
        }
      },
      "environment": [
        { "name": "AWS_REGION",   "value": "ap-northeast-2" },
        { "name": "ENVIRONMENT",  "value": "dev" },
        { "name": "SERVICE_NAME", "value": "api-auth" }
      ]
    }
  ]
}
```

## 설정 파일 배포 옵션

| 방식 | 장점 | 단점 |
|---|---|---|
| **SSM Parameter Store** (적용) | KMS 암호화, IAM 제어, 즉시 갱신 | 4KB 제한, 다중 파일 결합 어려움 |
| S3 (FireLens config) | 큰 파일 가능, 다중 파일 | KMS·OAC 정책 추가 |
| 컨테이너 이미지 빌드 | 변경 시 재배포 | 변경 빈번 시 부담 |

권장:
- `adot-collector.yaml` → SSM Parameter `/{project}/{env}/otel/config` (4KB 미만)
- `firelens-fluentbit.conf` + `parsers.conf` + `masking.lua` → S3 버킷 `techai-{env}-firelens-config/` (3 파일 결합)

## Spring Boot 통합

`build.gradle` 에 OTel auto-instrumentation Java agent 추가:

```groovy
configurations { otelAgent }
dependencies {
    otelAgent("io.opentelemetry.javaagent:opentelemetry-javaagent:2.10.0")
}
tasks.named("bootBuildImage") {
    environment = [
        // OTel Java agent 자동 포함
        "BPE_DEFAULT_JAVA_TOOL_OPTIONS": "-javaagent:/workspace/BOOT-INF/lib/opentelemetry-javaagent.jar"
    ]
}
```

또는 [Application Signals](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Application-Signals.html) 활성화 시 ADOT Collector 가 자동으로 처리.

## 비용 영향

- ADOT sidecar: vCPU 0.125 + Mem 256MB → Fargate 청구 ~$1.5/task/월. 6 service × 1 task = $9/월 dev 추가.
- FireLens sidecar: vCPU 0.0625 + Mem 128MB → ~$0.7/task/월. 6 × 1 = $4/월 dev 추가.
- CloudWatch Logs: 0.5 GB/월 가정 × $0.50/GB = $0.25/월.
- X-Ray: 트레이스 100K/월 무료, 이후 $5/M traces. dev 트래픽 0 → 무료.

dev 시드 추가 비용: **약 $14/월**.
