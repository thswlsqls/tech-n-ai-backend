# 05. 외부는 HTTPS, 내부는 평문 HTTP — ALB 뒤에서 암호화 전략이 갈리는 지점

> 1차 소스: [`devops/aws/{dev,beta,prod}/security.png`](../../../../../../../devops/aws/prod/security.png) · [`architecture-facts.md` §1 ALB+라우팅·§2 Aurora·ElastiCache·MSK](../../../../../../../devops/aws/architecture-facts.md)

## 한줄 요약(Hook)

> prod 다이어그램 오른쪽 노란 메모에는 "In-transit: Client → ALB HTTPS :443 ... ALB → Fargate HTTP :80(backend leg)"라고 적혀 있다. 클라이언트와 ALB 사이는 TLS로 감싸는데, 바로 그 뒤 ALB와 Fargate 사이는 평문 HTTP로 흐른다. 그런데 같은 메모의 다음 줄, Fargate에서 Aurora·Valkey·MSK로 가는 구간은 전부 IAM 인증이나 TLS로 다시 암호화된다. 왜 딱 그 한 구간만 평문인가.

## 핵심 질문

- ALB의 리스너(클라이언트 구간)와 타깃 그룹(백엔드 구간)은 왜 서로 다른 프로토콜을 쓸 수 있으며, 이 설계에서는 왜 백엔드 구간이 모든 환경에서 항상 HTTP인가?
- Fargate에서 Aurora(IAM DB 인증)·Valkey(TLS+auth token)·MSK(TLS+IAM SASL)로 가는 세 구간은 각각 어떤 방식으로 암호화·인증되는가?
- ALB→Fargate 구간이 평문이어도 되는 이유를, VPC 네트워크 경계(Private 서브넷, Security Group)가 어떻게 보완하는가?

## 다루는 관점

- ✅ 설계 근거(Why) — 모든 구간을 TLS로 감싸지 않고 구간별로 다른 전략을 쓰는 이유
- ✅ 구현 — ALB 타깃 그룹 프로토콜, Aurora·Valkey·MSK 각각의 암호화·인증 방식
- ✅ 운영 — 구간별 위협 모델을 나눠서 생각하는 법

## 근거

- 다이어그램: prod·dev·beta `security.png`의 "In-transit" 노란 메모 — "Client → ALB HTTPS :443(ACM cert, TLS 종단). ALB → Fargate HTTP :80(backend leg). Fargate → Aurora IAM DB auth(rds-db:connect). → Valkey TLS + auth token. → MSK TLS + IAM SASL(:9098/:9094)"(dev·beta는 Client→ALB 구간만 HTTP :80으로 다르게 표기)
- `architecture-facts.md` §1 ALB+라우팅(44행) — "ALB→Fargate 백엔드 레그는 모든 env에서 HTTP"
- `architecture-facts.md` §2 Aurora MySQL(69행) — IAM DB auth 모듈 default true
- `architecture-facts.md` §2 ElastiCache Valkey(86행) — `transit_encryption_enabled` default true(TLS), `auth_mode=auth_token`(모든 env 명시 전달)
- `architecture-facts.md` §2 MSK(102~103행) — MSK Serverless는 IAM SASL 전용(포트 9098) / MSK Provisioned는 IAM SASL(9098)+TLS(9094) 둘 다, in-transit `client_broker=TLS`
- `architecture-facts.md` §5 Security Group 표(204행) — Workload SG는 ALB SG로부터 container_port(8081~8086)만 인바운드 허용, 즉 ALB→Fargate 구간이 Private 서브넷 안, SG로 출발지가 제한된 경로로만 흐른다는 네트워크 경계

## 타깃 독자 & 난이도

- ALB 뒤 백엔드 구간이 평문이어도 되는지 판단해야 하는 백엔드·보안 엔지니어, "종단간 TLS"를 당연하게 요구하기 전에 위협 모델부터 따져보고 싶은 독자
- ★★★☆☆ (사전지식: ALB 리스너·타깃 그룹 구조, TLS 종단의 기본 개념)

## 예상 분량

- 보통 (~3,200자)

## 글 아웃라인

1. **들어가며 — 메모 한 줄 안에서 암호화가 켜졌다 꺼졌다 하는 지점**
   - "HTTPS :443"과 "HTTP :80(backend leg)"이 같은 문장 안에 나란히 있다는 관찰
2. **ALB→Fargate 백엔드 레그가 평문인 이유**
   - 타깃 그룹 프로토콜 설정과, 이 구간이 Private-app 서브넷 안에서만 흐르며 SG로 출발지가 ALB SG로 제한된다는 네트워크 경계
3. **Fargate→데이터 계층, 세 가지 다른 암호화 방식**
   - Aurora의 IAM DB 인증(`rds-db:connect`), Valkey의 TLS+auth token, MSK의 TLS+IAM SASL을 나란히 비교
4. **"종단간 암호화"가 아니라 "구간별 위협 모델"이라는 관점**
   - 어느 구간을 누가 관제·접근할 수 있는지에 따라 요구되는 암호화 수준이 달라진다는 것 — VPC 내부 구간과 인터넷을 거치는 구간의 차이
5. **결론 — 모든 구간을 TLS로 감싸는 것이 항상 정답은 아니다**
   - 트레이드오프(운영 복잡도 vs 방어 심도)를 중심으로 정리하고, 이 설계가 어떤 전제(Private 서브넷 격리) 위에서만 성립하는지 짚기

## 참고할 1차 출처

- Application Load Balancer 타깃 그룹: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-target-groups.html
- Amazon MSK IAM 액세스 제어: https://docs.aws.amazon.com/msk/latest/developerguide/iam-access-control.html
- Aurora MySQL의 IAM 데이터베이스 인증(`rds-db:connect`): https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/UsingWithRDS.IAMDBAuth.IAMPolicy.html
- Amazon ElastiCache 전송 중 암호화(TLS): https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/in-transit-encryption.html

## 시리즈 인용 관계

**[04 — KMS 5+2 키 구조](./04-kms-five-plus-two-keys.md)**가 다룬 저장 데이터 암호화(KMS)와 짝을 이루는, 전송 구간 암호화 단편이다. 04의 KMS 키 구조는 반복하지 않고 "흐르는 동안" 암호화가 갈리는 지점에만 집중한다. `../Network Topology/02-alb-https-toggle.md`가 이미 다룬 클라이언트→ALB 구간의 dev/beta/prod HTTP·HTTPS 토글은 배경으로만 짧게 인용하고 근거를 재도출하지 않는다.

## 작성 메모

- "백엔드 레그가 평문이니 취약하다"는 단정으로 흐르지 않는다. Private 서브넷 격리와 SG 출발지 제한이라는 전제를 항상 함께 제시해, 이 설계가 성립하는 조건을 분명히 한다.
- `../Network Topology/02-alb-https-toggle.md`가 이미 상세히 다룬 "Client→ALB가 prod에서만 HTTPS인 이유"는 이 단편에서 다시 설명하지 않는다. 이 단편은 그 뒤(ALB→Fargate, Fargate→데이터 계층) 구간에만 집중한다.
