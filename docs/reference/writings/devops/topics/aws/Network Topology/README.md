# 기술 블로그 주제 인덱스 — network-topology 다이어그램으로 읽는 환경별 설계

> 1차 소스: [`devops/aws/{dev,beta,prod}/network-topology.drawio`·`.png`](../../../../aws/README.md)
> 보조 컨텍스트: [`architecture-facts.md`](../../../../aws/architecture-facts.md)(Terraform 기준 line-cited 사실 모음) · [`devops/results/05-messaging-kafka.md`](../../../../results/05-messaging-kafka.md) · [`devops/results/03-compute-and-frontend-hosting.md`](../../../../results/03-compute-and-frontend-hosting.md) · [`devops/terraform/envs/prod/cluster.tf`](../../../../terraform/envs/prod/cluster.tf)
>
> 본 문서는 `devops/aws/dev·beta·prod/network-topology.png`(와 대응 `.drawio`)를 1차 소스로 도출한 블로그 주제 후보 모음이다. 02-network-vpc.md를 1차 소스로 삼은 [`../prototype/`](../prototype/README.md) 시리즈가 이미 CIDR·NAT·VPC Endpoint·SG/NACL·IPv6·Route53을 다뤘으므로, 본 시리즈는 **그 시리즈가 다루지 않은, 다이어그램에서만 직접 확인되는 사실**(MSK 배치 형태, ALB TLS 종단, ALB의 path-based 직접 라우팅)에 집중한다.
>
> 단편 03은 기획 단계에서 "설계 문서와 실제 리스너 규칙이 어긋나 보인다"는 확인 필요 지점으로 시작했으나, `write-tech-blog` 작업 직전 코드 재검증 과정에서 실제 인증 우회 취약점(api-agent가 게이트웨이 없이 직접 호출되면 클라이언트가 보낸 `x-user-id` 헤더를 그대로 신뢰하던 문제)으로 확인돼 즉시 수정했다. 아래 목록은 수정이 끝난 뒤의 최신 상태를 반영한다.

## 단편과 시리즈의 관계 — "빌딩블록 × 메타 글" 모델

`../prototype/`와 같은 모델을 따른다.

- **단편**(01~03)은 다이어그램에서 확인되는 사실을 **결정 단위**로 슬라이스한다. 한 편에 한 결정. 독립 출판이 가능하다.
- **시리즈**(series-01)는 그중 환경별로 달라지는 두 단편(01, 02)을 하나의 서사로 묶어, 단편 각각으로는 보이지 않는 "정렬"을 보여준다. 단편이 답한 "왜"는 반복하지 않는다.
- 단편 03(ALB path-based 라우팅)은 세 환경에 공통된 사실을 다루므로 시리즈에는 인용되지 않는 **독립 자산**이다.

### 단편 ↔ 시리즈 인용 그래프

| 단편 | series-01(성숙도 정렬) |
|---|---|
| 01 MSK 3단계 배치 | **본진** (관리형 서비스 commitment 축) |
| 02 ALB HTTPS 토글 | **본진** (TLS 종단 축) |
| 03 ALB path-based 라우팅과 인증 우회 발견·수정 | — (시리즈 외 독립 자산) |

## 1. 단편 글 후보 (빌딩블록)

| # | 제목 | Why | 구현 | 보안/운영 | 근거 | 분량 |
|---|---|:-:|:-:|:-:|---|---|
| [01](./01-msk-staged-rollout.md) | 없음 → Serverless → Provisioned — 다이어그램 세 장에 그려진 MSK 도입의 3단계 | ✅ | ✅ | ✅ | 다이어그램 + `architecture-facts.md` §2·§7·§8 + `05-messaging-kafka.md` §1.1 | 보통 |
| [02](./02-alb-https-toggle.md) | HTTP 80과 HTTPS 443 사이 — `var.alb_certificate_arn` 하나로 갈라지는 환경별 ALB | ✅ | ✅ | ✅ | 다이어그램 + `architecture-facts.md` §1·§7·§8 + `cluster.tf` + `03-compute-and-frontend-hosting.md` | 보통 |
| [03](./03-alb-path-based-routing-vs-gateway.md) | "path-based → :8081-8086" — 다이어그램 라벨 한 줄을 파고들다 찾아낸 인증 우회, 그리고 고친 방법 | ✅ | ✅ | ✅(보안) | 다이어그램 + `architecture-facts.md` §1(서비스 목록·ALB 라우팅) + 저장소 코드(`common/security`·`api/agent`·`api/gateway`) | 김 |

## 2. 시리즈 후보 (메타 글)

| # | 제목 | 편수 | 시리즈가 단편 위에 더하는 것 |
|---|---|---|---|
| [series-01](./series-01-three-diagrams-maturity-signal.md) | 세 장의 다이어그램을 나란히 놓고 읽기 — dev·beta·prod가 함께 움직이는 방향 | 1편(단일 메타 글) | NAT·MSK·TLS 세 축이 같은 방향(dev < beta < prod)으로 정렬돼 있다는 사실 + 이를 설명하는 AWS 다중 환경 전략 공식 가이드 |

## 3. 폐기·병합 로그(투명성)

