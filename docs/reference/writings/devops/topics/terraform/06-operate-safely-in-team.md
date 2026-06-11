# 06. 팀에서 안전하게 굴리기 — 품질 게이트·시크릿·OIDC CI/CD

> 1차 소스: [`09-iac-terraform.md` §1.4 품질 도구·§6 시크릿·§8 체크리스트](../../../../../../devops/results/09-iac-terraform.md)

## 한줄 요약(Hook)

> 혼자 쓰는 Terraform과 팀이 쓰는 Terraform은 다른 도구다. 누가 apply하는가, 시크릿이 state로 새지 않는가, 잘못된 코드가 머지되기 전에 걸리는가 — 이 세 질문에 답이 있어야 인프라 코드가 안전해진다.

## 핵심 질문

- 커밋·머지 전에 어떤 자동 게이트가 코드를 거르나(fmt/validate/lint/보안 스캔)?
- 시크릿을 Terraform으로 다루되 state에 평문이 남지 않게 하려면?
- 장기 액세스 키 없이 GitHub Actions가 어떻게 AWS에 배포하나(OIDC)?
- 실수로 인프라가 지워지거나 코드 밖에서 바뀌는 걸 무엇으로 막나?

## 다루는 관점

- ✅ 개념 이해(Why) — 팀 운영에서 새로 생기는 위험과 그 방어
- ✅ 실전 사용(기본기) — pre-commit·CI 게이트, OIDC Role, 시크릿 패턴
- ✅ Java 개발자 대비 — CI 파이프라인·정적 분석·비밀 관리 경험과의 연결

## 09 문서 근거

- §1.4 품질 도구 체인 — `fmt`/`validate`/`tflint`/`Trivy`/`Checkov`/`terraform-docs`, pre-commit·CI
- §3.3 상태 접근 IAM Role — state 버킷/락/KMS 접근을 워크로드 권한과 분리
- §6 IaC 시크릿 관리 — Secrets Manager 껍데기만 IaC, 값은 런타임/로테이션, state 평문 0건
- §8 베스트 프랙티스 체크리스트 — PR 기반 plan 코멘트·환경별 승인, drift 주 1회, `prevent_destroy`, 보안 스캔 게이트, import는 코드로
- 실제 코드: [`bootstrap/oidc.tf`](../../../../../../devops/terraform/bootstrap/oidc.tf) (GitHub OIDC Provider), [`bootstrap/roles.tf`](../../../../../../devops/terraform/bootstrap/roles.tf) (gha-deploy/terraform/security-scan 4종 Role)

## 타깃 독자 & 난이도

- Terraform을 처음 쓰지만 팀 단위로 운영해야 하는, Java 미들 개발자 출신 데브옵스 엔지니어
- ★★★★☆ (사전지식: 01~05편 전체, CI/CD·IAM 기본 개념)

## 예상 분량

- 김 (~5,000자) — 운영 주제가 넓어 절이 많다

## 글 아웃라인

1. **들어가며 — apply 권한이 모두에게 있으면 생기는 일**
   - 로컬에서 누구나 prod에 apply하던 팀의 사고 시나리오
2. **커밋 전 게이트 — pre-commit 도구 체인**
   - `terraform fmt`(포매팅)·`validate`(구문/참조)·`tflint`(AWS 룰)·`terraform-docs`(README 자동)(§1.4)
   - Java 대비: 포매터 + 컴파일 + 정적 분석을 커밋 훅으로 당겨 오는 것
3. **CI 보안 게이트 — Trivy와 Checkov**
   - `Trivy config`(구 tfsec, Aqua 통합)·`Checkov`로 Critical/High 발견 시 머지 차단(§8)
   - Java 대비: SpotBugs/SonarQube 게이트와 같은 위치, 대상이 인프라일 뿐
4. **누가 apply하나 — OIDC와 4종 Role**
   - GitHub Actions가 장기 키 없이 `AssumeRoleWithWebIdentity`로 단기 자격을 받는다(`bootstrap/oidc.tf`)
   - `sub` 조건으로 "어느 워크플로가 어느 환경에 배포하는지"를 못 박는다(`bootstrap/roles.tf`: gha-deploy-{env}, gha-terraform-apply-{env} 등)
   - PR엔 read-only Role로 `plan` 코멘트, apply는 환경별 승인 + 전용 Role로만(§8)
