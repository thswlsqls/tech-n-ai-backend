# envs/dev-ec2 — 단일 EC2(퍼블릭 서브넷) docker-compose 토폴로지 설계서

이 문서는 [free-tier-design.md](./free-tier-design.md)가 "범위 밖"으로 미뤄둔 단일 EC2 토폴로지를 별도 환경 `envs/dev-ec2`로 구현하기 위한 설계안이다. 기존 Fargate 기반 `envs/dev`를 대체하는 게 아니라, **신규 계정 $200 크레딧으로 6개월을 버티는 초저비용 dev**가 필요할 때 고르는 다른 환경이다. 실제 `.tf`는 이 문서에 담지 않고, 무엇을 만들고 무엇을 재사용하며 어디를 조심해야 하는지를 정리한다.

## 목적과 범위

- 목표: 백엔드 6개 모듈을 EC2 한 대에서 docker-compose로 돌려, dev 월 비용을 ~$34~61로 낮춘다(계산 근거는 free-tier-design.md 비용 절).
- 전제: 신규 AWS 계정(크레딧 모델, 6개월 자동 종료). 데이터·메시징은 free-tier-design.md 시나리오 1과 동일하게 **외부 무기한 무료 SaaS**(Aiven Kafka·Upstash/Redis Cloud·외부 MySQL·MongoDB Atlas M0)에 둔다.
- 비범위: prod·beta, HA·무중단 배포, 오토스케일링. 이 토폴로지는 단일 장애점이고 dev 전용이다.

## 아키텍처 개요

```
인터넷
  │  (Elastic IP, 80/443 또는 gateway 포트만 SG 허용)
  ▼
[ 퍼블릭 서브넷 ]
  EC2 1대 (Amazon Linux 2023, arm64, t4g.large)
    └ docker compose
        ├ api-gateway / api-auth / api-emerging-tech
        ├ api-chatbot / api-bookmark / api-agent
        └ (선택) kafka·redis 컨테이너 동거
  └ IGW 로 직접 아웃바운드 (NAT 없음)
        │
        ▼ TLS+인증
  외부 SaaS: Aiven Kafka · Upstash/Redis Cloud · 외부 MySQL · MongoDB Atlas M0
```

핵심은 NAT·ALB·Fargate·VPC 인터페이스 엔드포인트가 전부 빠진다는 점이다. 퍼블릭 서브넷에 있으므로 아웃바운드는 인터넷 게이트웨이로 직접 나가고, 인바운드는 보안그룹으로 최소 포트만 연다.

## 무엇을 새로 만들고, 무엇을 재사용/버리는가

| 자원 | dev-ec2에서 | 비고 |
|---|---|---|
| VPC·서브넷·IGW | **재사용** `modules/network` | `enable_nat_gateway=false`, `enable_vpc_endpoints=false`. 서브넷·IGW·라우트는 과금 없음 |
| NAT Gateway | **버림** | 퍼블릭 서브넷 직접 아웃바운드 |
| VPC 인터페이스 엔드포인트 | **버림** | 인터넷으로 ECR·SSM 접근 |
| ALB | **버림** | api-gateway가 라우팅 |
| ECS·Fargate 6개 | **버림** | docker-compose로 대체 |
| EC2 1대 + EBS + Elastic IP | **새로 만듦** | 이 환경의 핵심 |
| IAM 인스턴스 프로파일 | **새로 만듦** | SSM·ECR·파라미터 읽기 |
| KMS 5개 | **축소** | dev-ec2는 로그·시크릿 최소화. 필요 시 1개만 |
| Aurora·ElastiCache·MSK 모듈 | **호출 안 함** | 외부 SaaS로 대체 |

## envs/dev-ec2 파일 구성 (구현 시 만들 것)

`envs/dev`의 레이아웃을 따른다.

