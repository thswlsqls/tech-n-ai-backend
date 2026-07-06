# 01. 장기 자격증명 없는 CI/CD — GitHub Actions OIDC 페더레이션과 역할을 4개로 쪼갠 이유

> 1차 소스: [`devops/aws/{dev,beta,prod}/security.png`](../../../../../../../devops/aws/prod/security.png) · [`architecture-facts.md` §5 IAM](../../../../../../../devops/aws/architecture-facts.md)

## 한줄 요약(Hook)

> 다이어그램 왼쪽 위 "CI/CD trust boundary" 상자 안에는 자물쇠 아이콘이 다섯 개 있다. OIDC Provider 하나와 IAM Role 네 개(`gha-deploy-{env}`, `gha-terraform-apply-{env}`, `gha-terraform-readonly`, `gha-security-scan`). 어디에도 AWS Access Key는 없다. GitHub Actions 워크플로가 AWS 리소스를 배포하는데 저장소 시크릿에 장기 자격증명을 넣지 않는 방법과, 왜 역할 하나가 아니라 넷으로 쪼갰는지를 각 역할의 `sub` 클레임 조건에서 확인할 수 있다.

## 핵심 질문

- OIDC 페더레이션이 장기 IAM 사용자 액세스 키를 대체하는 방식은 무엇이며, 왜 이것이 더 안전한가?
- 배포(deploy)·Terraform apply·Terraform readonly·보안 스캔을 왜 하나의 역할이 아니라 4개의 역할로 나눴는가?
- 각 역할의 신뢰 정책이 `sub` 클레임(예: `environment:prod`, `pull_request`, `ref:refs/heads/main`)으로 "어떤 워크플로만 이 역할을 assume할 수 있는가"를 어떻게 제한하는가?

## 다루는 관점

- ✅ 설계 근거(Why) — CI/CD 파이프라인에 장기 자격증명을 두지 않아야 하는 이유
- ✅ 구현 — IAM 신뢰 정책의 OIDC 조건과 GitHub Actions 워크플로 설정
- ✅ 운영 — 역할을 쪼갠 것이 사고 발생 시 노출 범위를 어떻게 줄이는가

## 근거

- 다이어그램: prod `security.png`의 "CI/CD trust boundary(bootstrap · GitHub OIDC)" 상자, OIDC Provider ↔ GitHub Actions 사이 "OIDC federation" 화살표, 4개 Role 노드의 `sub` 라벨(`sub environment:prod`, `sub tf-prod`, `sub pull_request`, `sub refs/heads/main`)과 각 화살표 목적지(ECR push/pull·update·PassRole / encrypts·pull / describe/pull / scan)
- `architecture-facts.md` §5 IAM — GitHub OIDC Role 4종(184~189행): `gha-deploy-{env}`(sub `repo:{org}/{repo}:environment:{env}`. 권한: ECR push/pull(techai/*)·ECS update/RegisterTaskDef·CodeDeploy create·PassRole(task/exec role)·SSM/Secrets read·Amplify start-job·Signer sign, max session 3600), `gha-terraform-readonly`(sub `pull_request`, `ReadOnlyAccess` managed + tfstate read 인라인), `gha-terraform-apply-{env}`(sub `environment:tf-{env}`, `PowerUserAccess` + IAM 관리 인라인 + tfstate RW), `gha-security-scan`(sub `ref:refs/heads/main`, ECR describe/pull + Inspector findings)
- `architecture-facts.md` §5 OIDC Provider(189행) — `token.actions.githubusercontent.com`, aud `sts.amazonaws.com`

## 타깃 독자 & 난이도

- GitHub Actions에서 AWS로 배포하는 파이프라인을 처음 설계하거나, 저장소 시크릿에 여전히 `AWS_ACCESS_KEY_ID`를 넣고 있는 팀의 데브옵스·백엔드 엔지니어
- ★★★☆☆ (사전지식: IAM 역할과 신뢰 정책, GitHub Actions 워크플로 기본 문법)

## 예상 분량

- 보통 (~3,500자)

## 글 아웃라인

1. **들어가며 — 자물쇠 아이콘 다섯 개, Access Key는 0개**
   - CI/CD trust boundary 상자를 처음 볼 때의 관찰에서 출발
2. **OIDC 페더레이션이 대체하는 것**
   - 워크플로가 실행될 때마다 GitHub가 서명한 토큰을 발급받고, AWS STS가 이를 검증해 단기 자격증명을 내주는 흐름
3. **왜 역할 하나가 아니라 넷인가**
   - 배포·Terraform apply·Terraform readonly·보안 스캔이 서로 다른 권한 범위(쓰기 vs 읽기, 환경별 vs 전역)를 요구하는 이유
4. **`sub` 클레임이 만드는 울타리**
   - `environment:prod`는 prod 배포 워크플로에서만, `pull_request`는 PR 워크플로에서만 각 역할을 assume할 수 있다는 것을 신뢰 정책 조건으로 확인
5. **결론 — 역할을 쪼개는 비용과 대가**
   - 관리해야 할 역할이 늘어나는 복잡도와, 자격증명 하나가 새어도 전체 계정이 아니라 좁은 범위만 위험해지는 이득을 함께 짚기

## 참고할 1차 출처

- IAM과 OpenID Connect 자격 증명 공급자: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-idp_oidc.html
- GitHub Actions에서 AWS용 OpenID Connect 구성(GitHub 공식 문서): https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services

## 시리즈 인용 관계

이 단편은 시리즈의 출발점이다. CI/CD가 배포 시점에만 assume하는 4개 역할을 다룬 뒤, 컨테이너가 실행되는 동안 상시 사용하는 6개 Task Role은 **[02 — Task Role 6개, 시크릿 5개](./02-ecs-task-role-secrets-least-privilege.md)**로 넘긴다. 두 신뢰 경계(배포 시점 vs 실행 시점)를 같은 최소 권한 원칙으로 엮어 읽도록 의도했다.

## 작성 메모

- "OIDC가 무조건 안전하다"는 식으로 단순화하지 않는다. 신뢰 정책의 조건(`sub`, `aud`)을 제대로 좁히지 않으면 여전히 위험할 수 있다는 점을 함께 짚어, 조건 설정 자체가 핵심이라는 톤을 유지한다.
- 4개 역할의 정확한 권한 목록을 나열하는 데 그치지 않고, "왜 이 경계에서 나눴는가"(쓰기/읽기, PR/main, 환경별/전역)라는 기준을 분명히 드러낸다.