5. **시크릿 — state로 새지 않게**
   - 원칙: Secrets Manager 리소스 자체는 IaC, 값은 IaC 바깥에서 주입·로테이션(§6.1)
   - 안티패턴: tfvars 평문, `data "aws_secretsmanager_secret_version"`로 값 읽기 → state 평문 노출(§6.3)
   - 런타임 조회: ECS Task의 `secrets` 필드로 Execution Role이 주입(§6.4)
   - 02편의 "state엔 민감 값이 평문으로 남는다"가 여기서 해결된다
6. **지워지면 안 되는 것·코드 밖 변경 — 가드와 탐지**
   - `prevent_destroy`로 Aurora/MSK/KMS/상태 버킷 보호(§8, 04편과 연결)
   - drift 주 1회 탐지: `terraform plan -detailed-exitcode`가 0이 아니면 알림(§8, 02편 drift의 운영판)
7. **이미 있는 리소스를 코드로 — import**
   - 콘솔로 만든 자원을 `import { to = ... id = ... }` 블록으로 가져와 plan diff를 0으로(§3.4)
8. **마무리 — 게이트·권한·시크릿이 갖춰지면 인프라 코드는 애플리케이션 코드처럼 다뤄진다**

## Java 개발자 대비 포인트

이 단편은 Java 개발자의 기존 경험과 가장 잘 붙는다. pre-commit/CI 게이트는 포매터·컴파일·SpotBugs/SonarQube를 인프라로 옮긴 것이고, OIDC는 CI에 장기 비밀 키를 박지 않는다는 점에서 익숙한 "비밀 없는 배포" 흐름이다. 차이는 **시크릿이 state라는 산출물에 평문으로 남을 수 있다**는, IaC 특유의 함정이다. 이 한 가지를 강조하면 나머지는 독자가 자기 경험으로 빠르게 채운다.

## 참고할 1차 출처 (공식 문서)

- GitHub Actions — Configuring OIDC in AWS — https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services
- Sensitive Data in State — https://developer.hashicorp.com/terraform/language/state/sensitive-data
- Import — https://developer.hashicorp.com/terraform/language/import
- Tests — https://developer.hashicorp.com/terraform/language/tests
- tflint — https://github.com/terraform-linters/tflint
- Trivy (Aqua) — https://github.com/aquasecurity/trivy
- Checkov — https://www.checkov.io/
- AWS Tagging Best Practices (Whitepaper) — https://docs.aws.amazon.com/whitepapers/latest/tagging-best-practices/tagging-best-practices.html

## 시리즈 인용 관계

시리즈의 **마무리 편**으로, 앞 단편이 남긴 운영 숙제를 모아 닫는다: **[02 — State](./02-state-the-source-of-truth.md)** 의 "state 시크릿 평문"·"접근 Role 분리"·drift, **[04 — 모듈 설계](./04-module-design.md)** 의 `prevent_destroy`·태그 정책, **[05 — 환경 조립](./05-environment-composition.md)** 의 "환경별 apply 승인". 각 개념의 정의는 해당 단편에 있으므로 여기서는 **운영(누가·언제·무엇으로 막나)** 만 다루고 정의를 반복하지 않는다.

## 작성 메모

- 도구 나열식("이런 도구가 있습니다")로 흐르지 말 것. 각 게이트를 **무슨 사고를 막는가**(머지 전 보안 결함, 무자격 apply, state 시크릿 유출)로 묶어 서사화한다.
- OIDC는 thumbprint·`sub` 조건까지 깊이 파면 입문 독자가 빠진다. "장기 키 0개 + 어느 워크플로가 어느 환경에"까지만, 세부는 공식 문서로 넘긴다.
- §6 안티패턴(`data` source로 시크릿 읽기 → state 노출)은 반드시 코드로 보여 준다. 이 시리즈에서 가장 자주 저지르는 실수다.
- 버전 의존적 사실(Trivy가 tfsec를 통합, `import` 블록은 1.5+, `terraform test`는 1.6+)은 §1.4·§3.4·§4.5에 적힌 그대로 인용하고, 글에 쓸 땐 버전을 함께 명시한다.
- drift 자동 탐지·`terraform test`는 깊은 운영 주제다. 입문 시리즈 범위에선 "이런 게 있고 왜 필요한지"까지만, 구현 디테일은 과감히 생략(Simplicity First).
</content>
