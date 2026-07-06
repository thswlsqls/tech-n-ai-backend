# 시리즈 series-01. 세 장의 다이어그램을 나란히 놓고 읽기 — dev·beta·prod가 함께 움직이는 방향

> 1차 소스: [`devops/aws/{dev,beta,prod}/network-topology.png`](../../../../../aws/prod/network-topology.png) · [`architecture-facts.md` §7 환경 차이 매트릭스](../../../../../aws/architecture-facts.md)
> 본 글은 **메타 글**이다. 대응 단편이 이미 답한 각 결정의 근거(왜 MSK가 단계적인가, 왜 TLS가 prod에만 켜지는가)를 반복하지 않는다. 대신 이 결정들이 **같은 방향으로 함께 움직인다**는, 단편 하나로는 보이지 않는 사실만 다룬다.

## 시리즈 훅

> network-topology 다이어그램 세 장을 각각 따로 보면 "환경마다 설정이 다르구나" 정도로 지나치기 쉽다. 하지만 세 장을 나란히 놓고 dev → beta → prod로 시선을 옮기면, NAT Gateway 개수·MSK 클러스터 형태·ALB TLS 종단이라는 서로 무관해 보이는 세 결정이 정확히 같은 순서로 "가장 저렴함 → 검증 가능함 → 가장 신뢰할 수 있음"을 향해 움직인다. 이 글은 그 정렬이 우연이 아니라는 것을, 그리고 그 정렬을 만든 공통 원리가 무엇인지를 확인한다.

## 이 글이 단편 위에 더하는 것

- **정렬(alignment)** — 서로 다른 서비스(Kafka, TLS, NAT)에 대한 결정이 왜 하필 같은 순서로 단계화됐는가
- **공통 원리** — 이 정렬을 개별 결정이 아니라 하나의 조직적 습관으로 설명하는 AWS 공식 가이드는 무엇인가
- **다이어그램을 코드 리뷰처럼 읽는 절차** — 세 장을 비교해 "이 환경엔 왜 이 구성 요소가 없지?"를 질문하는 것 자체가 인프라 문서를 검증하는 방법이라는 제안

이는 [01](./01-msk-staged-rollout.md)이나 [02](./02-alb-https-toggle.md) 단편 하나만 읽어서는 드러나지 않는, 세 다이어그램을 동시에 봐야만 보이는 가치다.

## 인용 빌딩블록과 이 글이 반복하지 않는 것

| 신호 | 인용 단편/외부 자료 | 단편이 이미 답한 것 (반복하지 않음) |
|---|---|---|
| Kafka 관리형 서비스 commitment | [단편 01 — MSK 3단계 배치](./01-msk-staged-rollout.md) | dev에 MSK가 없는 이유, beta/prod가 Serverless/Provisioned로 갈리는 근거 |
| TLS 종단 commitment | [단편 02 — ALB HTTPS 토글](./02-alb-https-toggle.md) | `var.alb_certificate_arn` 토글의 구현 방식과 TLS 정책 선택 근거 |
| NAT Gateway 이중화 | `docs/reference/writings/devops/topics/prototype/03-nat-gateway-az-tradeoff.md`(별도 시리즈, 02-network-vpc.md 기반) | AZ별 NAT 3개 vs single_nat_gateway의 환경별 회계 — **본 글은 이 근거를 재도출하지 않고, "이 결정도 같은 방향으로 정렬돼 있다"는 사실만 인용한다** |

## 꼭지

1. **도입 — 세 다이어그램을 나란히 놓고 표로 옮기기**
   - `architecture-facts.md` §7 환경 차이 매트릭스에서 NAT·MSK·ALB 프로토콜 세 행만 발췌한 축소 표 제시
2. **정렬을 확인하기 — dev < beta < prod가 세 축 모두에서 성립하는가**
   - NAT: 1 → 1 → 3 (신뢰성 축)
   - MSK: 없음 → Serverless → Provisioned (관리형 서비스 commitment 축)
   - ALB: HTTP → HTTP → HTTPS (보안 축)
   - 세 축의 "전환점"이 전부 beta→prod 사이에 몰려 있고, dev→beta 사이에는 MSK 한 축만 움직인다는 관찰
3. **이 정렬을 설명하는 공식 원리 — 환경을 계정/워크로드 경계로 나누는 이유**
   - AWS가 권고하는 다중 계정·환경 전략에서 비프로덕션 환경의 목적(빠른 반복·비용 최소화)과 프로덕션 환경의 목적(신뢰성·보안 강화)이 왜 다른 인프라 강도로 이어지는지
4. **이 정렬이 깨지는 지점을 찾아보기 — 매트릭스에서 예외를 찾는 법**
   - `architecture-facts.md` §7에서 "환경마다 다르지 않은" 항목(VPC Endpoint on, KMS 키 수 등)을 짚어, 모든 것이 단계화 대상은 아니라는 균형 잡힌 시선 제공
5. **결론 — 다이어그램은 설정값의 스냅샷이 아니라 조직의 위험 감수 곡선이다**
   - 세 장의 다이어그램을 함께 읽는 습관이 새 인프라 결정을 어느 환경에 먼저 넣을지 판단하는 데 재사용 가능한 질문("이 결정도 세 축과 같은 방향인가?")을 남긴다는 정리

## 참고할 1차 출처

- AWS 다중 계정 전략(Control Tower 가이드): https://docs.aws.amazon.com/controltower/latest/userguide/aws-multi-account-landing-zone.html
- AWS Organizations 모범 사례 — 다중 계정으로 워크로드 분리: https://docs.aws.amazon.com/organizations/latest/userguide/orgs_best-practices.html
- 단편 01·02의 참고 출처(MSK, ALB HTTPS)는 재인용하지 않고 링크만 남긴다.

## 작성 가이드(이 시리즈 공통)

- 본문에는 `architecture-facts.md`에 line-cited된 사실만 사용한다(추정치는 "추정:" 표기).
- 단편이 이미 답한 "왜"를 시리즈에서 다시 설명하지 않는다 — 표와 링크로 인용하고 넘어간다.
- 다이어그램 이미지는 세 장을 나란히 배치하거나, `architecture-facts.md` §7 표를 축약해 재현한다.
