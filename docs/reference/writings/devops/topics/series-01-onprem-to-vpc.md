# 시리즈 S1. 온프레미스 네트워크 엔지니어를 위한 VPC 번역 사전 — 전 4편

> 1차 소스: [`02-network-vpc.md`](../../../../../devops/results/02-network-vpc.md) 전 절
> 본 시리즈는 **메타 글**이다. 각 편은 대응되는 단편 글을 1차 빌딩블록으로 인용하고, 그 위에 **온프레미스 운영 모델과의 1:1 매핑 + 매핑이 깨지는 지점 + 마이그레이션 시나리오**라는 시리즈 고유 가치만 더한다.

## 시리즈 훅

> 사내 데이터센터에서 ACL·방화벽·코어 스위치·DNS를 다뤄 본 사람에게 VPC는 새로운 세계가 아니라, **익숙한 도구를 다른 이름으로 부르는 동네**다. 다만 이름이 달라진 만큼 운영 모델도 달라졌다. 이 시리즈는 1:1로 번역한 뒤, 그 번역이 거짓말이 되는 순간을 짚는다.

## 시리즈가 단편 위에 더하는 것

각 편은 대응 단편이 이미 다룬 "**왜 그렇게 설계했는가**"를 반복하지 않는다. 대신 다음 세 가지만 다룬다.

1. **운영 모델 매핑 표** — 온프레의 박스/도구 ↔ AWS 컴포넌트의 1:1 대응
2. **매핑이 깨지는 지점** — 1:1로 보이지만 운영적으로 다른 1~2개의 사례
3. **마이그레이션 시나리오** — 온프레 정책/구성을 클라우드로 옮길 때의 변환 패턴 (가능한 경우)

이 세 가지는 단편 글 한 편에 끼워 넣기에는 너무 무겁고, 각 단편의 결론이 있어야 자연스럽게 따라오는 내용이다.

## 편별 구성

### 1편 — ACL과 방화벽: 온프레 정책을 NACL+SG로 다시 그리기

- **인용 빌딩블록**: [단편 05 — Stateful(SG)과 Stateless(NACL)을 함께 쓰는 이유](./05-sg-vs-nacl-defense-in-depth.md)
- **단편이 답한 것**: "왜 두 레이어를 동시에 두는가"의 설계 근거
- **시리즈가 더하는 것**:
  1. **매핑 표**: 라우터 ACL ↔ NACL, 체크포인트/팔로알토 스테이트풀 방화벽 ↔ Security Group, 호스트 방화벽(iptables) ↔ SG (역할별)
  2. **매핑이 깨지는 지점**: SG ID 참조 — 온프레에는 등가 개념이 없음. 마이크로세그멘테이션을 흉내내려면 NSX/Illumio 같은 별도 솔루션이 필요했고, AWS는 이를 SG 자체에 내장했다.
  3. **마이그레이션 시나리오**: 사내 정책 매트릭스(출발지 IP/대역 → 목적지 IP/포트)를 02 §2.1 "SG ID 참조" 매트릭스로 변환하는 4단계 워크북
- **02 근거**: §2(SG 매트릭스), §3.1(NACL 4세트)
- **편 분량**: 보통 (~3,000자)

### 2편 — 코어 스위치/NAT 라우터: 온프레 박스를 TGW+NAT GW로 분해하기

- **인용 빌딩블록**: [단편 03 — AZ별 NAT Gateway 3개 vs single_nat_gateway](./03-nat-gateway-az-tradeoff.md)
- **단편이 답한 것**: "환경별 NAT 토폴로지의 비용·가용성 회계"
- **시리즈가 더하는 것**:
  1. **매핑 표**: 코어 스위치/라우터 ↔ Transit Gateway + 라우팅 테이블, NAT 라우터 ↔ NAT Gateway, VLAN ↔ Subnet+RT+NACL 조합, HSRP/VRRP ↔ AZ 다중화
  2. **매핑이 깨지는 지점**:
     - HSRP/VRRP는 "이미 사둔 두 박스"이므로 이중화가 거의 무료지만, NAT GW는 이중화의 단가가 명시적이라 환경별 토폴로지 분기가 자연스러워진다 (단편 03의 결론).
     - TGW는 사내 코어 스위치와 달리 **attachment subnet** 개념이 있다 — Private-TGW /26 서브넷이 왜 필요했는지(02 §1.2.4)
  3. **마이그레이션 시나리오**: 사내망의 VLAN 정책을 환경별 VPC + TGW 라우팅 테이블로 옮기는 패턴
- **02 근거**: §1.3(라우팅), §1.3.2(NAT GW), §1.2.4·§4(TGW)
- **편 분량**: 보통 (~3,500자)

