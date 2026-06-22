# AWS Well-Architected Review — tech-n-ai

> 근거: `devops/aws/architecture-facts.md` (Terraform 코드 추출). 다이어그램은 `devops/aws/{dev,beta,prod}/*.drawio` 참고.
> 이 리뷰는 코드를 바꾸지 않습니다 — 현재 구현을 6개 기둥으로 점검하고 권고만 합니다.
> 외부 사실은 AWS 공식 문서 URL로 근거를 답니다. 비용은 **추정치**이며 가정을 명시합니다.

## 요약 (기둥별 신호등)

| 기둥 | 상태 | 한 줄 평 |
|---|---|---|
| 운영 우수성 | 🟢 양호 | Terraform 모듈화 + GitHub OIDC + CodeDeploy Blue/Green. 관측성 사이드카는 꺼져 있음 |
| 보안 | 🟢 양호 | KMS 키 분리, 최소권한 task role, 키리스 OIDC, private-data 격리. ALB가 HTTP(:80)인 점이 약점 |
| 신뢰성 | 🟡 주의 | prod는 Multi-AZ·다중 NAT로 견고. dev/beta는 단일 NAT·단일 노드라 SPOF 존재(의도된 비용 절감) |
| 성능 효율 | 🟢 양호 | Graviton(ARM64) + Aurora Serverless v2 + VPC Endpoint. 적절한 사이징 |
| 비용 최적화 | 🟡 주의 | env별 차등 사이징은 좋음. prod NAT 3개·MSK Provisioned·Aurora iopt1이 비용 주동인 |
| 지속가능성 | 🟢 양호 | Graviton 효율, 오토스케일링·Serverless로 유휴 자원 축소 |

참고: AWS Well-Architected Framework — <https://docs.aws.amazon.com/wellarchitected/latest/framework/welcome.html>

---

## 1. 운영 우수성 (Operational Excellence)

**현재 구현**
- 인프라가 Terraform으로 모듈화돼 있고(`modules/` 11개), 환경 조립 계층(`envs/{dev,beta,prod}`)이 모듈 호출만 한다. 환경 파일이 byte 단위로 동일하고 차이는 tfvars로만 둬서, 환경 간 드리프트가 구조적으로 줄어든다(facts 머리말).
- 배포는 GitHub Actions OIDC 역할로 수행된다(`gha-deploy-{env}`). 키리스 연합이라 장기 자격증명을 두지 않는다(facts §5).
- 릴리스는 CodeDeploy Blue/Green(`Canary10Percent5Minutes`) + ECS circuit breaker로, ALB 5xx·p95 지연 알람에 걸리면 자동 롤백한다(facts §1).
- 상태는 S3+DynamoDB 원격 백엔드(버전닝·Object Lock·PITR)로 관리된다(facts §6).

**위험·격차**
- ADOT·FireLens 사이드카가 **모든 env에서 비활성**이다(facts §1). 분산 추적(X-Ray)과 구조화 로그 라우팅이 꺼져 있어, 장애 시 서비스 간 호출 추적이 어렵다.
- ALB 액세스 로그 설정이 코드에 보이지 않는다(확인 필요).

**권고**
- 최소한 prod에서 ADOT 사이드카를 켜 X-Ray 추적을 확보한다(`enable_adot=true`). 비용은 작고 운영 가시성 이득이 크다.
- CodeDeploy 알람 임계(5xx 1%, p95 1.5s, chatbot 5.0s)가 실제 트래픽에 맞는지 초기 운영 후 재조정한다.

### EB vs ECS Fargate (대안 비교)

이 워크로드는 ECS Fargate task 정의·서비스·오토스케일링·사이드카를 직접 운영한다. `deploy-on-aws`의
`elastic-beanstalk` 스킬은 정의상 "이미 ECS task 정의/Fargate 구성이 있으면 EB는 부적합"이라고 명시한다.
그래서 EB로 가는 별도 아키텍처는 만들지 않고, 선택 근거만 기록한다.

| 관점 | ECS Fargate (현재) | Elastic Beanstalk (대안) |
|---|---|---|
| 제어 수준 | task 정의·사이드카(ADOT/FireLens)·Blue/Green 트래픽까지 세밀 제어 | 플랫폼이 EC2·배포·스케일링을 위임 관리, 세밀 제어 약함 |
| 컨테이너 모델 | awsvpc·ARM64·다중 컨테이너 task 자연스러움 | 단일 컨테이너/플랫폼 중심, 멀티서비스 라우팅은 별도 구성 필요 |
| 서버 관리 | 서버리스(노드 패치 불필요) | EC2 인스턴스 패치를 플랫폼이 관리(단, EC2는 존재) |
| 적합성 판정 | 6개 마이크로서비스·CQRS·이벤트(MSK) 구조에 적합 | 이 구조에는 부적합(스킬 자체 가이드) |

