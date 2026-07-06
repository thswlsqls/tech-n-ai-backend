# 02. HTTP 80과 HTTPS 443 사이 — `var.alb_certificate_arn` 하나로 갈라지는 환경별 ALB

> 1차 소스: [`devops/aws/{dev,beta,prod}/network-topology.png`](../../../../../aws/prod/network-topology.png) · [`architecture-facts.md` §1 ALB + 라우팅](../../../../../aws/architecture-facts.md) · [`devops/terraform/envs/prod/cluster.tf`](../../../../../terraform/envs/prod/cluster.tf)

## 한줄 요약(Hook)

> dev·beta 다이어그램의 ALB 옆에는 ":80 HTTP"만 적혀 있고, prod에만 ":443 HTTPS (:80→:443 redirect)"가 붙는다. 이 차이를 만드는 코드는 `if/else` 두 갈래가 아니라 변수 하나가 비어 있는지 확인하는 한 줄, `alb_https_enabled = var.alb_certificate_arn != ""`이다.

## 핵심 질문

- ALB 리스너를 HTTP/HTTPS 두 가지로 만드는 대신, "인증서 ARN이 있으면 HTTPS를 켠다"는 토글로 설계한 이유는 무엇인가?
- 이 토글이 dev·beta에는 아직 켜지지 않은 이유는 인증서가 없어서인가, 아니면 의도적으로 미루는 것인가?
- HTTP 리스너가 사라지지 않고 "443으로 301 리다이렉트"라는 역할로 남는 이유는 무엇인가?

## 다루는 관점

- ✅ 설계 근거(Why) — 환경별로 TLS 종단을 다르게 가져가는 위협 모델의 경제학
- ✅ 구현(Terraform 코드) — 조건부 리소스 생성과 `local.alb_https_enabled` 패턴
- ✅ 운영 — 인증서 발급 전/후 환경을 코드 변경 없이 전환하는 법

## 근거

- 다이어그램 사실: dev/beta network-topology.png "ALB :80 HTTP" 단독 라벨 / prod network-topology.png "ALB :443 HTTPS (:80→:443 redirect)" 라벨
- `architecture-facts.md` §1 ALB + 라우팅(42~44행) — `var.alb_certificate_arn` 토글 로직, prod tfvars만 ACM ARN 지정, dev/beta는 빈 값
- `architecture-facts.md` §7 환경 차이 매트릭스(238행) — "ALB 프로토콜: HTTP 80 / HTTP 80 / HTTPS 443(+리다이렉트)"
- `devops/terraform/envs/prod/cluster.tf` 3행 주석 — "HTTPS 전환은 `var.alb_certificate_arn`으로 토글", 9행 `local.alb_https_enabled`, 134행 `certificate_arn = var.alb_certificate_arn`
- `devops/results/03-compute-and-frontend-hosting.md` 299~305행 — Listener 443 HTTPS, TLS 정책 `ELBSecurityPolicy-TLS13-1-2-2021-06`, HTTP 80은 301 리다이렉트

## 타깃 독자 & 난이도

- Terraform으로 여러 환경의 ALB를 관리해야 하는 인프라/백엔드 엔지니어
- ★★☆☆☆ (사전지식: ALB 리스너, ACM 인증서의 기본 개념)

## 예상 분량

- 보통 (~3,200자)

## 글 아웃라인

1. **들어가며 — 다이어그램 세 장에서 딱 한 줄만 다른 곳**
   - ALB 라벨의 프로토콜 표기 차이를 시작점으로 삼기
2. **HTTPS를 "만들지 말지"가 아니라 "언제 켤지"의 문제로 설계하기**
   - `var.alb_certificate_arn`이 빈 문자열이면 HTTP 단독, 채워지면 HTTPS + 리다이렉트로 전환되는 조건부 로직
   - 이 방식이 dev/beta 리스너 정의를 별도로 관리하지 않고 같은 모듈로 세 환경을 커버하게 하는 이유
3. **HTTP 리스너는 사라지지 않는다 — 301 리다이렉트라는 두 번째 역할**
   - prod에서 80 리스너의 default action이 fixed-response(404)에서 리다이렉트로 바뀌는 지점
   - AWS 공식 문서 기준 HTTP→HTTPS 리다이렉트 리스너 규칙의 표준 패턴과 대조
4. **왜 dev·beta는 아직 HTTP인가 — 인증서가 없어서 못 켠 것과 아직 안 켠 것의 차이**
   - 도메인 검증(DNS validation)이 필요한 ACM 발급 절차와, 검증용 환경에 공인 인증서를 발급할 유인이 낮다는 운영 판단
   - "안 켰다"는 결정이 코드 한 줄(tfvars 값)만 바꾸면 되돌릴 수 있는 가역적 결정이라는 점
5. **TLS 정책까지 변수화하기 — `ELBSecurityPolicy-TLS13-1-2-2021-06`**
   - `var.alb_ssl_policy` 기본값이 왜 TLS 1.3 우선 정책인지, AWS가 제공하는 사전 정의 보안 정책과의 관계
6. **결론 — 보안 강도를 환경 변수로 관리한다는 것의 의미**
   - "prod만 HTTPS"가 느슨한 보안이 아니라, 위협 노출면이 다른 환경에 다른 강도를 적용하는 명시적 설계라는 정리

## 참고할 1차 출처

- Application Load Balancer HTTPS 리스너: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/create-https-listener.html
- ALB SSL 보안 정책(ELBSecurityPolicy): https://docs.aws.amazon.com/elasticloadbalancing/latest/application/describe-ssl-policies.html
- ALB 리스너 개요(HTTP→HTTPS 리다이렉트 액션 포함): https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html
- AWS Certificate Manager — 공인 인증서 요청: https://docs.aws.amazon.com/acm/latest/userguide/gs-acm-request-public.html

## 시리즈 인용 관계

이 단편은 **[series-01 — 다이어그램 세 장을 나란히 읽기](./series-01-three-diagrams-maturity-signal.md)** 의 "TLS 종단 commitment 단계" 신호로 인용된다. 시리즈 글은 본 단편의 토글 구현을 반복 설명하지 않고, 이 선택이 MSK·NAT 선택과 같은 방향(dev < beta < prod)으로 움직인다는 사실만 더한다.

## 작성 메모

- "dev/beta는 보안이 약하다"는 단정 대신, ALB→Fargate 백엔드 레그는 모든 환경에서 HTTP라는 사실(`architecture-facts.md` §8)까지 함께 언급해 TLS 종단 지점의 의미를 정확히 짚는다.
- Terraform 조건부 리소스 생성(`count`/`local` 불리언 토글) 패턴은 이 시리즈의 다른 글(01의 MSK 토글)과 같은 형태이므로, 두 글을 같이 읽는 독자를 위해 "MSK 토글과 같은 패턴"이라는 연결 문장 하나 정도는 넣어도 좋다(반복 설명은 금지).
