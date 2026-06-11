# 팀에서 Terraform을 안전하게 굴리기 — 게이트·권한·시크릿으로 사고를 막는 법

> 시리즈: Terraform 입문 — Java 개발자가 데브옵스로 넘어갈 때 (6편, 마무리)

---

## SEO 제목 후보

- **팀에서 Terraform을 안전하게 굴리기 — 게이트·권한·시크릿으로 사고를 막는 법** — 혼자 쓰던 Terraform을 팀 운영으로 옮기며 사고를 줄이고 싶은 독자에게
- **GitHub Actions OIDC로 장기 키 없이 AWS에 배포하기 — Terraform CI/CD 권한 설계** — 액세스 키를 CI에 박지 않고 배포 권한을 나누는 방법을 찾는 독자에게
- **Terraform state에 시크릿이 평문으로 남는 문제와 그 방어** — IaC에서 비밀 값 관리와 state 노출을 검색하는 독자에게

---

## 들어가며 — apply 권한이 모두에게 있으면 생기는 일

혼자 쓰는 Terraform과 팀이 쓰는 Terraform은 사실상 다른 도구입니다. 혼자일 때는 내 노트북에서 `terraform apply`를 치는 일이 전부지만, 사람이 늘어나면 같은 명령이 위험해집니다. 누구나 자기 노트북에서 운영 환경에 apply할 수 있다면, 어느 날 누군가의 로컬에만 있던 변경이 운영에 그대로 반영되고, 그 사람이 자리를 비운 사이 무엇이 왜 바뀌었는지 아무도 설명하지 못하는 상황이 옵니다. 인프라 코드가 팀의 자산이 되려면, 코드 자체보다 그 코드를 누가 언제 무엇으로 거르고 적용하는가가 먼저 정리되어야 합니다.

이 글은 그 운영 장치를 다룹니다. 앞선 다섯 편에서 잡은 개념(선언형 사고, state, 문법, 모듈, 환경 조립)을 전제로 두고, 여기서는 정의를 다시 설명하지 않습니다. 대신 세 가지 질문에 답합니다. 잘못된 코드가 머지되기 전에 무엇이 거르는가, 누가 어떤 권한으로 apply하는가, 시크릿이 state로 새지 않으려면 어떻게 하는가입니다. 각 장치를 "이런 도구가 있다"가 아니라 "어떤 사고를 막는가"로 묶어 보겠습니다.

Java 미들 개발자로 일하다 데브옵스로 넘어온 분이라면 이 편이 기존 경험과 가장 잘 붙습니다. 커밋 훅과 CI 게이트는 포매터·컴파일·정적 분석을 인프라로 옮긴 것이고, OIDC 배포는 CI에 비밀 키를 박지 않는다는 점에서 익숙한 흐름입니다. 진짜 낯선 함정은 하나뿐입니다. 시크릿이 state라는 산출물에 평문으로 남을 수 있다는 IaC 특유의 문제입니다.

---

## 커밋 전에 거르기 — 포매팅·검증·린트

가장 앞단의 게이트는 커밋 전에 동작합니다. Terraform은 코드 자체를 점검하는 명령을 기본으로 제공합니다. `terraform fmt`는 코드 포맷을 표준 스타일로 맞추고, `terraform validate`는 구문이 올바른지, 참조가 어긋나지 않는지를 확인합니다. 이 둘은 외부 자원에 접근하지 않고 코드만 검사하므로 빠르고, 커밋 훅으로 당겨 오기에 적합합니다.

여기에 린트와 문서 자동화를 더합니다. `tflint`는 Terraform 공식 문서가 다루는 기본 검증을 넘어, 잘못된 인스턴스 타입이나 폐기된 문법 같은 클라우드별 규칙을 잡아 줍니다. `terraform-docs`는 모듈의 변수와 출력을 읽어 README 표를 자동으로 만들어 줍니다. 이 도구들은 HashiCorp 본체가 아니라 별도 오픈소스이므로, 공식 저장소(`terraform-linters/tflint` 등)를 근거로 두고 쓰는 편이 좋습니다.

Java 개발자에게 이 단계는 새로울 게 없습니다. 포매터로 스타일을 맞추고, 컴파일로 문법을 검증하고, 정적 분석으로 잠재 결함을 잡는 그 흐름을 커밋 훅으로 당겨 온 것과 같습니다. 다른 점은 검사 대상이 애플리케이션 코드가 아니라 인프라 코드라는 것뿐입니다.

---

## 머지 전에 거르기 — 보안 스캔 게이트

