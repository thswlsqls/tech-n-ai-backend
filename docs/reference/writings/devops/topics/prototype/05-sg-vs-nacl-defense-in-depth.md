# 05. Stateful(SG)과 Stateless(NACL)을 함께 쓰는 이유 — 심층 방어의 두 레이어

> 1차 소스: [`02-network-vpc.md` §2, §3.1](../../../../../devops/results/02-network-vpc.md)

## 한줄 요약(Hook)

> SG가 있는데 NACL을 또 두는 게 중복일까? 아니다. 하나는 ENI에, 하나는 서브넷에 붙어 있어 공격자가 한쪽을 우회해도 다른 한쪽이 남는다. **두 도구의 책임은 서로 다르다.**

## 핵심 질문

- 스테이트풀과 스테이트리스의 차이는 운영 중 어떤 사고로 드러나는가?
- "SG는 SG ID로 참조한다"는 규칙이 Fargate 스케일링 환경에서 가지는 의미는?
- Private-Data 서브넷의 NACL 아웃바운드를 인터넷 완전 차단으로 두는 결정의 함의는?

## 다루는 관점

- ✅ 설계 선택의 근거(Why) — 심층 방어, CIS Benchmark
- ✅ 기본기 — Stateful vs Stateless 방화벽, ENI 단위 vs 서브넷 단위
- ✅ 온프레비교 — 전통 방화벽(L3/L4 Stateful)과 ACL의 매핑

## 02 문서 근거

- §2.1 SG 정의 및 매트릭스 — SG ID 참조 원칙, 기본 아웃바운드 제거
- §2.3 SG 변경 관리 — Terraform 단일 진실 공급원, AWS Config Rule `restricted-common-ports`
- §3.1 NACL 4세트 — Public / Private-App / Private-Data / Private-TGW
- §3.1 핵심 방어 — `nacl-prod-private-data` 아웃바운드 인터넷 완전 차단

## 타깃 독자 & 난이도

- 주니어~중급 SRE/DevOps, 보안 입문자
- ★★★☆☆

## 예상 분량

- 보통 (~3,500자)

## 글 아웃라인

1. **두 도구를 한 번에 두는 이유 — 책임 분리**
   - SG: ENI 단위, 스테이트풀, IP가 아닌 SG ID로 참조 가능
   - NACL: 서브넷 단위, 스테이트리스, 명시적 인바운드/아웃바운드 모두 룰 필요
2. **스테이트풀이 운영을 얼마나 편하게 만드는가**
   - 이펨버럴 포트(1024-65535) 리턴 트래픽을 외울 필요 없음
   - NACL은 스테이트리스라 양방향 모두 명시
3. **SG ID 참조 원칙의 진짜 가치 — Fargate 스케일링 환경**
   - Task가 죽고 살아나며 ENI IP가 바뀌어도 SG는 변하지 않음
   - "CIDR 대신 SG ID"가 단순한 베스트 프랙티스가 아니라 **운영 자동화의 전제**
4. **02 매트릭스 읽기 — `sg-prod-api-gateway`의 인바운드/아웃바운드 해석**
   - ALB → API Gateway → 각 백엔드 SG → 데이터 SG의 호출 그래프
5. **NACL이 막는 것 — SG가 못 막는 사고**
   - Private-Data 서브넷 아웃바운드 `0.0.0.0/0` 완전 차단
   - 데이터 계층의 SSRF/공격받은 컨테이너가 외부로 나갈 경로 자체를 제거
6. **변경 관리 — 콘솔 수동 수정을 막는 두 겹의 안전장치**
   - Terraform 단일 소스 + AWS Config Rule + CloudTrail
7. **결론 — "두 개라 중복"이 아니라 "두 곳을 지킨다"**

## 온프레 대비 포인트

- 온프레의 코어 방화벽(체크포인트/팔로알토)은 보통 스테이트풀이고, 라우터 ACL은 스테이트리스다.
- AWS의 SG=Stateful FW, NACL=ACL이라는 1:1 매핑이 거의 그대로 성립한다.
- 다만 SG의 **SG ID 참조**는 온프레에는 없는 기능 — 사내망에서는 "어떤 IP 대역의 호스트가 들어오는가"로 설계해야 했고, 클라우드에서는 "어떤 역할(SG)을 가진 워크로드가 들어오는가"로 추상화 수준이 한 단계 올라간다.

## 참고할 1차 출처

- AWS Security Group: https://docs.aws.amazon.com/vpc/latest/userguide/VPC_SecurityGroups.html
- AWS Network ACL: https://docs.aws.amazon.com/vpc/latest/userguide/vpc-network-acls.html
- CIS AWS Foundations Benchmark: https://www.cisecurity.org/benchmark/amazon_web_services

## 시리즈 인용 관계

이 단편은 다음 시리즈에서 인용·확장된다:
- **[S1 1편 — ACL과 방화벽: 온프레 정책을 NACL+SG로 다시 그리기](./series-01-onprem-to-vpc.md)** — 본 글의 결론을 사내 라우터 ACL/체크포인트 방화벽/iptables 운영 모델과 1:1 매핑하고, "SG ID 참조"라는 매핑이 깨지는 지점을 다룬다.
- **[S2 2편 — '데이터 유출 경로 0'은 한 결정의 결과가 아니다](./series-02-vpc-design-diary.md)** — 본 글의 SG/NACL과 [단편 04](./04-vpc-endpoint-privatelink.md)의 PrivateLink가 합쳐졌을 때 성립하는 시스템 속성을 침해 시뮬레이션으로 검증한다.

## 작성 메모

- §2.1의 SG 매트릭스를 본문에 옮기되, 글 흐름에 따라 일부 행만 발췌해 보여주는 게 가독성에 좋다.
- 도입부에 "SG = 호텔 방문, NACL = 호텔 정문 검문" 류의 비유 한 줄 권장.
