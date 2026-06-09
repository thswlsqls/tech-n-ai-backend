# 02. /16에서 /20으로 4,096개 IP를 잡은 이유 — Fargate ENI 한 개의 무게

> 1차 소스: [`02-network-vpc.md` §1.1, §1.2.2](../../../../../devops/results/02-network-vpc.md)

## 한줄 요약(Hook)

> ECS Fargate는 Task당 ENI 1개를 쓴다. 이 한 줄이 Private-App 서브넷 사이즈를 /24가 아니라 /20으로 만든 이유의 전부다.

## 핵심 질문

- 마이크로서비스 6개 + 배치 1개 + Blue/Green 이중 실행이라는 워크로드 패턴은 서브넷 IP를 어떻게 소비하는가?
- /24(251 IP)와 /20(4,091 IP)의 차이는 어떤 운영 사고를 막아주는가?
- 환경별 VPC를 `10.10/10.20/10.30` 으로 **10 단위 이격**한 결정이 향후 TGW·Peering에 어떤 안전마진을 주는가?

## 다루는 관점

- ✅ 설계 선택의 근거(Why)
- ✅ 데브옵스 기본기 (CIDR 산술, RFC 1918, AWS 예약 5개 IP)
- △ 온프레비교 — 짧게 (사내망 IP 계획 vs 클라우드 IP 계획의 차이)

## 02 문서 근거

- §1.1.1 계정/환경별 VPC CIDR 할당표 — RFC 1918 준수, 10 단위 이격의 산술 근거
- §1.1.2 서브넷 CIDR 분할 표(prod 기준) — `cidrsubnet()` 결정적 분할
- §1.2.2 Private-App /20인 이유 — Fargate Task당 ENI 1개, ~12배 헤드룸
- §1.1.1 MongoDB Atlas `192.168.0.0/16` 회피 — PrivateLink 채택 근거

## 타깃 독자 & 난이도

- 주니어 백엔드/인프라 엔지니어, ECS를 처음 운영하는 SRE
- ★★★☆☆ (사전지식: 서브넷 마스크, 비트 산술 약간)

## 예상 분량

- 보통 (~3,500자)

## 글 아웃라인

1. **사고 실험 — /24 서브넷에서 Fargate Blue/Green 배포가 일으키는 IP 고갈**
   - 6 서비스 × 10 Task × 3 replica + Blue/Green 동시 실행 = 어떻게 251 IP를 넘는가
2. **CIDR 산술의 기본기 — /20은 왜 4,096이 아니라 4,091인가**
   - AWS 예약 IP 5개(.0, .1, .2, .3, .255 broadcast)
   - `cidrsubnet(base, newbits, netnum)`의 동작과 02 문서의 매핑
3. **서브넷 4계층 분할 — 왜 Public은 /24, App은 /20, Data는 /24, TGW는 /26인가**
   - 각 계층이 소비하는 ENI 패턴이 다르기 때문
4. **환경 간 CIDR 이격의 수학적 근거 — `10.<10*N>.0.0/16` 규칙**
   - dev(`10.10`) / beta(`10.20`) / prod(`10.30`) — 두 대역의 교집합이 공집합인 이유
   - 향후 TGW 라우팅 충돌이 발생하지 않는 이유를 비트마스크로 증명
5. **외부 시스템과의 IP 충돌 회피 — MongoDB Atlas `192.168/16` 사례**
   - Atlas Peering 시 충돌 리스크와 PrivateLink 선택의 본질
6. **확장 시뮬레이션 — 12배 헤드룸이 의미하는 운영 안정감**
7. **체크리스트 — 새 VPC를 만들 때 던져야 할 5가지 산술 질문**

## 온프레 대비 포인트

- 사내 데이터센터에서는 코어 스위치/라우터의 인터페이스 수, VLAN 수, DHCP 풀 크기로 IP 계획이 결정된다.
- 클라우드는 **워크로드 단위(Task, Pod)가 ENI를 직접 소비**하기 때문에 IP 소비 패턴이 훨씬 동적이다.
- 따라서 "충분히 크게 잡고 잘 나눠 쓰는" 클라우드의 사상이 온프레의 "딱 맞게 자르는" 사상과 갈린다.

## 참고할 1차 출처

- ECS Fargate Task Networking: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-networking.html
- RFC 1918: https://datatracker.ietf.org/doc/html/rfc1918
- AWS VPC Subnet 계획: https://docs.aws.amazon.com/vpc/latest/userguide/

## 시리즈 인용 관계

이 단편은 다음 시리즈에서 인용·확장된다:
- **[S2 1편 — CIDR 회계학: 한 줄의 산술이 만든 다섯 개의 후속 결정](./series-02-vpc-design-diary.md)** — 본 글의 "왜 /20인가"라는 단일 결정을 **TGW 충돌 회피 / Blue-Green 자유도 / Atlas PrivateLink 채택 / IPv6 가역성 / SG 매트릭스 단순화** 5가지 후속 결정으로 잇는다.

## 작성 메모

- "왜 /20인가"를 끝까지 **숫자**로 답하는 글이 되도록. 추정치 대신 02 문서의 산술 검증을 그대로 인용.
- Mermaid로 4계층 서브넷 사이즈 비교 다이어그램 권장.
