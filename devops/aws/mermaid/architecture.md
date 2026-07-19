# tech-n-ai Architecture (Mermaid)

> 근거: `devops/aws/architecture-facts.md` (Terraform 코드에서 추출). drawio 다이어그램과 같은 사실을 텍스트로 표현합니다.
>
> 현재 **배포되는** 구조 기준입니다. CloudFront·Amplify는 모듈만 있고 어느 env에서도 호출되지 않아(facts §3) 진입점은 ALB 뿐입니다. ALB 프로토콜은 환경마다 다릅니다 — prod 는 HTTPS(443) + HTTP(80)→443 리다이렉트(`alb_certificate_arn` 설정), dev/beta 는 HTTP(80) 단독입니다.

## 1. Reference Architecture

ALB(prod=HTTPS 443, dev/beta=HTTP 80, path 라우팅) → ECS Fargate 서비스 6개 → 데이터 저장소. 이벤트 흐름은 점선.
MSK는 env별로 다릅니다(prod=Provisioned, beta=Serverless, dev=없음). 아래는 prod 기준이며, dev는 MSK 노드와 연결이 빠집니다.

```mermaid
flowchart LR
    client["Client / API consumer"]

    subgraph aws["AWS Cloud · ap-northeast-2"]
        alb["ALB<br/>(HTTPS :443, path-based)<br/>HTTP :80 → :443 redirect"]

        subgraph ecs["ECS Cluster (Fargate, ARM64)"]
            gw["api-gateway :8081<br/>/*"]
            auth["api-auth :8083<br/>/auth/*"]
            et["api-emerging-tech :8082<br/>/emerging-tech/*"]
            chat["api-chatbot :8084<br/>/chatbot/*"]
            book["api-bookmark :8085<br/>/bookmark/*"]
            agent["api-agent :8086<br/>/agent/*"]
        end

        subgraph data["Data layer"]
            aurora[("Aurora MySQL<br/>write store :3306")]
            valkey[("ElastiCache Valkey<br/>cache :6379")]
            msk["MSK Kafka<br/>event bus :9098"]
        end

        logs["CloudWatch Logs<br/>/aws/ecs/{env}/{service}"]
    end

    mongo[("MongoDB Atlas<br/>external · CQRS read store<br/>RAG DB · Vector Search")]

    client -->|HTTPS :443| alb
    alb --> gw & auth & et & chat & book & agent

    auth --> aurora
    book --> aurora
    auth --> valkey
    chat --> valkey
    book --> valkey

    et -.->|produce/consume| msk
    book -.->|produce/consume| msk
    agent -.->|produce/consume| msk

    chat --> mongo
    agent --> mongo
    et --> mongo

    ecs --> logs

    %% 환경 차이: dev 에는 MSK 노드와 점선 연결이 없음. beta=MSK Serverless, prod=MSK Provisioned(3 broker).
    %% ALB 프로토콜: prod=HTTPS 443(+80 리다이렉트), dev/beta=HTTP 80. 위 그림은 prod 기준.
    %% 프런트(Amplify/CloudFront)는 모듈만 있고 미배포 → 진입점은 ALB 뿐.

    %% 색상: 공식 브랜드·AWS 카테고리 색으로 계층을 한눈에 구분
    classDef cons fill:#5F6B7A,stroke:#3B4453,color:#fff
    classDef lb fill:#8C4FFF,stroke:#5B2FB0,color:#fff
    classDef svc fill:#ED7100,stroke:#B35600,color:#fff
    classDef rdb fill:#4479A1,stroke:#2D5570,color:#fff
    classDef cache fill:#DC382D,stroke:#9E241C,color:#fff
    classDef bus fill:#231F20,stroke:#000000,color:#fff
    classDef mongodb fill:#00ED64,stroke:#00684A,color:#001E2B

    class client cons
    class alb lb
    class gw,auth,et,chat,book,agent svc
    class aurora rdb
    class valkey cache
    class msk bus
    class mongo mongodb
```