다음 게이트는 보안 결함이 머지되는 것을 막습니다. 인프라 코드는 한 줄을 잘못 적으면 버킷이 공개되거나 보안 그룹이 0.0.0.0/0에 열리는 식으로 사고가 큽니다. 그래서 코드 정적 분석에 더해, 인프라 설정을 보안 관점으로 스캔하는 단계를 CI에 둡니다.

이 자리에 흔히 쓰는 도구가 Trivy와 Checkov입니다. Trivy는 Aqua Security가 만든 스캐너로, 한때 별도 도구였던 tfsec의 Terraform 설정 검사 기능을 통합해 왔습니다. Checkov는 Bridgecrew(현재 Palo Alto Networks) 계열의 정책 스캐너입니다. 두 도구 모두 "공개 접근 허용", "암호화 미설정" 같은 위험한 설정을 규칙으로 잡아냅니다. 운영에서는 보통 Critical이나 High 등급이 발견되면 머지를 막도록 게이트를 겁니다. 이 도구들의 정확한 규칙과 통합 범위는 버전에 따라 달라지므로, 각 공식 저장소(`aquasecurity/trivy`, `checkov.io`)에서 확인하는 편이 안전합니다.

이 단계 역시 Java 개발자에게는 SpotBugs나 SonarQube 게이트와 같은 위치에 있습니다. 정적 분석이 일정 등급 이상의 문제를 찾으면 빌드를 깨뜨리는 그 구조 그대로이고, 대상이 인프라 설정으로 바뀐 것뿐입니다.

---

## 누가 apply하는가 — OIDC와 권한을 나눈 네 개의 Role

가장 핵심은 권한입니다. 코드가 게이트를 다 통과해도, 그 코드를 누가 어떤 권한으로 클라우드에 적용하는가가 정리되지 않으면 운영은 여전히 위험합니다. 이 프로젝트는 사람의 노트북이 아니라 GitHub Actions가 배포를 맡되, CI에 장기 액세스 키를 두지 않는 방식을 씁니다. OIDC(OpenID Connect)입니다.

GitHub 공식 문서는 OIDC를 두고 "GitHub Actions 워크플로가 AWS 자격 증명을 장기 GitHub 시크릿으로 저장하지 않고도 AWS 자원에 접근할 수 있게 한다"고 설명합니다. 동작은 이렇습니다. 워크플로가 GitHub이 발급한 OIDC 토큰을 받아 AWS에 제시하면, AWS가 그 토큰을 확인하고 짧은 수명의 임시 자격 증명을 내줍니다. 공식 문서의 표현으로는 이 토큰이 "짧은 수명의 액세스 토큰으로 외부 서비스를 인증하는 데 쓰인다"고 합니다. CI 어딘가에 영구 비밀 키를 박아 두고 그게 새어 나갈까 걱정하는 모형 자체가 사라집니다.

이 프로젝트의 bootstrap은 먼저 GitHub을 신뢰할 발급자로 등록합니다.

```hcl
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # ... thumbprint_list 생략
}
```

그다음이 핵심입니다. "GitHub이 보낸 토큰이면 무조건 신뢰"가 아니라, 어느 저장소의 어느 워크플로가 어느 환경에 들어오는지를 못 박습니다. 그 못이 토큰의 `sub`(subject) 조건입니다. AWS OIDC 가이드도 신뢰 정책에서 `token.actions.githubusercontent.com:sub` 조건 키를 평가해 "어떤 GitHub 액션이 Role을 맡을 수 있는지를 제한"하라고 권합니다. 이 프로젝트는 그 권고를 따라 권한을 네 개의 Role로 쪼갰습니다. 배포용(`gha-deploy-{env}`), PR 단계의 읽기 전용(`gha-terraform-readonly`), 환경별 apply용(`gha-terraform-apply-{env}`), 주간 보안 스캔용(`gha-security-scan`)입니다.

apply용 Role의 신뢰 정책을 보면 조건이 어떻게 박히는지 드러납니다.

```hcl
condition {
  test     = "StringEquals"
  variable = "${local.oidc_provider_url}:sub"
  values   = ["repo:${local.github_repo_full}:environment:tf-${each.value}"]
}
```

