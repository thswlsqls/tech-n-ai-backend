# 07. 환경별 PHZ와 Cloud Map — 같은 도메인을 환경별로 분리하는 법

> 1차 소스: [`02-network-vpc.md` §1.5](../../../../../devops/results/02-network-vpc.md)

## 한줄 요약(Hook)

> 사내망 시절 우리는 같은 도메인이 내·외부에서 다르게 해석되도록 BIND view를 분기했다. AWS는 같은 일을 **Zone 자체를 분리**해서 한다 — 그래서 환경 간 네임 충돌이 우연으로라도 생길 길이 없다.

## 핵심 질문

- 같은 서비스 이름이 환경별로 다른 IP를 가리키도록 하려면 무엇이 분리되어야 하는가?
- Route 53 Private Hosted Zone(PHZ)을 환경별로 따로 두고 VPC를 환경별로만 Associate하는 결정이 보호하는 사고는 무엇인가?
- Cloud Map은 그저 "DNS 자동 등록기"인가, 아니면 ECS 서비스 라이프사이클과 다른 무엇을 묶는가?

## 다루는 관점

- ✅ 설계 선택의 근거(Why) — 환경 간 네임 충돌·크로스 리졸브 차단
- ✅ 기본기 — DNS Private Zone, SRV vs A 레코드, 서비스 디스커버리
- ✅ 온프레비교 — 사내 BIND/AD DNS, Consul/Eureka와의 매핑

## 02 문서 근거

- §1.5.1 Public Zone(`techn-ai.example.com`) + 환경별 PHZ(`<env>.internal.techn-ai`)
- §1.5.1 환경별 VPC만 Associate → 환경 간 네임 충돌 방지
- §1.5.2 AWS Cloud Map Namespace 등록, 네이밍 규칙(`api-gateway.prod.internal.techn-ai`)
- §1.5.2 SRV vs A 레코드, Spring Boot `spring.cloud.discovery` 연동
- §1.5.3 Route 53 Resolver Endpoints — 향후 on-prem 연동 대비

## 타깃 독자 & 난이도

- 주니어~중급 백엔드/DevOps
- ★★★☆☆ (사전지식: DNS A/SRV 레코드, VPC 개념)

## 예상 분량

- 보통 (~3,000자)

## 글 아웃라인

1. **들어가며 — "왜 환경별로 도메인 인스턴스가 별도여야 하나"**
   - "오타 한 번에 dev가 prod 도메인을 가리키는 사고"의 비현실적이지 않은 가능성
2. **Public Zone vs Private Hosted Zone — 두 종류의 Zone 분리**
   - Public: `techn-ai.example.com` → ALB ALIAS
   - Private: `<env>.internal.techn-ai` → 환경별 워크로드만 보임
3. **환경별 VPC Association — 네임 충돌이 '구조적으로' 불가능한 이유**
   - PHZ를 환경별로 따로 만들고 해당 환경 VPC에만 Associate
   - 정책 변경 비용 0 — 새 환경 추가 시 새 PHZ 발급 + 새 VPC만 Associate
4. **Cloud Map — ECS 서비스와 DNS의 라이프사이클 결합**
   - Task가 뜨면 자동 등록, 죽으면 자동 해제
   - 02 §1.5.2 네이밍 규칙: `<service>.<env>.internal.techn-ai`
5. **A 레코드 + 고정 포트 vs SRV 레코드의 선택**
   - Spring Boot `spring.cloud.discovery` 연동 시 어느 쪽이 적절한가
6. **on-prem 연동 대비 — Resolver Endpoint를 지금은 비활성으로 두는 이유**
7. **결론 — DNS 분리는 토폴로지 분리의 마지막 한 겹**

## 온프레 대비 포인트

- 사내 BIND/AD DNS는 통상 Split-horizon view로 같은 도메인의 내·외부 응답을 분기시켰다. AWS는 같은 일을 **Zone 자체를 분리**해서 해결한다.
- 운영 차이:
  - **온프레**: 한 Zone 인스턴스가 view 분기 → 정책 변경 시 Zone 파일 단일 진실 공급원
  - **AWS**: 환경별 Zone 인스턴스 → 정책 변경 비용은 작지만, 두 Zone을 실수로 같이 편집할 위험은 IaC가 막아야 함
- Consul/Eureka와 비교한 Cloud Map의 특징: ECS 서비스 라이프사이클과 결합되어 별도 헬스체크 프로토콜이 없음. 그 대신 ECS 외 워크로드(Lambda, EC2)에는 별도 등록 로직이 필요.

## 시리즈 인용 관계

이 단편은 다음 시리즈에서 인용·확장된다:
- **[S1 4편 — Split-horizon DNS: 사내 BIND를 Route 53 Public + PHZ로](./series-01-onprem-to-vpc.md)** — 본 글의 결론을 온프레 BIND/Consul 운영 모델과 1:1 매핑하고, 매핑이 깨지는 지점을 다룬다.

## 참고할 1차 출처

- Route 53 Private Hosted Zones: https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/hosted-zones-private.html
- AWS Cloud Map: https://docs.aws.amazon.com/cloud-map/latest/dg/what-is-cloud-map.html

## 작성 메모

- 02 §1.5.2의 네이밍 규칙을 본문 표로 옮기면 즉시 이해된다 (`api-gateway.prod...`, `api-auth.prod...` 등).
- "Cloud Map = ECS 라이프사이클 통합 DNS"라는 한 줄로 Consul과의 차이를 박는 것이 효과적.