> 색상 구분: Client(회색) · ALB(AWS 네트워킹 보라 `#8C4FFF`) · ECS 서비스(AWS 컴퓨트 주황 `#ED7100`) · Aurora MySQL(MySQL 블루 `#4479A1`) · ElastiCache Valkey(캐시 레드 `#DC382D`) · MSK Kafka(Kafka 블랙 `#231F20`) · MongoDB Atlas(MongoDB 브랜드 그린 `#00ED64`). MongoDB Atlas는 챗봇의 **RAG 지식베이스**로 쓰며 **Vector Search**로 임베딩을 검색합니다.

## 2. Network Topology

VPC 4-tier 서브넷. 핵심 환경 차이는 **NAT 개수**(prod=3, dev·beta=1)와 VPC CIDR입니다.
private-data는 인터넷 라우트가 없는 격리 tier입니다.

### 2-1. prod (VPC 10.30.0.0/16, NAT ×3)

```mermaid
flowchart TB
    igw["Internet Gateway"]

    subgraph vpc["VPC 10.30.0.0/16 · ap-northeast-2"]
        subgraph pub["public /24 (a/b/c)"]
            alb["ALB<br/>HTTPS :443 (+80 redirect)"]
            nat_a["NAT-a"]
            nat_b["NAT-b"]
            nat_c["NAT-c"]
        end
        subgraph app["private-app /20 (a/b/c)"]
            ecs["ECS Fargate task ENI"]
            vpce["Interface VPC Endpoints x9<br/>ecr.api/dkr, logs, kms, sts,<br/>secretsmanager, ssm, ssmmessages, ec2messages"]
        end
        subgraph dat["private-data /24 (a/b/c) · isolated"]
            aurora[("Aurora :3306")]
            valkey[("Valkey :6379")]
            msk["MSK :9098/:9094"]
            mep["MongoDB Atlas endpoint"]
        end
        subgraph tgw["private-tgw /26 · reserved"]
            res["(future Transit Gateway)"]
        end
        gwe["Gateway VPC Endpoints<br/>S3 · DynamoDB"]
    end

    flow["VPC Flow Logs → CloudWatch Logs"]

    igw -->|0.0.0.0/0| pub
    app -->|0.0.0.0/0| nat_a
    nat_a --> igw
    alb -->|":443 → :8081-8086"| ecs
    ecs -->|":3306"| aurora
    ecs -->|":6379"| valkey
    ecs -->|":9098 / :9094"| msk
    ecs --> mep
    app -.->|443| vpce
    app -.-> gwe
    dat -.-> gwe
    vpc --> flow
```

### 2-2. dev / beta (NAT ×1 single)

dev = `10.10.0.0/16`, beta = `10.20.0.0/16`. 둘 다 NAT 1개를 모든 private-app 서브넷이 공유합니다.
**dev에는 MSK가 없습니다**(beta는 MSK Serverless).

```mermaid
flowchart TB
    igw["Internet Gateway"]

    subgraph vpc["VPC 10.10.0.0/16 (dev) · 10.20.0.0/16 (beta)"]
        subgraph pub["public /24 (a/b/c)"]
            alb["ALB"]
            nat["NAT (single, shared)"]
        end
        subgraph app["private-app /20 (a/b/c)"]
            ecs["ECS Fargate task ENI"]
            vpce["Interface VPC Endpoints x9"]
        end
        subgraph dat["private-data /24 (a/b/c) · isolated"]
            aurora[("Aurora :3306")]
            valkey[("Valkey :6379")]
            msk["MSK :9098<br/>(beta=Serverless, dev=없음)"]
            mep["MongoDB Atlas endpoint"]
        end
        subgraph tgw["private-tgw /26 · reserved"]
            res["(future TGW)"]
        end
        gwe["Gateway VPCE: S3 · DynamoDB"]
    end

    igw -->|0.0.0.0/0| pub
    app -->|0.0.0.0/0| nat
    nat --> igw
    alb -->|":80 → :8081-8086"| ecs
    ecs -->|":3306"| aurora
    ecs -->|":6379"| valkey
    ecs -->|"event"| msk
    ecs --> mep
    app -.->|443| vpce
    app -.-> gwe
    dat -.-> gwe
```

## 3. Security

신뢰 경계: CI/CD(GitHub OIDC → 역할 4종)와 Runtime(서비스별 task role 6종 → 시크릿/데이터),
중앙에 KMS(부트스트랩 2 + env별 5). 환경 간 보안 구조는 동일하고, 키 5종·역할 6종은 dev/beta/prod 공통입니다.