근거: ECS/Fargate — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html> ·
Elastic Beanstalk 개념 — <https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/Welcome.html>

---

## 2. 보안 (Security)

**현재 구현**
- KMS 키를 용도별로 분리했다: bootstrap 공유 2개(tfstate, ecr) + env당 5개(data, s3-app, auth, ai, logs). 모두 자동 로테이션 on(facts §5). 키 분리는 폭발 반경(blast radius)을 줄인다.
- IAM 최소권한: 서비스별 task role 6개가 각자 필요한 시크릿·데이터에만 접근한다(api-auth만 jwt+rds, api-chatbot만 openai+mongodb 등). 공유 execution role은 pull·로그·시크릿 read로 한정(facts §5).
- 키리스 CI/CD: GitHub OIDC + `sub` 조건(`environment:{env}`, `tf-{env}`, `pull_request`, `refs/heads/main`)으로 워크플로별 권한을 분리(facts §5).
- 데이터 보호: private-data 서브넷 인터넷 격리, Aurora IAM DB 인증, Valkey TLS+토큰, MSK TLS+IAM SASL, ECR 태그 IMMUTABLE+scan-on-push, S3 state Object Lock GOVERNANCE + HTTPS/KMS 강제 정책(facts §5/§6).
- VPC Flow Logs 모든 env on(facts §4).

**위험·격차**
- **ALB가 HTTP :80 단독**이다(ACM/HTTPS 아님, facts §1). 클라이언트→ALB 구간이 평문이다. 가장 큰 보안 격차.
- WAF가 붙어 있지 않다(CloudFront 모듈 변수로만 존재, default null — facts §3). 단, CloudFront 자체가 미배포라 현재 WAF 적용 지점이 없다.
- 시크릿 자동 로테이션은 Aurora Managed Master Password만 자동이고, JWT/OpenAI/Mongo 등은 수동 로테이션 설계로 보인다(확인 필요).

**권고**
- ALB에 ACM 인증서를 붙여 HTTPS :443 + HTTP→HTTPS 리다이렉트를 적용한다. prod 우선. (High)
- 외부 노출 진입점(ALB 또는 향후 CloudFront)에 AWS WAF를 적용한다. (Medium)
- Secrets Manager 로테이션 람다 또는 일정 기반 로테이션을 JWT·API 키에 적용 검토. (Medium)

근거: Security 기둥 — <https://docs.aws.amazon.com/wellarchitected/latest/security-pillar/welcome.html> ·
ALB HTTPS 리스너 — <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/create-https-listener.html>

---

## 3. 신뢰성 (Reliability)

**현재 구현**
- prod: 3 AZ에 NAT 3개(AZ 격리), Aurora 3 인스턴스(writer+reader 2), ElastiCache Multi-AZ+replica, MSK Provisioned 3 broker(RF=3, min.insync.replicas=2), Aurora 백업 30일·삭제보호 on(facts §2/§4/§7).
- ECS: deployment circuit breaker + 오토스케일링(prod min2/max6, 단 4개 서비스는 모듈 default 2/10)(facts §1).
- ECR·S3 state에 `prevent_destroy`, lifecycle, 버전닝(facts §6).

**위험·격차**
- **dev/beta 단일 NAT가 SPOF**다(facts §4). 단일 NAT가 죽으면 해당 env의 모든 사설 서브넷 아웃바운드가 끊긴다. 의도된 비용 절감이지만, beta가 실서비스 전 검증 환경이라면 위험.
- dev ElastiCache replica 0, Aurora serverless 단일 → 복구 목표(RTO/RPO)가 prod와 크게 다르다.
- 4개 서비스(emerging-tech, chatbot, bookmark, agent)의 autoscaling max가 모듈 default 10으로, 의도와 다를 수 있다(facts §1, §7 주의).

**권고**
- beta를 prod 유사 검증 환경으로 쓸 거면 NAT를 AZ별로 올리는 것을 검토(비용↑). 순수 개발용이면 현 상태 유지가 합리적. (Medium)
- 데이터 계층 RTO/RPO 목표를 env별로 문서화하고, dev/beta의 한계를 명시. (Low)
- 4개 서비스의 autoscaling min/max를 tfvars에서 명시 전달해 의도를 코드로 고정. (Low)