- `providers.tf` — AWS provider, 리전 `ap-northeast-2`, 기본 태그.
- `backend.tf` + `backend.hcl` — bootstrap이 만든 S3 state 버킷·DynamoDB Lock 재사용(키만 `dev-ec2/terraform.tfstate`로 분리).
- `variables.tf` — `project`, `environment="dev-ec2"`, `vpc_cidr`, `azs`, `instance_type`, `root_volume_size`, `allowed_ingress_cidrs`, 외부 SaaS 연결용 SSM 경로 등.
- `terraform.tfvars` — dev-ec2 기본값.
- `network.tf` — `module.network` 호출(NAT·엔드포인트 off).
- `ec2.tf` — `aws_instance` + `aws_eip` + `aws_eip_association` + `aws_ebs_volume`(루트만 쓰면 생략).
- `security.tf` — EC2 SG(인바운드 최소), 아웃바운드 전체 허용.
- `iam.tf` — 인스턴스 역할·프로파일.
- `user_data.sh.tftpl` — docker 설치 + compose 기동 스크립트(아래).
- `outputs.tf` — 퍼블릭 IP/도메인, 인스턴스 ID.

## EC2 상세

- **AMI**: Amazon Linux 2023 arm64를 SSM Public Parameter로 조회(`data "aws_ssm_parameter"`로 `/aws/service/ami-amazon-linux-latest/...`). AMI ID를 하드코딩하지 않는다.
- **인스턴스 타입**: 기본 `t4g.large`(2 vCPU·8GB, arm64). 6개 JVM 합계 ~6.5GB라 8GB가 무난. 힙을 `-Xmx`로 조이면 `t4g.medium`(4GB)도 가능 — 변수로 노출.
- **스토리지**: 루트 gp3 30GB. 자체 Kafka/Redis를 동거시키면 데이터용으로 늘린다.
- **퍼블릭 IP**: `modules/network`의 퍼블릭 서브넷은 `map_public_ip_on_launch=false`다(자동 공인 IP 차단). 따라서 EC2에 **Elastic IP를 명시로 붙이거나** 인스턴스에 `associate_public_ip_address=true`를 준다. 안정적 주소·DNS 연결을 위해 EIP 권장. (퍼블릭 IPv4는 부착돼 있어도 시간당 과금됨 — free-tier-design.md 비용 절 참고.)
- **메타데이터**: IMDSv2 강제(`metadata_options { http_tokens = "required" }`).

## 기동 방식 (user-data)

user-data로 부팅 시 다음을 한다.

1. docker engine + compose plugin 설치.
2. 백엔드 이미지 확보 — **사전 빌드 이미지 pull 권장**(ECR 또는 레지스트리). 인스턴스에서 git clone + build는 메모리·시간을 많이 먹으므로 dev라도 피한다.
3. 연결 정보 주입 — 비밀이 아닌 값은 SSM Parameter Store(표준 파라미터, 무료)에서, 비밀값(외부 SaaS 비밀번호·OpenAI 키 등)은 SSM SecureString 또는 Secrets Manager에서 인스턴스 역할로 읽어 `.env`로 떨군 뒤 `docker compose up -d`.
4. 백엔드가 이미 `REDIS_*`·`KAFKA_BOOTSTRAP_SERVERS`·`MONGODB_URI` 등 환경변수로 읽으므로 **백엔드 코드 변경은 없다**. Kafka를 외부 SaaS의 SASL_SSL로 붙일 때만 Spring 설정(`security.protocol`·`sasl.*`) 추가가 필요하다(free-tier-design.md "적용 시 건드릴 곳"과 동일).

## IAM

인스턴스 역할에 최소 권한만 부여한다.

- `AmazonSSMManagedInstanceCore` — SSM Session Manager로 셸 접속(아래 보안 항목).
- SSM Parameter / Secrets Manager 읽기 — `${project}/dev-ec2/*` 범위로 한정.
- ECR pull — 사전 빌드 이미지를 ECR에 둘 경우.

## 보안

퍼블릭 서브넷 노출이라 인바운드를 최소화한다.

- **SSH 포트 열지 않음**: 셸은 SSM Session Manager로 접속(인바운드 22 불필요, 키 관리 불필요). 인스턴스 역할에 `AmazonSSMManagedInstanceCore` 필요.
- **인바운드 SG**: 외부에 노출할 포트만(예: 443, 또는 게이트웨이 포트). 가능하면 `allowed_ingress_cidrs`로 접근 IP를 좁힌다. TLS가 필요하면 게이트웨이 앞에 경량 리버스 프록시(예: Caddy 컨테이너)로 Let's Encrypt를 둔다.
- **데이터 저장소**: 외부 SaaS는 TLS+인증으로 붙는다. Kafka/Redis를 같은 EC2에 동거시키면 docker 네트워크/localhost에만 바인딩하고 외부로 절대 노출하지 않는다.
- **상태**: state에 비밀값이 들어가지 않도록 시크릿은 SSM/Secrets Manager에서 읽고 Terraform 변수로 평문 전달하지 않는다.