```mermaid
flowchart LR
    gha["GitHub Actions"]

    subgraph cicd["CI/CD trust boundary"]
        oidc["IAM OIDC provider<br/>token.actions.githubusercontent.com"]
        rdep["gha-deploy-{env}<br/>sub: environment:{env}"]
        rapply["gha-terraform-apply-{env}<br/>sub: tf-{env}"]
        rro["gha-terraform-readonly<br/>sub: pull_request"]
        rscan["gha-security-scan<br/>sub: refs/heads/main"]
    end

    subgraph runtime["Runtime trust boundary"]
        exec["ECS Task Execution Role<br/>(shared)"]
        t_gw["api-gateway → SSM read"]
        t_auth["api-auth → jwt + rds-db:connect"]
        t_chat["api-chatbot → openai + mongodb-uri"]
        t_agent["api-agent → kafka-cluster:* + mongodb-uri"]
        t_book["api-bookmark → rds-db:connect + cache token"]
        t_et["api-emerging-tech → openai"]
    end

    subgraph kms["KMS keys"]
        k_state["tfstate (bootstrap)"]
        k_ecr["ecr (bootstrap)"]
        k_data["{env}-data"]
        k_s3["{env}-s3-app"]
        k_auth["{env}-auth"]
        k_ai["{env}-ai"]
        k_logs["{env}-logs"]
    end

    subgraph sm["Secrets Manager"]
        s_jwt["jwt-signing-key"]
        s_oai["openai-api-key"]
        s_mongo["mongodb-uri"]
        s_cache["elasticache-auth-token"]
    end

    gha --> oidc --> rdep & rapply & rro & rscan
    rdep -->|ECR push / ECS / CodeDeploy| exec

    t_auth -->|reads| s_jwt
    t_chat -->|reads| s_oai & s_mongo
    t_agent -->|reads| s_mongo
    t_book -->|reads| s_cache
    t_et -->|reads| s_oai

    k_auth -.encrypts.-> s_jwt
    k_ai -.encrypts.-> s_oai
    k_data -.encrypts.-> s_mongo & s_cache
    k_data -.encrypts.-> runtime
    k_ecr -.encrypts.-> rscan
    k_state -.encrypts.-> rapply

    %% 경계 통제: private-data 인터넷 격리, ECR IMMUTABLE+scan, S3 Object Lock GOVERNANCE, VPC Flow Logs
    %% 전송 구간: (prod) client→ALB HTTPS:443(ACM, TLS 종료), ALB→Fargate HTTP:80(백엔드), Aurora IAM DB auth, Valkey TLS+token, MSK TLS+IAM SASL
    %% dev/beta 는 client→ALB 가 HTTP:80. dev 에서는 MSK가 없어 api-agent 의 kafka-cluster 권한이 미사용.
```

## 4. 환경 차이 요약

| 항목 | dev | beta | prod |
|---|---|---|---|
| ALB 프로토콜 | HTTP 80 | HTTP 80 | HTTPS 443 (+80→443 리다이렉트) |
| VPC CIDR | 10.10.0.0/16 | 10.20.0.0/16 | 10.30.0.0/16 |
| NAT Gateway | 1 (shared) | 1 (shared) | 3 (per AZ) |
| Aurora | serverless v2 0.5–2.0 ACU | serverless v2 0.5–4.0 ACU | provisioned 3×db.r7g.large |
| ElastiCache Valkey | t4g.micro, 0 replica, single-AZ | t4g.small, 1 replica, Multi-AZ | t4g.small, 1 replica, Multi-AZ |
| MSK | 없음 | Serverless | Provisioned 3×kafka.m7g.large |
| ECS desired/min/max | 1 / 1 / 3 | 1 / 1 / 4 | 2 / 2 / 6 |
| Aurora backup / 삭제보호 | 1d / off | 7d / off | 30d / on |
| KMS 키 | env 5 (+bootstrap 2) | env 5 | env 5 |
| Amplify / CloudFront | 미배포 | 미배포 | 미배포 |

> 주의: ECS autoscaling min/max는 api-gateway·api-auth에만 tfvars 값이 전달되고, 나머지 4개 서비스는 모듈 default(min 2 / max 10)를 씁니다(facts §1).
