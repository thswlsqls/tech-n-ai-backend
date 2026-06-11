# 기술 블로그 주제 인덱스 — Terraform 입문 (Java 개발자 출신 데브옵스용)

> 1차 소스: [`09-iac-terraform.md`](../../../../../../devops/results/09-iac-terraform.md)
> 보조 컨텍스트(실 구현체): [`devops/terraform/`](../../../../../../devops/terraform/README.md) — `bootstrap/`·`modules/`·`envs/`
>
> 본 문서는 09-iac-terraform.md(설계 명세)와 그 구현체인 `devops/terraform/` 코드를 1차 소스로 도출한 **Terraform 입문 시리즈**의 주제 설계도 인덱스다.
> 대상 독자는 **Terraform을 처음 쓰는, Java 미들 개발자 출신 데브옵스 엔지니어**다. 모든 단편은 다음 3관점으로 일관되게 잡았다: **(1) 개념 이해(Why) / (2) 실전 사용(기본기) / (3) Java 개발자 대비**.

## 시리즈의 성격 — 순서대로 읽으면 누적되는 선형 학습 시리즈

`devops/topics/`의 네트워크 시리즈가 "단편(빌딩블록) × 시리즈(메타 글)" 모델이었다면, 본 시리즈는 **선형 학습 시리즈**다.

- 각 단편은 **독립 출판이 가능**하다(한 편에 한 주제).
- 동시에 **01 → 06 순서로 읽으면 개념이 쌓인다**. 앞 단편이 잡은 개념(plan/apply, state, 모듈)을 뒤 단편이 전제로 쓰고, 정의를 반복하지 않는다.
- 그래서 별도의 메타/시리즈 글(`series-*.md`)을 두지 않는다. 누적 효과는 각 단편의 "시리즈 인용 관계" 절이 직접 잇는다.

### 학습 곡선 설계 — 어려운 두 개념을 앞에 배치

Java 개발자가 가장 막히는 두 지점은 **선언형 사고**(01)와 **state**(02)다. 둘 다 Java/Gradle에 대응물이 없어서, 문법(03)보다 **먼저** 떼어내 멘탈 모델부터 세운다. 문법·모듈·환경(03~05)은 그 위에서 실코드로 익히고, 팀 운영(06)에서 앞의 숙제(시크릿·drift·권한)를 모아 닫는다.

```
01 선언형 사고  ─┐
                 ├─→ 03 문법 ─→ 04 모듈 ─→ 05 환경 조립 ─→ 06 팀 운영
02 state       ─┘                                              ↑
   (멘탈 모델)         (실코드로 익히기)            (앞의 운영 숙제를 닫음)
```

## 1. 단편 글 목록

| # | 제목 | Why | 기본기 | Java대비 | 09 근거 | 분량 |
|---|---|:-:|:-:|:-:|---|---|
| [01](./01-why-terraform-declarative-shift.md) | 왜 Terraform인가 — 명령형에서 선언형으로의 사고 전환 | ✅ | ✅ | ✅ | §1.1, §1.2, §1.3 | 보통 |
| [02](./02-state-the-source-of-truth.md) | State — Terraform이 현실을 기억하는 법 | ✅ | ✅ | ✅ | §3.1, §3.2, §3.4, §3.5 | 김 |
| [03](./03-hcl-language-essentials.md) | HCL 문법 핵심 — resource·variable·output·local·data와 표현식 | ✅ | ✅ | ✅ | §4.1, §4.2 | 김 |
| [04](./04-module-design.md) | 모듈 설계 — 재사용 단위와 입력 검증 | ✅ | ✅ | ✅ | §2.2, §4, §5 | 김 |
| [05](./05-environment-composition.md) | 환경 조립 — 변수 한두 개로 dev/beta/prod를 분기한다 | ✅ | ✅ | ✅ | §2.2, §2.3, §3.2 | 보통 |
| [06](./06-operate-safely-in-team.md) | 팀에서 안전하게 굴리기 — 품질 게이트·시크릿·OIDC CI/CD | ✅ | ✅ | ✅ | §1.4, §3.3, §6, §8 | 김 |

### 단편 사이 인용 관계 (선형 누적)

| 단편 | 앞 단편 전제 | 뒤 단편으로 넘기는 숙제 |
|---|---|---|
| 01 선언형 사고 | — (출발점) | "plan은 무엇과 비교하나" → 02 |
| 02 state | 01 plan/apply | 환경별 state → 05 · 시크릿 평문/접근 Role → 06 |
| 03 HCL 문법 | 01·02 멘탈 모델 | 블록·검증 문법을 모듈 재료로 → 04 |
| 04 모듈 설계 | 03 문법 | 모듈을 환경별 호출 재료로 → 05 · prevent_destroy/태그 운영 → 06 |
| 05 환경 조립 | 02 state · 04 모듈 | 환경별 apply 승인 → 06 |
| 06 팀 운영 | 01~05 전체 | — (시리즈 마무리) |