근거: Reliability 기둥 — <https://docs.aws.amazon.com/wellarchitected/latest/reliability-pillar/welcome.html> ·
NAT Gateway 가용성 — <https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat-gateway.html>

---

## 4. 성능 효율 (Performance Efficiency)

**현재 구현**
- Fargate **ARM64(Graviton)**: 동급 x86 대비 가격/성능이 좋다(facts §1).
- Aurora **Serverless v2**(dev/beta): 부하에 따라 ACU 자동 스케일, 유휴 시 축소(facts §2).
- **VPC Endpoint**로 ECR·Logs·KMS·STS·Secrets·SSM·S3·DynamoDB 트래픽이 NAT를 우회한다 → 지연·비용 동시 개선(facts §4).
- ALB 타깃 트래킹 오토스케일링(CPU 기반)(facts §1).

**위험·격차**
- api-chatbot은 LLM 호출로 지연이 크다(p95 롤백 임계 5.0s로 별도 설정). 외부 LLM 의존이라 자체 인프라로는 한계.

**권고**
- chatbot 계열은 동시성 기반(요청 큐 길이) 스케일링이나 비동기화 검토. (Low)
- prod Aurora가 provisioned 고정인데, 트래픽 변동이 크면 Serverless v2 + 일부 reader provisioned 혼합도 검토. (Low)

근거: Performance 기둥 — <https://docs.aws.amazon.com/wellarchitected/latest/performance-efficiency-pillar/welcome.html> ·
Graviton/Fargate — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-arm64.html>

---

## 5. 비용 최적화 (Cost Optimization)

**현재 구현**
- env 차등 사이징이 명확하다: dev는 단일 NAT·Serverless v2 소형·MSK off, prod만 Multi-AZ·Provisioned(facts §7). 비용을 환경 목적에 맞게 분리한 좋은 설계.
- VPC Endpoint로 NAT 데이터 처리 비용을 줄인다.
- ECR lifecycle(untagged 7일, tagged 60개 초과 정리)로 스토리지 누적 억제(facts §6).

### 환경별 월 비용 개략 추정 (매우 거친 추정 — 반드시 검증 필요)

