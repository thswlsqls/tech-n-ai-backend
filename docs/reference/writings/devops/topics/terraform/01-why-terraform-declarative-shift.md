# 01. 왜 Terraform인가 — 명령형에서 선언형으로의 사고 전환

> 1차 소스: [`09-iac-terraform.md` §1 도구 선정](../../../../../../devops/results/09-iac-terraform.md)

## 한줄 요약(Hook)

> 셸 스크립트로 인프라를 만들면 "어떻게 만들지"를 한 줄씩 적는다. Terraform은 "무엇이 있어야 하는지"만 적고, 그 상태로 가는 길은 도구가 계산한다. Java 개발자가 처음 부딪히는 벽은 문법이 아니라 이 사고의 방향이 뒤집히는 지점이다.

## 핵심 질문

- 콘솔 클릭이나 셸 스크립트 대신 Terraform을 쓰면 실제로 뭐가 달라지는가?
- 명령형(어떻게)과 선언형(무엇)의 차이가 일상 작업에서 어떻게 드러나는가?
- `plan`과 `apply`는 각각 무엇을 보장하는가?
- Provider 버전을 왜 핀(pin) 고정하고 lock 파일을 커밋하는가?

## 다루는 관점

- ✅ 개념 이해(Why) — IaC를 쓰는 이유, 선언형 모델의 핵심
- ✅ 실전 사용(기본기) — init → plan → apply의 첫 한 바퀴
- ✅ Java 개발자 대비 — 명령형 코드 습관과의 충돌, Gradle 의존성 핀과의 유비

## 09 문서 근거

- §1.1 IaC 도구 비교 — Terraform/CDK/CloudFormation/Pulumi 표
- §1.2 선정 결론 — "코드가 곧 리소스 명세", HCL의 낮은 진입 장벽, 멀티 계정·멀티 환경 재현
- §1.3 버전 및 Provider 핀 고정 — `required_version`, `required_providers`, `.terraform.lock.hcl` 커밋
- 실제 코드: [`envs/beta/providers.tf`](../../../../../../devops/terraform/envs/beta/providers.tf) (`~> 1.9.5`, AWS `~> 5.60`)

## 타깃 독자 & 난이도

- Terraform을 처음 쓰는, Java 미들 개발자 출신 데브옵스 엔지니어
- ★☆☆☆☆ (사전지식: AWS 리소스 한두 개를 콘솔에서 만들어 본 경험)

## 예상 분량

- 보통 (~3,500자)

## 글 아웃라인

1. **들어가며 — 콘솔로 만든 인프라는 왜 재현이 안 되나**
   - 손으로 만든 VPC를 dev/beta/prod에 똑같이 세 번 만들 때 생기는 미묘한 차이
2. **명령형 vs 선언형 — 사고의 방향이 뒤집힌다**
   - 셸 스크립트: "이 명령을 이 순서로 실행하라"(어떻게)
   - Terraform: "이 리소스들이 존재해야 한다"(무엇). 순서·생성/수정/삭제 판단은 도구가 한다
   - Java 개발자에게: SQL의 선언형(원하는 결과를 적고 실행 계획은 옵티마이저가)과 비슷한 결
3. **plan과 apply — 실행 전에 미래를 본다**
   - `plan`은 "지금 상태에서 코드대로 가려면 무엇을 만들고/바꾸고/지울지"를 미리 보여 준다
   - `apply`는 그 계획을 실제로 실행한다. plan 없는 apply는 컴파일 없이 배포하는 것과 같다
4. **Provider — 클라우드를 다루는 플러그인**
   - AWS Provider가 HCL을 실제 AWS API 호출로 옮긴다
   - `required_providers`로 어떤 Provider의 어떤 버전 범위를 쓸지 선언(§1.3)
5. **버전 핀과 lock 파일 — 재현성의 기본기**
   - `~> 1.9.5`, `~> 5.60` 같은 범위 고정 + `.terraform.lock.hcl` 커밋
   - Java 개발자에게: Gradle/Maven의 의존성 버전 고정과 lock 파일과 같은 동기
6. **이 저장소가 Terraform을 고른 이유 — 멀티 계정·멀티 환경 재현**
   - §1.2 결론을 이 프로젝트 맥락(dev/beta/prod 동일 설계 재현)으로 옮겨 설명
7. **마무리 — 문법보다 먼저 넘어야 할 건 사고의 방향**

## Java 개발자 대비 포인트

명령형에 익숙한 개발자는 Terraform 코드를 "위에서 아래로 실행되는 스크립트"로 오해하기 쉽다. 실제로는 리소스 사이의 의존 관계로 실행 순서가 정해지고, 같은 코드를 두 번 apply해도 결과가 같다(멱등성). 이 차이를 "SQL을 처음 배울 때 for 루프 대신 SELECT를 쓰는 법을 익히던 순간"에 빗대면 Java 출신 독자가 빠르게 잡는다.

## 참고할 1차 출처 (공식 문서)

- Terraform Documentation — https://developer.hashicorp.com/terraform/docs
- Terraform Language — Overview — https://developer.hashicorp.com/terraform/language
- Provider Requirements — https://developer.hashicorp.com/terraform/language/providers/requirements
- Dependency Lock File — https://developer.hashicorp.com/terraform/language/files/dependency-lock
- Terraform AWS Provider — https://registry.terraform.io/providers/hashicorp/aws/latest/docs

## 시리즈 인용 관계

이 단편은 시리즈의 **출발점**이다. 뒤의 모든 단편이 여기서 잡은 plan/apply·선언형·Provider 개념을 전제한다. 특히 4번 절에서 "plan은 무엇과 비교하는가?"라는 질문을 일부러 열어 두고, 그 답(state와의 3자 비교)은 **[02 — State](./02-state-the-source-of-truth.md)** 로 넘긴다. 본 단편에서는 state를 "장부가 있다" 한 줄로만 예고한다.

## 작성 메모

- "왜 IaC인가"를 일반론으로 길게 늘이지 말 것. 이 저장소가 dev/beta/prod를 같은 설계로 재현해야 한다는 **구체적 동기**(§1.2)에 빨리 착지한다.
- 도구 비교표(§1.1)는 전부 옮기지 말고, Terraform이 선택된 핵심 근거 2~3개만 추린다(멀티 계정 재현, HCL 진입 장벽, 모듈 생태계).
- plan/apply를 "안전장치"로만 말하지 말고 **멱등성**과 묶어 설명해야 선언형의 진짜 이점이 산다.
- lock 파일 커밋은 §1.3·§8 체크리스트에 명시돼 있으니 추측 없이 그대로 인용한다.
</content>