## 데이터 지속성과 운영

- 외부 SaaS(Atlas·Aiven·Upstash)에 든 데이터는 EC2 수명과 무관하게 살아남는다. 이게 이 토폴로지를 신규 계정에서 쓰는 이유다(계정이 6개월 뒤 닫혀도 데이터는 외부에 남음).
- 자체 동거시킨 Kafka/Redis 데이터는 EBS에만 있으므로, 인스턴스 교체 시 사라질 수 있음을 전제로 한다(dev라 허용 가능).
- 단일 장애점이다. 인스턴스가 죽으면 수동 재생성/재기동. dev 용도라 수용한다.

## 비용

free-tier-design.md의 계산을 그대로 따른다. 권장(t4g.large + 외부 SaaS) 기준 **월 ~$61**, 최소(t4g.medium, 힙 조임) 기준 **월 ~$34**. 최소 구성이면 $200 크레딧이 6개월 계정 수명을 거의 그대로 덮어 실지출 0에 수렴한다. 야간·주말 인스턴스 정지로 더 줄일 수 있다.

## 적용 절차 (구현 후)

```bash
cd devops/terraform/envs/dev-ec2
terraform init -backend-config=backend.hcl
terraform plan -var-file=terraform.tfvars -out=tfplan
terraform apply tfplan
# 접속: SSM Session Manager 로 셸 → docker compose ps 로 기동 확인
```

## 트레이드오프 / 한계

- HA·무중단 배포·오토스케일링 없음. prod엔 부적합, dev 전용.
- 퍼블릭 서브넷 노출 → SG·SSM·TLS로 표면을 좁히는 게 필수.
- 한 대에 6개 JVM → 메모리 압박. 힙 설정·서비스 수를 실측으로 맞춰야 한다.
- 기존 `envs/dev`(Fargate)와 별도 환경이라 둘을 동시에 켜면 비용이 합산된다. dev-ec2를 쓰는 동안 `envs/dev`는 destroy하거나 apply하지 않는다.

## 확인 필요 (구현 전 직접 확인)

- EC2·EBS·퍼블릭 IPv4의 서울(ap-northeast-2) 정확 단가: AWS Pricing Calculator로 재산정.
- 6개 모듈의 실제 메모리 사용량 → t4g.medium 충분 여부 / large 필요 여부는 띄워서 측정.
- CI(예: Jenkins)가 `envs/*`를 순회해 terraform을 도는지: 그렇다면 새 `envs/dev-ec2`가 파이프라인 매트릭스에 잡히는지 확인하고 제외/포함을 정한다.
- `modules/network` 재사용 시 퍼블릭 서브넷에 EIP 부착 방식(인스턴스 `associate_public_ip_address` vs `aws_eip`) 중 무엇을 쓸지 확정.
- 사전 빌드 이미지를 어디에 둘지(ECR vs 외부 레지스트리)와 그에 따른 IAM·네트워크.

## 공식 출처

- 비용·무료 한도 근거: [free-tier-design.md](./free-tier-design.md)의 "공식 출처" 절을 그대로 따른다(AWS Free Tier·RDS·MSK·ElastiCache·VPC·Fargate·ALB, Aiven·Upstash·Redis Cloud·Redpanda).
- Terraform `aws_instance`·`aws_eip` — HashiCorp AWS Provider Registry, <https://registry.terraform.io/providers/hashicorp/aws/latest/docs>.
- SSM Session Manager(SSH 없는 접속) — AWS Systems Manager 공식 문서, <https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager.html>.
- EC2·EBS·퍼블릭 IPv4 단가는 위 free-tier-design.md 출처(공식 가격 페이지)와 Pricing Calculator로 확인. 서울 정확 수치는 직접 재산정 필요.
- 저장소 코드(직접 확인): `modules/network/outputs.tf`(`public_subnet_ids`·`internet_gateway_id`), `modules/network/main.tf:67`(`map_public_ip_on_launch=false`), `modules/network/variables.tf`(`enable_nat_gateway`·`enable_vpc_endpoints`), `docker-compose.yml`, `common/core/.../application-common-core.yml`, `common/kafka/.../application-kafka.yml`.