이 조건은 "이 저장소의 `tf-prod` 같은 특정 GitHub 환경에서 실행된 워크플로만 이 apply Role을 맡을 수 있다"는 뜻입니다. PR에서 도는 워크플로는 이 조건을 만족하지 못하므로 apply 권한을 절대 얻지 못합니다. 그래서 PR 단계에서는 읽기 전용 Role로 `plan`만 돌려 변경 내용을 코멘트로 남기고, 실제 apply는 환경별 승인을 거친 전용 Role로만 일어납니다. 같은 코드라도 "변경을 미리 보는 일"과 "변경을 적용하는 일"의 권한이 물리적으로 분리되는 것입니다.

권한 분리는 state 접근에서도 한 번 더 드러납니다. 읽기 전용 Role은 state 버킷에 `s3:GetObject`까지만 허용되고, apply Role만 `s3:PutObject`로 state를 쓸 수 있습니다. plan은 state를 읽기만 하면 되고, state를 바꾸는 권한은 실제로 적용하는 쪽에만 준다는 원칙이 코드에 그대로 박혀 있습니다. 이 state 접근 권한을 워크로드 자체의 권한과 분리하는 일은 2편에서 짚은 "state 접근 Role을 따로 둔다"의 운영판이기도 합니다.

OIDC는 더 깊이 파면 토큰 thumbprint나 `sub` 패턴 매칭 같은 세부가 이어집니다. 입문 단계에서는 "장기 키 0개, 그리고 어느 워크플로가 어느 환경에 들어오는지를 조건으로 못 박는다"까지만 잡으면 충분하고, 나머지는 공식 문서로 넘기는 편이 낫습니다.

---

## 시크릿 — state로 새지 않게

이제 IaC 특유의 함정입니다. Terraform은 자기가 관리하는 자원의 현재 상태를 state 파일에 기록합니다. 문제는 그 state에 민감한 값이 평문으로 들어갈 수 있다는 점입니다. Terraform 공식 문서는 이를 분명히 적습니다. "설정에 비밀 값을 직접 넣으면 Terraform이 그 비밀을 state와 plan 파일에 저장한다", 그리고 로컬에서 작업하면 "state를 평문 파일로 저장하며, 거기에는 설정에 정의한 비밀 값이 포함된다"고 합니다.

여기서 흔히 저지르는 실수가 있습니다. 시크릿 값을 코드로 읽어 와 다른 자원에 넘기는 패턴입니다. 예를 들어 `data` 소스로 Secrets Manager의 시크릿 버전을 읽으면, 그 비밀 값이 Terraform의 손을 거치는 순간 state에 기록됩니다. 한 가지 더 알아 둘 점은, 변수에 `sensitive`를 달아도 이 문제가 풀리지 않는다는 것입니다. 공식 문서는 "`sensitive` 인자가 붙은 값도 state와 plan 파일 양쪽에 저장되며, 그 파일에 접근할 수 있는 사람은 누구나 그 값을 볼 수 있다"고 못 박습니다. `sensitive`는 CLI 출력에서 값을 가릴 뿐, state에 안 남게 해 주는 장치가 아닙니다.

그래서 이 프로젝트가 택한 원칙은 역할을 나누는 것입니다. 시크릿을 담을 그릇, 즉 Secrets Manager의 시크릿 자원 자체는 IaC로 선언해 만들되, 그 안에 들어갈 실제 값은 Terraform 바깥에서 주입하고 로테이션합니다. 코드는 "여기에 이런 이름의 비밀 저장소가 있다"까지만 알고, 비밀의 내용물은 모릅니다. 그러면 비밀 값이 Terraform의 손을 거치지 않으므로 state에 평문으로 남을 일이 없습니다.

런타임에서 비밀이 필요한 쪽은 어떻게 받을까요. 이 프로젝트의 ECS 워크로드는 Task 정의의 `secrets` 필드를 통해 Execution Role의 권한으로 비밀을 주입받습니다. 실제로 dev 환경의 Execution Role에는 Secrets Manager와 SSM 파라미터를 읽는 권한이 인라인으로 붙어 있습니다.

```hcl
statement {
  sid     = "ReadSecrets"
  actions = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
  resources = [
    "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${var.project}/${var.environment}/*",
  ]
}
```

비밀의 내용물은 Terraform이 아니라 실행 시점의 컨테이너가 자기 Role 권한으로 직접 읽어 갑니다. 2편에서 남겨 둔 "state에 시크릿이 평문으로 남는다"는 숙제가 여기서 닫힙니다. 그릇은 코드로, 내용물은 런타임으로 나누는 한 줄 원칙입니다.

---

## 지워지면 안 되는 것, 코드 밖에서 바뀌는 것