- ❌ **"NAT Gateway 환경별 회계"** — `../prototype/03-nat-gateway-az-tradeoff.md`가 이미 02-network-vpc.md 기준으로 상세히 다룸. 본 시리즈에서는 series-01이 "같은 방향으로 정렬된 세 축 중 하나"로만 인용하고 근거를 재도출하지 않는다.
- ❌ **"Security Group 3단 체인(ALB SG → Workload SG → Data SG)"** — `../prototype/05-sg-vs-nacl-defense-in-depth.md`가 SG ID 참조 원칙과 심층 방어를 이미 다룸. 다이어그램의 SG 체인 라벨은 그 글의 사실과 동일해 새 관점을 못 만든다.
- ❌ **"VPC Flow Logs → CloudWatch 파이프라인(세 환경 동일)"** — `../prototype/README.md` 폐기 로그에서 이미 "단독 글로는 얕음"으로 판정하고 흡수 처리됨. 본 다이어그램에서도 세 환경 표기가 동일해 새로 다룰 내용이 없다.
- ❌ **"CIDR·서브넷 4계층 구조"** — `../prototype/02-cidr-and-fargate-eni.md`가 CIDR 산술과 4계층 서브넷 분할을 이미 다룸. 다이어그램은 그 결론을 시각화한 것일 뿐 새 근거가 없다.
- 🔁 **"draw.io + AWS4 아이콘으로 다이어그램을 코드처럼 관리하기(PNG vs SVG 렌더링)"** — `devops/aws/README.md`에 담긴 흥미로운 구현 디테일이지만, 근거가 AWS 공식 문서가 아니라 GitHub의 SVG 렌더링 정책이라 본 시리즈("aws-docs MCP 기반 공식 출처")의 범위를 벗어남. 별도 devops-tooling 카테고리 후보로 남기고 본 시리즈에서는 폐기.
- 🔁 **"CloudFront/Amplify 프런트 오리진 구성"** — `architecture-facts.md` §9에 따르면 `cloudfront-spa` 모듈이 어느 env에서도 호출되지 않아 현재 network-topology 다이어그램(백엔드 인프라 대상)에 나타나지 않는 리소스다. 프런트엔드 카테고리에서 별도로 다룰 후보로 남기고 본 시리즈에서는 폐기.

## 작성 가이드

- **인용 정책**: 기술적 사실의 근거는 `architecture-facts.md`의 파일:라인 출처 또는 `devops/aws/*/network-topology.png` 다이어그램 라벨, 그리고 aws-docs MCP로 확인한 공식 AWS 문서만 사용한다. 블로그·AI 생성 콘텐츠 인용 금지(`tech-n-ai-backend/CLAUDE.md` 외부 자료 참조 원칙).
- **본문 언어**: 한국어. 고유명사·기술 용어는 영문 유지(MSK, ALB, Target Group, Listener Rule 등).
- **숫자·산술**: `architecture-facts.md`에 명시된 수치만 사용한다. 추정값은 "추정:" 표기.
- **사실 검증 우선**: 03 단편처럼 설계 문서와 실제 구현 사이 간극이 발견되면, 완성 글을 쓰기 전 최신 코드(`devops/terraform/envs/prod/services.tf` 등)로 재확인한다. 다이어그램은 특정 시점의 스냅샷일 수 있다.
- **다이어그램 인용**: 각 단편 도입부에 해당 환경의 `network-topology.png` 캡처 또는 라벨 인용 박스를 둔다. series-01은 세 장을 한 번에 배치하거나 `architecture-facts.md` §7 표를 축약 재현한다.
- **단편 작성 시**: 글 마지막의 "시리즈 인용 관계" 섹션을 유지해 시리즈 작성자에게 빌딩블록 신호를 남긴다.
- **분량·SEO**: 완성 글은 `write-tech-blog`에서 7,000자 이상·SEO 제목 후보 3개+·번호 없는 소제목으로 다듬는다. 설계도의 아웃라인 번호는 기획용이다.
- **보안 취약점을 다루는 글(03)**: 발견·수정이 끝난 뒤에만 공개 대상으로 삼는다. 본문에는 문제의 구조와 고친 방법을 담되, 그대로 따라 하면 다른 시스템의 유사 취약점을 찾는 안내서가 되는 구체적 공격 재현 절차(요청 예시, 헤더 조작 명령 등)는 넣지 않는다.

## 공식 출처 (단편·시리즈 공통 보강)

- Amazon MSK — https://docs.aws.amazon.com/msk/latest/developerguide/what-is-msk.html
- Amazon MSK Serverless — https://docs.aws.amazon.com/msk/latest/developerguide/serverless.html
- Amazon MSK Provisioned — https://docs.aws.amazon.com/msk/latest/developerguide/msk-provisioned.html
- ALB HTTPS 리스너 — https://docs.aws.amazon.com/elasticloadbalancing/latest/application/create-https-listener.html
- ALB SSL 보안 정책 — https://docs.aws.amazon.com/elasticloadbalancing/latest/application/describe-ssl-policies.html
- ALB 리스너 개요 — https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html
- ALB 리스너 규칙 조건 타입 — https://docs.aws.amazon.com/elasticloadbalancing/latest/application/rule-condition-types.html
- ALB CreateRule API — https://docs.aws.amazon.com/elasticloadbalancing/latest/APIReference/API_CreateRule.html
- AWS Certificate Manager 공인 인증서 요청 — https://docs.aws.amazon.com/acm/latest/userguide/gs-acm-request-public.html
- AWS 다중 계정 전략(Control Tower) — https://docs.aws.amazon.com/controltower/latest/userguide/aws-multi-account-landing-zone.html
- AWS Organizations 모범 사례 — https://docs.aws.amazon.com/organizations/latest/userguide/orgs_best-practices.html

> 위 공식 출처 외의 블로그/AI 생성 문서는 본 시리즈의 근거로 인용하지 않는다.
