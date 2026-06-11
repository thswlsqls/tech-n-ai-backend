# 06. Bastion 없이 운영하는 법 — SSM Session Manager로 22번 포트를 영구 폐쇄하기

> 1차 소스: [`02-network-vpc.md` §2.2](../../../../../devops/results/02-network-vpc.md)

## 한줄 요약(Hook)

> "관리자가 들어가야 하니까 22번은 열어둘 수밖에 없잖아요"는 더 이상 유효한 변명이 아니다. SSM Session Manager는 SG에 22번을 한 줄도 두지 않고도 같은 일을 한다.

## 핵심 질문

- Bastion 호스트가 만드는 운영 부담(키 관리, 패치, 감사)은 정확히 무엇인가?
- SSM Session Manager가 22번 포트 없이 어떻게 셸 접속을 제공하는가?
- "SSH 22 금지"를 IAM 조건과 어떻게 결합해 강제할 수 있는가?

## 다루는 관점

- ✅ 설계 선택의 근거(Why) — CIS 5.2, AWS SRA
- — 기본기(곁가지) — SSM 에이전트, 역방향 ENI
- ✅ 온프레비교 — 점프 호스트(Jump host) / Bastion / 사내 VPN의 매핑

## 02 문서 근거

- §0 설계 원칙 — "SSH 금지: SSM Session Manager 전용, 22 포트 개방 금지"
- §2.2 접근(관리) 정책 — Bastion 없음, `ecs execute-command`, IAM Condition (`aws:SourceIp`, `aws:MultiFactorAuthPresent`)
- §1.4 SSM Interface Endpoint 3종 (`ssm`, `ssmmessages`, `ec2messages`)
- §2.1 매트릭스의 `sg-prod-admin-ssm` — SSH 22 금지 원칙

## 타깃 독자 & 난이도

- 주니어~중급 SRE/DevOps, 보안 입문자
- ★★☆☆☆

## 예상 분량

- 짧음 (~1,800자)

## 글 아웃라인

1. **Bastion이라는 운영 부채 — SSH 키 + 패치 + 감사**
2. **SSM Session Manager의 통신 모델 — 22번 포트 없이 셸이 열리는 원리**
   - SSM 에이전트가 아웃바운드로 SSM 엔드포인트에 연결
   - 사용자는 IAM 인증으로 콘솔/CLI에서 세션 시작
   - VPC 내부에서 `ssm`, `ssmmessages`, `ec2messages` Interface Endpoint 3종으로 NAT도 우회
3. **Fargate에서의 동일 모델 — `ecs execute-command`**
   - Task에 진입할 때도 22번 포트를 열지 않음
4. **IAM Condition으로 진입 자체를 잠그기**
   - `aws:SourceIp = 회사 VPN CIDR`
   - `aws:MultiFactorAuthPresent = true`
5. **감사와 운영 — 모든 세션이 자동으로 CloudTrail/CloudWatch에 남는다**
6. **결론 — 22번 포트를 열어둘 정당한 이유는 거의 없다**

## 온프레 대비 포인트

- 사내망에서는 점프 호스트 + 사내 VPN 조합이 일반적이고, 점프 호스트는 SSH 키와 패치, 접속 로그 보관의 운영 부담을 항상 동반했다.
- SSM Session Manager는 점프 호스트를 **AWS 관리형 컴포넌트로 대체**하는 결정에 가깝다.
- 그러나 "VPN으로 사내망에 들어가야 한다"는 원칙은 IAM Condition(`aws:SourceIp`)으로 클라우드에서도 동일하게 강제할 수 있다.

## 참고할 1차 출처

- AWS Systems Manager Session Manager: https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager.html
- ECS Exec: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-exec.html
- CIS AWS Foundations Benchmark 5.2: https://www.cisecurity.org/benchmark/amazon_web_services

## 시리즈 인용 관계

이 단편은 **시리즈에 흡수되지 않는 독립 글**이다. CIS 5.2 컴플라이언스와 SSM Session Manager의 통신 모델이라는 단일 주제이며, 시리즈의 인과 사슬(S2)이나 온프레 매핑(S1) 어느 쪽에도 자연스럽게 끼워 넣을 자리가 없다. 단편 01(IPv6)과 함께 "시리즈 외 자산"으로 분류된다.

## 작성 메모

- 분량이 짧은 글이므로 코드/CLI 한두 개를 박아두면 좋다. 단, 02 문서에 명시되지 않은 옵션·플래그는 공식 문서에서 한 번 더 확인 후 인용.
- "Bastion이 그리워질까? 일주일이면 잊혀진다" 류의 마무리가 어울린다.