운영에서는 사람의 실수로 중요한 자원이 지워지는 일도 막아야 합니다. Terraform에는 `lifecycle` 블록의 `prevent_destroy` 옵션이 있어, 이 값을 켠 자원은 destroy 대상에 들어가면 apply가 거부됩니다. 데이터베이스나 KMS 키, state를 담은 버킷처럼 한 번 지우면 복구가 어려운 자원에 이 가드를 걸어 둡니다. 모듈 설계를 다룬 4편에서 짚은 자원 보호 장치의 운영 활용입니다.

다른 한쪽의 위험은 코드 밖에서 일어나는 변경입니다. 누군가 콘솔에서 손으로 설정을 바꾸면, 코드가 기술한 상태와 실제 인프라가 어긋납니다. 이 어긋남을 drift라고 부르고, 2편에서 개념을 다뤘습니다. 운영에서는 이를 주기적으로 탐지합니다. `terraform plan`을 정기적으로 돌려 코드와 현실 사이에 차이가 있는지 확인하고, 차이가 잡히면 알림을 보내 사람이 그 변경이 의도된 것인지 판단하게 합니다. drift를 자동으로 되돌리는 일까지 가면 운영이 더 깊어지는데, 입문 단계에서는 "이런 탐지가 왜 필요한지"까지만 잡아도 충분합니다.

이미 콘솔로 만들어 둔 자원을 코드의 관리 아래로 들이는 일도 운영에서 자주 생깁니다. Terraform은 설정 기반 import를 지원합니다. 가져올 자원과 그 식별자를 코드로 선언하면, Terraform이 그 자원을 state에 편입시키고 plan으로 차이를 보여 줍니다. 콘솔에서 손으로 만든 자원도 일단 코드 관리로 들어오면, 그 뒤의 모든 변경은 다시 코드와 리뷰를 거치게 됩니다. import의 정확한 문법과 지원 범위는 Terraform 버전에 따라 다르므로 공식 import 문서를 확인하는 편이 좋습니다.

---

## 마무리 — 인프라 코드를 애플리케이션 코드처럼

여기까지가 팀에서 Terraform을 굴릴 때 필요한 운영 장치입니다. 커밋과 머지 전에 게이트가 잘못된 코드와 보안 결함을 거르고, OIDC와 분리된 Role이 누가 무엇에 apply할 수 있는지를 못 박고, 시크릿은 그릇만 코드로 두어 state로 새지 않게 합니다. 거기에 중요한 자원을 destroy로부터 보호하고, 코드 밖 변경을 탐지하는 장치를 더합니다.

개인적으로 이 운영 장치들을 정리하면서 가장 또렷해진 생각은, 이것들이 모이면 결국 인프라 코드가 애플리케이션 코드와 같은 규율로 다뤄진다는 점이었습니다. 리뷰를 거쳐 머지되고, 자동 게이트를 통과해야 하며, 권한이 분리된 파이프라인으로만 배포되고, 비밀은 코드에 박지 않습니다. Java 개발자로 일하며 당연하게 여겼던 그 규율이 인프라에도 그대로 적용되는 것이고, 새로 배워야 할 것은 의외로 적습니다. 새로운 건 state라는 산출물이 비밀을 머금을 수 있다는 한 가지 함정뿐이었습니다.

또 하나 남는 인상은, 팀 운영의 안전이 똑똑한 코드가 아니라 단순한 분리에서 온다는 점이었습니다. plan과 apply의 권한을 나누고, 읽기와 쓰기의 state 접근을 나누고, 비밀의 그릇과 내용물을 나누는 일은 어느 것도 복잡하지 않습니다. 다만 이 분리가 코드에 박혀 있으면, 사람이 실수해도 권한이 그 실수를 막아 줍니다. 이 시리즈를 여기서 닫으며, 앞선 다섯 편이 잡은 개념이 결국 이 운영의 규율 위에서 비로소 안전해진다는 말로 마무리하고 싶습니다.

---

## 참고한 공식 문서

- GitHub Actions — Configuring OpenID Connect in Amazon Web Services — https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services
- Terraform — Sensitive Data in State — https://developer.hashicorp.com/terraform/language/state/sensitive-data
- Terraform — Import — https://developer.hashicorp.com/terraform/language/import
- Terraform — The `lifecycle` Meta-Argument — https://developer.hashicorp.com/terraform/language/meta-arguments/lifecycle
- AWS STS — AssumeRoleWithWebIdentity — https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRoleWithWebIdentity.html
- tflint — https://github.com/terraform-linters/tflint
- Trivy (Aqua Security) — https://github.com/aquasecurity/trivy
- Checkov — https://www.checkov.io/
