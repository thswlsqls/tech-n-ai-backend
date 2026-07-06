# 03. dev에는 없는 MSK를 향한 권한 — kafka-cluster:*가 아무것도 가리키지 않을 때

> 1차 소스: [`devops/aws/dev/security.png`](../../../../../../../devops/aws/dev/security.png) · [`architecture-facts.md` §2 MSK·§5 IAM](../../../../../../../devops/aws/architecture-facts.md)

## 한줄 요약(Hook)

> dev 다이어그램의 노란 메모에는 이렇게 적혀 있다. "MSK is OFF (enable_msk=false) — so api-agent's kafka-cluster:* grant exists in the role but has NO MSK target in dev (unused!)". api-agent의 IAM 정책은 dev·beta·prod 세 환경에서 완전히 동일하다. 그런데 dev에는 애초에 MSK 클러스터가 없다. 아무것도 가리키지 않는 권한이 코드에 그대로 남아 있다는 것은 무엇을 의미하는가.

## 핵심 질문

- Task Role의 IAM 정책이 환경마다 달라지지 않고 동일하게 유지되는 것은, Terraform 모듈 설계상 왜 자연스러운 결과인가?
- `kafka-cluster:*` 같은 리소스 기반 권한이, 그 리소스 자체가 존재하지 않는 환경에서는 실질적으로 어떤 상태가 되는가?
- 이런 "구조적으로 비어 있는 권한"을 최소 권한 원칙 관점에서 문제로 볼지, 무시해도 되는 것으로 볼지는 어떤 기준으로 판단할 수 있는가?

## 다루는 관점

- ✅ 구현 — IAM 정책과 리소스 존재 여부가 서로 독립적으로 관리된다는 사실
- ✅ 운영 — IaC 재사용성과 환경별 최소 권한 사이의 긴장을 읽는 법

## 근거

- 다이어그램: dev `security.png` 노란 메모 — "DEV specifics: ... MSK is OFF (enable_msk=false) — so api-agent's kafka-cluster:* grant exists in the role but has NO MSK target in dev (unused!). NAT 1 (single). All 5 per-env KMS keys + 6 task roles identical to beta/prod." dev·beta·prod 세 다이어그램 모두 "task roles identical to {다른 두 환경}"이라고 동일하게 명시
- `architecture-facts.md` §2 MSK(99행) — dev는 `enable_msk` default **false** → MSK 미생성
- `architecture-facts.md` §5 IAM(180행) — api-agent Task Role의 `kafka-cluster:*`(Connect/Describe/Read/WriteData 등) 권한, mongodb-uri read
- `architecture-facts.md` 환경 구조 주의(4행) — `envs/dev·beta·prod`의 `.tf` 파일은 byte 단위로 동일하고, 환경별 차이는 오직 `terraform.tfvars`·`variables.tf` default 값에서만 발생한다는 사실

## 타깃 독자 & 난이도

- Terraform으로 여러 환경에 동일한 모듈을 재사용하면서, IAM 정책도 리소스 존재 여부에 맞춰 조건부로 좁혀야 하는지 고민하는 인프라 엔지니어
- ★★★★☆ (사전지식: IAM 리소스 기반 정책, Terraform 모듈의 환경별 값 주입 구조)

## 예상 분량

- 짧음 (~2,800자)

## 글 아웃라인

1. **들어가며 — 노란 메모 한 줄이 짚은 "unused" 권한**
   - dev 다이어그램에서만 보이는 이 메모가 왜 눈에 띄는지
2. **왜 세 환경의 Task Role 정책이 byte 단위로 같은가**
   - `.tf` 파일 공유 + `tfvars`만 다르다는 구조가 IAM 정책까지 그대로 복제하는 이유
3. **`kafka-cluster:*` 권한이 dev에서 실질적으로 가리키는 것**
   - 존재하지 않는 ARN에 대한 권한이 API 호출 시점에 어떻게 처리되는지(리소스가 없으므로 호출 자체가 실패한다는 사실)
4. **이것을 최소 권한 원칙 위반으로 볼지 판단하는 기준**
   - 크리덴셜이 탈취됐을 때 이 권한이 실제 공격 표면이 될 수 있는지(리소스가 없어 원천적으로 무해한지), 아니면 향후 MSK가 켜졌을 때를 대비한 잠재적 위험으로 봐야 하는지 — 코드만으로 결론을 단정하지 않고 판단 기준을 정리
5. **결론 — IaC 재사용성과 환경별 최소 권한의 트레이드오프**
   - 모듈을 환경마다 동일하게 재사용하는 이점과, 그로 인해 "쓰이지 않는 권한"이 함께 복제되는 비용을 함께 인정하는 정리

## 참고할 1차 출처

- IAM 정책 요소 — Resource: https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_elements_resource.html
- Amazon MSK IAM 액세스 제어: https://docs.aws.amazon.com/msk/latest/developerguide/iam-access-control.html

## 시리즈 인용 관계

**[02 — Task Role 6개, 시크릿 5개](./02-ecs-task-role-secrets-least-privilege.md)**가 표로 정리한 6개 Task Role 중 api-agent 한 줄을 그대로 이어받아 심화한다. 02의 다른 5개 Task Role 권한은 반복하지 않는다.

## 작성 메모

- "이것은 버그다"라고 단정하지 않는다. IAM 정책이 리소스 존재 여부와 무관하게 관리되는 것은 Terraform 모듈 재사용의 자연스러운 결과이지, 반드시 실수는 아니라는 점을 분명히 한다.
- "위험하지 않다"고도 단정하지 않는다. dev 자격증명이 향후 prod로 승격되거나, dev에 MSK가 나중에 켜질 가능성까지 고려하면 이 권한이 계속 무해하다고 보장할 수는 없다는 점을 함께 남긴다. 결론은 "확인 필요"에 가까운 균형 잡힌 톤으로 마무리한다.