> **가정**: 서울 리전(ap-northeast-2), On-Demand, 24/7 가동, 트래픽·데이터 전송량은 소규모로 가정.
> 아래는 **자릿수 감을 잡기 위한 계획용 추정**이며 실제 청구와 다릅니다. 정확한 값은
> AWS Pricing Calculator(<https://calculator.aws/>)로 구성별 산정하세요. `awspricing` MCP가 연결돼 있으면
> 각 항목을 실데이터로 다시 산정하는 것을 권장합니다.

| 항목 | dev | beta | prod | 비용 주동인 |
|---|---|---|---|---|
| ECS Fargate (서비스 6개) | 낮음 (desired 1) | 낮음 (desired 1) | 중간 (desired 2, ARM64) | task 수 × vCPU/메모리 |
| Aurora | 낮음 (Serverless v2 0.5–2 ACU) | 낮음~중간 (0.5–4 ACU) | 높음 (3×db.r7g.large + iopt1) | prod 인스턴스 3개 상시 |
| ElastiCache Valkey | 매우 낮음 (t4g.micro 단일) | 낮음 (t4g.small ×2) | 낮음 (t4g.small ×2) | 노드 수 |
| MSK | 없음 | 중간 (Serverless) | 높음 (Provisioned 3×m7g.large + EBS) | prod broker 상시 |
| NAT Gateway | 낮음 (1개) | 낮음 (1개) | 중간 (3개 + 데이터 처리) | prod NAT 3개 |
| CloudWatch/Logs/기타 | 낮음 | 낮음 | 낮음~중간 | 로그량 |

**prod 비용 3대 주동인**: ① Aurora provisioned 3 인스턴스 + I/O-Optimized, ② MSK Provisioned 3 broker 상시, ③ NAT 3개.

**권고**
- prod 안정화 후, Fargate에 **Compute Savings Plans** 또는 Aurora **Reserved Instances** 적용으로 상시 가동분 비용 절감 검토. (Medium)
- 비프로덕션(dev) ECS에 **Fargate Spot** 적용 검토(중단 허용 시). (Low)
- prod NAT 3개 ↔ VPC Endpoint 커버리지를 재점검해, NAT 데이터 처리량을 더 줄일 수 있는지 확인. (Low)
- MSK가 prod에서 정말 Provisioned가 필요한지(처리량·지연 요구) 재검토. 요건이 낮으면 Serverless가 더 쌀 수 있음. (Medium)

근거: Cost 기둥 — <https://docs.aws.amazon.com/wellarchitected/latest/cost-optimization-pillar/welcome.html> ·
Savings Plans — <https://docs.aws.amazon.com/savingsplans/latest/userguide/what-is-savings-plans.html>

---

## 6. 지속가능성 (Sustainability)

**현재 구현**
- Graviton(ARM64) Fargate는 와트당 성능이 좋아 같은 작업에 더 적은 에너지를 쓴다(facts §1).
- 오토스케일링·Aurora Serverless v2로 유휴 자원을 줄인다(수요에 맞춰 축소).
- 단일 리전(ap-northeast-2) 운영으로 불필요한 리전 간 복제 자원이 없다.

**권고**
- 사용량 적은 시간대(야간)에 dev/beta ECS를 스케일 다운/정지하는 스케줄을 검토(비용·지속가능성 동시 이득). (Low)

근거: Sustainability 기둥 — <https://docs.aws.amazon.com/wellarchitected/latest/sustainability-pillar/sustainability-pillar.html>

---

## 권고 우선순위

| ID | 기둥 | 심각도 | 권고 | 근거 |
|---|---|---|---|---|
| R-01 | 보안 | **High** | ALB에 ACM 인증서로 HTTPS(:443) 적용 + HTTP 리다이렉트 (특히 prod) | facts §1 (ALB :80 단독) |
| R-02 | 보안 | Medium | 외부 진입점에 AWS WAF 적용 | facts §3 (WAF 미부착) |
| R-03 | 운영 | Medium | prod에 ADOT 사이드카 활성화(X-Ray 추적) | facts §1 (sidecar off) |
| R-04 | 비용 | Medium | prod 상시 가동분에 Savings Plans / Aurora RI, MSK Provisioned 필요성 재검토 | facts §2/§7 |
| R-05 | 신뢰성 | Medium | beta가 prod-유사 검증 환경이면 단일 NAT 재검토 | facts §4 |
| R-06 | 보안 | Medium | JWT·API 키 시크릿 로테이션 자동화 검토 | facts §5 |
| R-07 | 신뢰성 | Low | 4개 서비스 autoscaling min/max를 tfvars로 명시 | facts §1/§7 |
| R-08 | 비용 | Low | dev ECS에 Fargate Spot / 야간 스케일다운 | facts §7 |

---

## 확인 불가 / 추가 확인 필요

- **CloudFront·Amplify 실제 배포 여부**: 모듈만 있고 어느 env에서도 호출되지 않음. 운영에서 별도 변수로 켜는지 코드 외부 사안(facts §3/§9). 만약 운영에서 켠다면 이 리뷰의 엣지/보안(WAF·HTTPS) 평가가 달라진다.
- **ALB 액세스 로그·시크릿 로테이션 주기**: 코드만으로 확인 불가. 운영 설정 확인 필요.
- **비용 수치**: 위 표는 자릿수 추정. 실제 값은 AWS Pricing Calculator 또는 `awspricing` MCP로 재산정 필요.
- **batch-source 실행 방식**: ECR 리포만 존재, 실행 방식(스케줄·외부 Jenkins)은 코드 밖(facts §9).

## 참고 문서 (인용 URL)

- AWS Well-Architected Framework — <https://docs.aws.amazon.com/wellarchitected/latest/framework/welcome.html>
- Security Pillar — <https://docs.aws.amazon.com/wellarchitected/latest/security-pillar/welcome.html>
- Reliability Pillar — <https://docs.aws.amazon.com/wellarchitected/latest/reliability-pillar/welcome.html>
- Performance Efficiency Pillar — <https://docs.aws.amazon.com/wellarchitected/latest/performance-efficiency-pillar/welcome.html>
- Cost Optimization Pillar — <https://docs.aws.amazon.com/wellarchitected/latest/cost-optimization-pillar/welcome.html>
- Sustainability Pillar — <https://docs.aws.amazon.com/wellarchitected/latest/sustainability-pillar/sustainability-pillar.html>
- AWS Fargate — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html>
- ECS on ARM64/Graviton — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-arm64.html>
- ALB HTTPS Listener — <https://docs.aws.amazon.com/elasticloadbalancing/latest/application/create-https-listener.html>
- Elastic Beanstalk — <https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/Welcome.html>
- AWS Pricing Calculator — <https://calculator.aws/>
