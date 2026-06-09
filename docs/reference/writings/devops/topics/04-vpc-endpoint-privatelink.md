# 04. NAT를 우회하는 두 가지 길 — Gateway Endpoint와 Interface Endpoint 선택 기준

> 1차 소스: [`02-network-vpc.md` §1.4](../../../../../devops/results/02-network-vpc.md)

## 한줄 요약(Hook)

> S3는 Gateway, ECR은 Interface. 한 글자 차이가 비용과 보안 모델을 동시에 바꾼다. PrivateLink는 NAT 우회가 아니라 **트래픽이 AWS 백본을 떠나지 않게 하는 결정**이다.

## 핵심 질문

- Gateway Endpoint와 Interface Endpoint는 무엇이 다르며, 언제 어느 쪽을 선택해야 하는가?
- VPC Endpoint를 도입했을 때 절감되는 비용은 NAT 처리 비용인가, 데이터 전송 비용인가, 둘 다인가?
- "NAT 비용 절감"보다 **"데이터 유출 경로 차단"**이라는 보안 효과가 더 큰 이유는?
- MongoDB Atlas PrivateLink는 왜 Peering이 아니라 PrivateLink를 선택했는가?

## 다루는 관점

- ✅ 설계 선택의 근거(Why) — 비용·보안의 동시 최적화
- ✅ 기본기 — PrivateLink, ENI, Prefix List, Endpoint Policy
- ✅ 온프레비교 — 사내 프록시·서비스 메시·Egress 게이트웨이와의 매핑

## 02 문서 근거

- §1.4 VPC Endpoint(PrivateLink) — Gateway/Interface 구분 표
- §1.4 Endpoint Policy — `aws:PrincipalOrgID`, `aws:SourceVpce` Condition
- §1.4 Private DNS 활성화, 전용 SG(`sg-vpce`)
- §1.4 MongoDB Atlas PrivateLink — Atlas 측 IP Access List
- §1.1.1 Atlas Peering 대신 PrivateLink 채택의 대역 충돌 회피 근거

## 타깃 독자 & 난이도

- 중급 SRE/DevOps, 보안에 관심 있는 백엔드 엔지니어
- ★★★★☆ (사전지식: SG, 라우팅 테이블, IAM Condition Key)

## 예상 분량

- 김 (~5,000자)

## 글 아웃라인

1. **두 가지 PrivateLink — Gateway와 Interface의 본질**
   - Gateway: 라우팅 테이블에 Prefix List(`pl-xxxx`) 한 줄 추가, 무료
   - Interface: 서브넷에 ENI 생성, 시간당·데이터당 요금
2. **선택 매트릭스 — 02 §1.4 표의 의사결정 흐름**
   - S3, DynamoDB → Gateway (무료, AWS가 둘만 지원)
   - ECR(api+dkr), Logs, Secrets Manager, STS, SSM×3, KMS, Kafka → Interface
3. **NAT 비용 절감의 진짜 정체**
   - NAT GW 데이터 처리 비용 + AZ 데이터 전송 비용의 합
   - 트래픽이 AWS 백본을 떠나지 않으므로 IGW를 거치지 않음
4. **NAT 절감보다 더 큰 효과 — Data Exfiltration 차단**
   - Endpoint Policy로 "본 조직 외 S3 버킷에는 못 쓰게" 강제 (`aws:PrincipalOrgID`)
   - 컴플라이언스(GDPR/개인정보보호) 관점의 의미
5. **Private DNS — 클라이언트 코드를 바꾸지 않아도 되는 이유**
   - `s3.ap-northeast-2.amazonaws.com`을 그대로 써도 자동 라우팅
   - SDK/Spring Boot 설정 무변경
6. **운영의 디테일 — `sg-vpce` 전용 SG, ECR이 두 개인 이유**
   - `ecr.api`와 `ecr.dkr`을 모두 만들어야 하는 이유
7. **MongoDB Atlas PrivateLink — 외부 SaaS와도 같은 모델**
   - Peering의 대역 충돌 리스크(`192.168/16`)를 PrivateLink가 어떻게 근본 제거하는가
8. **결론 — PrivateLink는 "NAT 절감 도구"가 아니라 "신뢰 경계 도구"**

## 온프레 대비 포인트

- 사내에서 외부 SaaS 호출을 통제하던 Forward Proxy(Squid/Zscaler)와 PrivateLink Interface Endpoint는 거의 같은 역할을 한다.
- 서비스 메시의 Egress Gateway와도 비교 가능. 다만 PrivateLink는 **AWS API와 SaaS 호출에 한해** 백본을 떠나지 않는다는 점이 결정적 차이.
- 온프레의 "내부망에서 끝나는 호출"이 클라우드에서는 "VPC를 떠나지 않는 호출"로 번역된다.

## 참고할 1차 출처

- AWS PrivateLink 개념: https://docs.aws.amazon.com/vpc/latest/privatelink/concepts.html
- AWS PrivateLink로 AWS 서비스 접근: https://docs.aws.amazon.com/vpc/latest/privatelink/privatelink-access-aws-services.html
- MongoDB Atlas Private Endpoint: https://www.mongodb.com/docs/atlas/security-private-endpoint/

## 시리즈 인용 관계

이 단편은 다음 시리즈에서 인용·확장된다:
- **[S1 3편 — Forward Proxy/서비스 메시: PrivateLink로 다시 그리는 외부 호출](./series-01-onprem-to-vpc.md)** — 본 글의 결론을 사내 Squid/Zscaler/Egress Gateway 운영 모델과 매핑하고, "화이트리스트 단위가 다르다"는 결정적 차이를 다룬다.
- **[S2 2편 — '데이터 유출 경로 0'은 한 결정의 결과가 아니다](./series-02-vpc-design-diary.md)** — 본 글의 PrivateLink와 [단편 05](./05-sg-vs-nacl-defense-in-depth.md)의 SG/NACL 심층방어가 합쳐졌을 때만 성립하는 **누적 효과**를 침해 시뮬레이션 3편으로 검증한다.

## 작성 메모

- 도입부 훅: "PrivateLink는 비용 도구라 들었는데, 막상 써 보니 보안 도구더라" 류의 반전 구도가 어울린다.
- §1.4의 표를 본문에 그대로 옮기면 글의 산출 밀도가 높아진다.
