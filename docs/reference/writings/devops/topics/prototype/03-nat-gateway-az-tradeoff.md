# 03. AZ별 NAT Gateway 3개 vs single_nat_gateway — 비용과 SPOF 사이의 환경별 회계

> 1차 소스: [`02-network-vpc.md` §1.3.2](../../../../../devops/results/02-network-vpc.md)

## 한줄 요약(Hook)

> 가용성은 prod에서 비싸고, dev/beta에서는 사치다. NAT Gateway 한 줄 변수(`single_nat_gateway`)가 환경별 회계의 가장 솔직한 표현인 이유.

## 핵심 질문

- AZ별 NAT GW 3개와 단일 NAT GW의 가용성 차이는 실제로 얼마나 큰가?
- "AZ 간 데이터 전송 비용"이 NAT GW 비용 결정에서 차지하는 비중은 어느 정도인가?
- prod와 dev/beta를 다른 토폴로지로 가는 결정이 IaC 모듈을 어떻게 더 깔끔하게 만드는가?

## 다루는 관점

- ✅ 설계 선택의 근거(Why) — Well-Architected REL-2, 단일 NAT SPOF
- ✅ 기본기 — NAT의 본질, AZ 데이터 전송 요금, EIP·ENI 관계
- ✅ 온프레비교 — 사내 NAT 라우터 이중화와의 비교

## 02 문서 근거

- §1.3.2 IGW · NAT GW — AZ별 1개(prod 3개), Well-Architected REL-2
- §1.3.2 dev/beta 비용 절감 예외 — `single_nat_gateway = true`
- §1.2.2 동일 AZ NAT 라우팅으로 AZ 간 데이터 전송 비용 차단
- §1.3.1 라우팅 테이블 세트 — AZ별 RT가 동일 AZ NAT를 가리키는 구조

## 타깃 독자 & 난이도

- 주니어~중급 SRE/DevOps, FinOps에 관심 있는 백엔드 엔지니어
- ★★★☆☆

## 예상 분량

- 보통 (~3,000자)

## 글 아웃라인

1. **3개 vs 1개 — 같은 NAT인데 무엇이 그렇게 다른가**
   - SPOF의 정의, AZ 단위 장애 시나리오 가정
2. **NAT 비용의 두 축 — 시간당 요금 + 데이터 처리 요금**
   - NAT GW 자체 비용과 GB당 처리 비용의 합산
3. **숨은 항목: AZ 간 데이터 전송 비용**
   - Private-App-2a → NAT-GW-2b → IGW 경로가 AZ 데이터 전송 요금을 발생시키는 이유
   - "AZ별 라우팅 테이블"이 비용을 차단하는 메커니즘 (§1.3.1)
4. **환경별 토폴로지 분기 — `single_nat_gateway` 한 변수의 무게**
   - prod: AZ별 3개 (가용성 우선)
   - dev/beta: 1개 (월 비용 1/3)
   - IaC 모듈에서 같은 모듈을 다른 변수로 호출하는 패턴
5. **장애 시나리오 시뮬레이션**
   - 단일 NAT 환경에서 AZ-2a NAT가 죽으면 어떤 트래픽이 영향을 받는가
   - 멀티 NAT 환경에서 같은 사건의 blast radius
6. **결론 — 가용성을 환경별로 회계하는 일**

## 온프레 대비 포인트

- 사내 NAT는 통상 방화벽/라우터에 통합되어 있고 HSRP/VRRP로 액티브-스탠바이 구성한다.
- 온프레의 "이중화는 거의 무료" 사상(이미 박스 두 대를 사뒀으니까)은 클라우드의 "이중화는 곱하기" 사상과 갈린다.
- 클라우드는 **이중화의 단가가 명시적**이라 환경별로 다른 토폴로지를 쓰는 결정이 자연스러워진다.

## 참고할 1차 출처

- AWS NAT Gateway: https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat-gateway.html
- ECS Outbound Best Practices: https://docs.aws.amazon.com/AmazonECS/latest/bestpracticesguide/networking-outbound.html
- AWS Well-Architected Reliability Pillar: https://docs.aws.amazon.com/wellarchitected/latest/reliability-pillar/

## 시리즈 인용 관계

이 단편은 다음 시리즈에서 인용·확장된다:
- **[S1 2편 — 코어 스위치/NAT 라우터: 온프레 박스를 TGW+NAT GW로 분해하기](./series-01-onprem-to-vpc.md)** — 본 글의 환경별 회계를 온프레 HSRP/VRRP 운영 모델과 매핑하고, "이중화의 단가가 명시적이라 환경 분기가 자연스러워진다"는 결론을 더한다.
- **[S2 3편 — 환경별 비용 분기: NAT를 시작으로 본 IaC 모듈 분기 전략](./series-02-vpc-design-diary.md)** — 본 글이 다룬 NAT 한 컴포넌트의 환경 분기를, Flow Logs/Network Firewall/WAF/PHZ 등 4가지 다른 환경 분기 결정과 묶어 IaC 모듈 패턴으로 통합한다.

## 작성 메모

- "월 N달러" 같은 절대 금액은 02 문서에 명시되지 않은 한 사용 금지. 비교는 **상대 비율**(1/3 절감)로만.
- 02 §1.3.1의 라우팅 테이블 표를 그대로 인용해 "AZ별 RT가 동일 AZ NAT를 가리킨다"를 시각적으로 보이기.