## 2. 폐기·병합 로그(투명성)

- ❌ **"Terraform 설치와 첫 실행 튜토리얼"** — HashiCorp 공식 Getting Started를 베끼는 글이 되기 쉬움. init/plan/apply 한 바퀴는 **01의 한 절로 흡수**(개념 전달 목적에 한정).
- ❌ **"Provider 심화 — alias·멀티 리전·멀티 계정"** — 입문 범위 초과. 01에서 "Provider란 무엇인가 + 버전 핀"까지만 다루고 심화는 제외.
- 🔁 **"모듈별 상세 가이드(network/ecs-service/aurora 각각)"** — 09 §5가 이미 입력·출력 표로 정리했고, 네트워크 설계 자체는 별도 시리즈([`devops/topics/`](../prototype/README.md))가 다룸. 본 시리즈는 **모듈을 "어떻게 설계·재사용하나"**(04)에 집중하고, 개별 모듈 해설은 폐기.
- 🔁 **"CI/CD 파이프라인"과 "IaC 시크릿 관리"를 별도 두 편으로** — 둘 다 "팀에서 안전하게 운영"이라는 한 서사라 **06으로 병합**. 따로 떼면 각 편의 1차 소스(§1.4·§6)가 단독으로는 얇다.
- 🔁 **"drift 탐지 자동화 / `terraform test` / Terratest 심화"** — 운영 심화 주제. 06에서 "이런 게 있고 왜 필요한지"까지만 언급하고, 구현 디테일을 다루는 단독 글은 입문 범위 밖으로 폐기(Simplicity First).

## 3. 작성 가이드

- **인용 정책**: 기술적 사실의 근거는 09 문서 §번호 + 09 §9의 공식 출처(HashiCorp/AWS 공식 문서, GitHub OIDC 공식 가이드)만 사용한다. 블로그·포럼·AI 생성 콘텐츠 인용 금지(`tech-n-ai-backend/CLAUDE.md` 외부 자료 참조 원칙).
- **본문 언어·톤**: 한국어 `-ㅂ니다`체(완성 글은 `write-tech-blog` 규칙). 고유명사·기술 용어는 영문 유지(Terraform, state, Provider, OIDC 등).
- **실코드 우선**: 가짜 예제를 새로 짓기보다 `devops/terraform/`의 실제 코드 조각을 인용한다(s3-bucket의 `validation`·`dynamic`, beta `terraform.tfvars`의 환경 값, bootstrap의 OIDC Role 등).
- **버전 의존 사실 표기**: Terraform 버전에 따라 달라지는 것(1.9의 DynamoDB 잠금 vs 1.10+의 `use_lockfile`, `import` 블록은 1.5+, `terraform test`는 1.6+, Trivy의 tfsec 통합)은 **버전을 명시**하고, 단정 전에 공식 문서로 확인한다.
- **Java 대비는 도입으로, 한계까지**: 모든 단편이 Java 개념과의 유비로 진입하되(plan=컴파일+diff, 모듈=순수 함수, tfvars=프로파일), state처럼 **대응물이 없거나 유비가 깨지는 지점**을 분명히 짚는다. 유비를 과신하게 두지 않는다.
- **단편 작성 시**: 도입부에 09 §번호 인용 박스를 둔다. 각 단편 끝의 "시리즈 인용 관계" 절은 그대로 유지해 다음 단편이 어떤 개념을 전제하는지 신호를 남긴다.
- **분량·SEO**: 완성 글은 `write-tech-blog`에서 7,000자 이상·SEO 제목 후보 3개+·번호 없는 소제목으로 다듬는다. 설계도의 아웃라인 번호는 기획용이다.

## 공식 출처 (09 §9 + 단편별 보강)

- Terraform Documentation — https://developer.hashicorp.com/terraform/docs
- Terraform Language — https://developer.hashicorp.com/terraform/language
- Terraform Recommended Practices — https://developer.hashicorp.com/terraform/cloud-docs/recommended-practices
- Terraform AWS Provider — https://registry.terraform.io/providers/hashicorp/aws/latest/docs
- AWS Prescriptive Guidance — Terraform AWS Provider Best Practices — https://docs.aws.amazon.com/prescriptive-guidance/latest/terraform-aws-provider-best-practices/
- GitHub Actions — Configuring OIDC in AWS — https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services
- Trivy — https://github.com/aquasecurity/trivy · Checkov — https://www.checkov.io/ · tflint — https://github.com/terraform-linters/tflint

> 위 공식 출처 외의 블로그/AI 생성 문서는 본 시리즈의 근거로 인용하지 않는다.
</content>