### 3편 — Forward Proxy/서비스 메시: PrivateLink로 다시 그리는 외부 호출

- **인용 빌딩블록**: [단편 04 — NAT를 우회하는 두 가지 길 — Gateway Endpoint와 Interface Endpoint 선택 기준](./04-vpc-endpoint-privatelink.md)
- **단편이 답한 것**: "Gateway/Interface 선택 기준 + Data Exfiltration 차단의 본질"
- **시리즈가 더하는 것**:
  1. **매핑 표**: 사내 Forward Proxy(Squid/Zscaler) ↔ Interface Endpoint + Endpoint Policy, 서비스 메시 Egress Gateway ↔ `aws:PrincipalOrgID` 조건, 사내 SaaS 직결망 ↔ MongoDB Atlas PrivateLink
  2. **매핑이 깨지는 지점**:
     - 사내 Proxy는 "허용 도메인 화이트리스트"라는 모델이지만, PrivateLink는 "허용된 AWS 서비스/SaaS만, 그것도 AWS 백본 안에서만" — 화이트리스트의 단위가 다르다.
     - Forward Proxy 로그는 Proxy 자체가 보관하지만, PrivateLink 호출은 VPC Flow Logs + CloudTrail이 분담한다 — 감사 모델의 분산
  3. **마이그레이션 시나리오**: 사내 Proxy 화이트리스트 → VPC Endpoint 목록 + Endpoint Policy 변환표
- **02 근거**: §1.4 전체
- **편 분량**: 김 (~4,500자)

### 4편 — Split-horizon DNS: 사내 BIND를 Route 53 Public + PHZ로 다시 그리기

- **인용 빌딩블록**: [단편 07 — 환경별 PHZ와 Cloud Map: 같은 도메인을 환경별로 분리하는 법](./07-route53-phz-and-cloudmap.md)
- **단편이 답한 것**: "환경별 PHZ 분리와 Cloud Map 서비스 디스커버리의 설계 근거"
- **시리즈가 더하는 것**:
  1. **매핑 표**: 사내 BIND/AD DNS ↔ Route 53 PHZ, Split-horizon DNS ↔ Public Zone + PHZ, 사내 서비스 디스커버리(Consul/Eureka) ↔ AWS Cloud Map
  2. **매핑이 깨지는 지점**:
     - 사내 Split-horizon은 같은 Zone 인스턴스가 view 분기로 다른 응답을 주지만, AWS는 **Zone 자체를 분리**하고 VPC Association으로 라우팅한다 — 정책 변경 비용 모델이 다르다.
     - Cloud Map은 ECS 서비스 라이프사이클과 연동되어 자동 등록/해제되지만, Consul은 별도 헬스체크 프로토콜을 가진다 — 운영 책임의 위치
  3. **마이그레이션 시나리오**: 사내 Zone 파일을 Route 53 Public/PHZ로 분리 임포트하는 절차
- **02 근거**: §1.5(Route 53 + Cloud Map)
- **편 분량**: 보통 (~3,000자)

## 읽는 순서 권장

1편 → 2편: 트래픽이 통과하는 순서(접근 통제 → 라우팅).
3편: 1·2편을 다 읽고 나야 "VPC 안에서 끝난다"는 PrivateLink의 가치가 또렷해진다.
4편: DNS는 모든 호출의 시작이지만, 토폴로지를 알지 못하면 PHZ 분리의 의미가 와닿지 않는다.

## 시리즈 작성 가이드(공통)

- 매 편 도입부에 "이 편이 인용하는 단편"을 박스로 박는다. 단편을 읽지 않아도 따라올 수 있게 1단락 요약을 같이 둔다.
- 매 편 마지막에 "**내려놓은 것 / 새로 생긴 것**" 표로 운영 부담의 이동을 시각화한다.
- 02 §4 토폴로지 다이어그램을 시리즈 전체의 시각 기준으로 재사용.

## 참고할 1차 출처(시리즈 공통)

- AWS VPC User Guide: https://docs.aws.amazon.com/vpc/latest/userguide/
- AWS Security Reference Architecture: https://docs.aws.amazon.com/prescriptive-guidance/latest/security-reference-architecture/
- AWS Transit Gateway Best Practices: https://docs.aws.amazon.com/vpc/latest/tgw/tgw-best-design-practices.html
- AWS PrivateLink: https://docs.aws.amazon.com/vpc/latest/privatelink/concepts.html
- Route 53 Private Hosted Zones: https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/hosted-zones-private.html
- AWS Cloud Map: https://docs.aws.amazon.com/cloud-map/latest/dg/what-is-cloud-map.html
