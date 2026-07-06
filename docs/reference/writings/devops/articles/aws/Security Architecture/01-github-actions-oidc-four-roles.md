# 장기 자격증명 없는 CI/CD — GitHub Actions OIDC 페더레이션과 역할을 4개로 쪼갠 이유

> 시리즈: security 다이어그램 읽기 — 신원(identity)과 암호화(encryption)의 두 축

---

## SEO 제목 후보

- **GitHub Actions에서 AWS Access Key 없이 배포하기 — OIDC 페더레이션으로 장기 자격증명 지우기** — 저장소 시크릿에 아직 `AWS_ACCESS_KEY_ID`를 넣어 두고 있는 팀의 데브옵스·백엔드 엔지니어에게
- **IAM 역할을 왜 4개로 쪼갤까 — GitHub OIDC 신뢰 정책의 `sub` 클레임으로 배포·Terraform·스캔 권한 나누기** — 배포 역할 하나에 권한을 몰아 둔 구조를 다시 들여다보려는 엔지니어에게
- **`sub` 클레임이 만드는 울타리 — `environment:prod`와 `pull_request`로 워크플로별 AWS 권한을 좁히는 법** — GitHub Actions 신뢰 정책 조건을 정확히 설계하려는 인프라 엔지니어에게

---

## 들어가며 — 자물쇠 아이콘 다섯 개, Access Key는 0개

세 환경(dev·beta·prod)의 security 다이어그램을 나란히 펼쳐 두고 왼쪽 위 "CI/CD trust boundary" 상자만 들여다보면, 자물쇠 아이콘이 다섯 개 그려져 있습니다. OIDC Provider 하나와 IAM Role 네 개입니다. 그런데 이 상자 어디에도 AWS Access Key는 없습니다. GitHub Actions 워크플로가 ECR에 이미지를 밀어 넣고 ECS 서비스를 갱신하고 Terraform을 apply 하는데, 저장소 시크릿에 `AWS_ACCESS_KEY_ID`나 `AWS_SECRET_ACCESS_KEY` 같은 장기 자격증명이 하나도 들어가 있지 않습니다.

처음 이 상자를 봤을 때 든 질문은 두 가지였습니다. 첫째, 키가 없는데 어떻게 GitHub이 AWS 리소스를 건드리는가. 둘째, 배포든 Terraform이든 결국 "CI에서 AWS를 조작한다"는 같은 일인데 왜 역할을 하나로 두지 않고 넷으로 쪼갰는가. 이 글에서는 이 두 질문을, 실제 Terraform 신뢰 정책 코드와 AWS·GitHub 공식 문서를 근거로 따라가 보려 합니다. 결론부터 말하면, 키가 없는 이유는 OIDC 페더레이션 때문이고, 역할을 넷으로 쪼갠 이유는 각 워크플로가 요구하는 권한의 넓이와 신뢰해야 할 트리거가 서로 다르기 때문입니다.

---

## OIDC 페더레이션이 대체하는 것

전통적인 방식은 단순합니다. AWS에서 IAM 사용자를 하나 만들고 액세스 키를 발급받아, 그 키를 GitHub 저장소 시크릿에 복사해 넣습니다. GitHub 공식 문서는 이 방식을 두고 "하드코딩된 시크릿을 쓰려면 클라우드 제공자에서 자격증명을 만든 뒤 그것을 GitHub에 시크릿으로 복제해 두어야 한다"고 설명합니다. 문제는 이 키가 사람이 지우기 전까지 계속 유효하다는 데 있습니다. 저장소를 볼 수 있는 사람, 워크플로 로그를 잘못 남긴 순간, 포크된 저장소의 설정 실수 어느 하나라도 이 장기 키를 노출시키면 그 키는 폐기될 때까지 살아 있습니다.

OIDC 페더레이션은 이 "미리 만들어 저장해 두는 키"를 아예 없앱니다. GitHub 공식 문서의 표현을 그대로 옮기면, "잡이 실행될 때마다 GitHub의 OIDC provider가 OIDC 토큰을 자동으로 발급"합니다. 워크플로는 이 토큰을 AWS에 제시하고, AWS는 토큰을 검증한 뒤 그 잡에서만 쓸 수 있는 단기 자격증명을 돌려줍니다. 문서는 이 자격증명이 "단 하나의 잡에서만 유효하고 그 뒤 자동으로 만료된다"고 못 박습니다. 저장해 둘 키 자체가 존재하지 않으니, 유출될 장기 비밀도 없습니다.

AWS 쪽에서 이 신뢰를 받아들이는 장치가 IAM OIDC 자격 증명 공급자입니다. AWS 공식 문서는 OIDC 페더레이션을 쓰면 "AWS 계정에 IAM 사용자를 만드는 대신, 외부에서 관리하는 사용자 신원에게 AWS 리소스 접근 권한을 줄 수 있다"고 설명합니다. 이 프로젝트의 bootstrap 단계는 `token.actions.githubusercontent.com`을 OIDC Provider로 등록해 두었고, 오디언스는 `sts.amazonaws.com`으로 잡혀 있습니다. 워크플로가 토큰을 들고 오면, IAM 역할의 신뢰 정책이 `sts:AssumeRoleWithWebIdentity` 액션으로 그 토큰을 검증하고 임시 보안 자격증명을 내줍니다. 여기서 "임시"라는 단어가 핵심입니다. 이 자격증명은 역할에 설정된 세션 시간이 지나면 사라지고, 다음 잡은 다시 새 토큰으로 새 자격증명을 받습니다.

정리하면 흐름은 이렇습니다. 워크플로가 뜬다 → GitHub이 그 잡의 정체를 담은 서명된 토큰을 만든다 → 워크플로가 AWS STS에 토큰을 내민다 → AWS가 토큰의 서명과 조건을 확인하고 단기 자격증명을 발급한다 → 잡이 끝나면 자격증명이 만료된다. 이 왕복 어디에도 사람이 미리 만들어 저장해 둔 비밀은 없습니다.

---

## 신뢰 정책이 검사하는 두 값 — `aud`와 `sub`

토큰만 있으면 아무 워크플로나 이 역할을 assume 할 수 있다면, 키를 없앤 의미가 반쯤 사라집니다. 남의 저장소에서 발급된 토큰도 형식만 맞으면 통과할 테니까요. 그래서 신뢰 정책은 토큰 안에 담긴 클레임(claim) 두 개를 조건으로 검사합니다. 오디언스를 뜻하는 `aud`와 주체를 뜻하는 `sub`입니다.

`aud`는 "이 토큰이 누구에게 쓰이라고 발급됐는가"를 나타냅니다. 이 프로젝트의 네 역할은 모두 `token.actions.githubusercontent.com:aud` 값이 `sts.amazonaws.com`인지를 `StringEquals`로 확인합니다. GitHub이 AWS STS를 대상으로 발급한 토큰만 받겠다는 뜻입니다.

진짜 울타리는 `sub`에서 만들어집니다. `sub`는 "이 토큰이 정확히 어떤 워크플로 맥락에서 발급됐는가"를 담습니다. GitHub 공식 문서는 이 값이 "인증을 시도하는 특정 워크플로에 대해, 보안이 강화되고 검증 가능한 신원"을 만든다고 설명하고, 환경 배포의 경우 `repo:octo-org/octo-repo:environment:prod` 같은 형식을 예로 듭니다. 저장소 이름, 트리거 종류, 대상 환경이나 브랜치가 이 문자열 하나에 인코딩돼 있습니다.

이 조건을 얼마나 좁히느냐가 안전의 전부라는 점은 AWS 문서가 특히 강하게 강조합니다. AWS 공식 문서는 "`token.actions.githubusercontent.com:sub` 조건을 특정 조직이나 저장소로 제한하지 않으면, 통제 범위 밖의 조직·저장소에서 실행되는 GitHub Actions도 이 역할을 assume 할 수 있다"고 경고합니다. 더 나아가, GitHub OIDC Provider를 신뢰하는 역할에 대해서는 IAM이 신뢰 정책을 만들거나 수정하는 시점에 `sub` 조건이 존재하는지, 그리고 그 값이 순수한 와일드카드(`*`, `?`)나 null이 아닌지를 검사하고, 조건이 없거나 기준을 못 채우면 요청을 실패시킨다고 명시합니다. 즉 "OIDC를 켰다"는 사실만으로 안전해지는 게 아니라, `sub`를 제대로 좁히는 일 자체가 이 방식의 본체입니다. 이 대목을 읽고 나면 "OIDC라 안전하다"는 요약이 얼마나 성긴 말인지 알게 됩니다. 안전한 것은 OIDC가 아니라, 잘 좁혀진 `sub` 조건입니다.

---

## 왜 역할 하나가 아니라 넷인가

키를 없애고 `sub`를 좁히는 데까지 왔다면, 역할 하나로도 CI/CD를 돌릴 수는 있습니다. 그런데 이 프로젝트는 CI/CD 신뢰 경계 안에 역할을 넷 두었습니다. `gha-deploy-{env}`, `gha-terraform-apply-{env}`, `gha-terraform-readonly`, `gha-security-scan`입니다. 이 넷이 나뉘는 기준을 하나씩 보면, 그냥 취향으로 쪼갠 게 아니라 서로 다른 축의 결정이 겹쳐 있다는 게 드러납니다.

첫 번째 축은 쓰기와 읽기입니다. Terraform 워크플로는 두 역할로 갈립니다. `gha-terraform-readonly`는 PR이 올라왔을 때 `terraform plan`을 돌려 변경 예정 내용을 읽기만 하면 되고, `gha-terraform-apply-{env}`는 실제로 인프라를 바꾸는 apply를 실행합니다. 읽기와 쓰기는 요구하는 권한의 넓이가 완전히 다릅니다. 그래서 readonly 역할에는 AWS 관리형 `ReadOnlyAccess` 정책과 tfstate 읽기 권한만 붙어 있고, apply 역할에는 `PowerUserAccess`에 IAM 관리 권한을 더한 넓은 권한과 tfstate 읽기·쓰기 권한이 붙어 있습니다.

두 번째 축은 환경별과 전역입니다. 배포와 apply는 환경 이름이 역할 이름에 박혀 있습니다(`gha-deploy-prod`, `gha-terraform-apply-prod`처럼요). prod를 건드리는 자격증명과 dev를 건드리는 자격증명이 애초에 다른 역할로 분리돼 있다는 뜻입니다. 반면 보안 스캔은 환경과 무관하게 main 브랜치 기준으로 한 번 돌면 되고, PR plan도 특정 환경에 매이지 않으므로 전역 역할 하나로 둡니다.

세 번째 축은 작업의 성격 자체입니다. `gha-deploy-{env}`가 하는 일은 애플리케이션을 배포하는 것입니다. ECR에 이미지를 밀어 넣고(`techai/*` 리포로 한정), ECS 서비스를 업데이트하고, 태스크 정의를 등록하고, CodeDeploy 배포를 트리거하고, 프런트엔드를 위해 Amplify 잡을 시작하고, 이미지 서명을 위해 Signer를 호출합니다. `gha-security-scan`이 하는 일은 전혀 다릅니다. ECR 이미지를 describe·pull 하고 Amazon Inspector의 스캔 결과(`inspector2:ListFindings`, `GetFinding`, `ListCoverage`)를 읽습니다. 배포하는 역할과 취약점 스캔 결과를 읽는 역할이 같은 자격증명을 공유할 이유는 없습니다.

이렇게 쓰기/읽기, 환경별/전역, 배포/스캔이라는 세 기준이 포개지면서 자연스럽게 네 개의 역할로 갈라집니다. 하나로 합쳤다면 "PR plan을 위한 읽기 권한"과 "prod에 실제로 apply 하는 쓰기 권한"이 같은 자격증명 안에 뭉쳐 있었을 텐데, 이 프로젝트는 그 둘을 처음부터 다른 역할로 떼어 놓았습니다.

---

## `sub` 클레임이 만드는 울타리 — 정확 일치와 패턴 일치

역할을 넷으로 나눴다는 것보다 더 흥미로운 부분은, 각 역할의 신뢰 정책이 서로 다른 방식으로 `sub`를 검사한다는 점입니다. 같은 `sub` 조건인데 어떤 역할은 정확 일치(`StringEquals`)를, 어떤 역할은 패턴 일치(`StringLike`)를 씁니다. 이 선택이 "이 역할을 누가 assume 할 수 있는가"의 넓이를 결정합니다.

배포 역할 `gha-deploy-{env}`는 `sub`를 `repo:{org}/{repo}:environment:{env}` 값과 `StringEquals`로 비교합니다. 정확히 그 저장소의, 정확히 그 환경 배포에서 나온 토큰만 통과합니다. AWS 문서가 예시로 드는 `repo:octo-org/octo-repo:environment:prod` 형식이 그대로 쓰인 셈입니다. Terraform apply 역할도 마찬가지로 정확 일치를 쓰되, 값이 `environment:tf-{env}`입니다. 여기서 눈여겨볼 점은 배포용 환경(`environment:{env}`)과 Terraform용 환경(`environment:tf-{env}`)을 GitHub 환경 이름 단계에서부터 분리했다는 것입니다. GitHub 환경은 보호 규칙(승인자 지정, 배포 가능 브랜치 제한 등)을 걸 수 있는 단위인데, 배포와 인프라 변경을 서로 다른 환경 이름에 걸어 두면 각각에 다른 보호 규칙과 승인 절차를 적용할 수 있습니다. AWS 문서도 GitHub 환경을 OIDC 정책에 쓸 때는 보호 규칙을 함께 두라고 권합니다.

반면 readonly와 scan 역할은 패턴 일치를 씁니다. `gha-terraform-readonly`의 `sub` 조건은 `StringLike`로 `repo:{org}/{repo}:pull_request`와 `repo:{org}/{repo}:pull_request:*` 두 값을 허용합니다. PR에서 트리거된 plan 워크플로는 특정 환경에 매이지 않으므로, 정확한 환경 문자열 대신 "이 저장소의 pull_request 맥락"이라는 패턴으로 받는 것입니다. `gha-security-scan`도 `StringLike`로 `repo:{org}/{repo}:ref:refs/heads/main`을 검사합니다. main 브랜치에서 도는 워크플로만 이 역할을 assume 할 수 있다는 뜻입니다. 이 `ref:refs/heads/...` 형식은 AWS와 GitHub 문서 양쪽이 브랜치 기준 신뢰 정책의 예로 드는 바로 그 형식입니다.

여기서 짚어 둘 것은, 패턴 일치를 쓴다고 울타리가 사라지는 게 아니라는 점입니다. `pull_request:*`처럼 뒤에 와일드카드가 붙어 있어도 앞의 `repo:{org}/{repo}:` 접두어가 고정돼 있어서, 통제 범위 밖 저장소의 토큰은 애초에 걸러집니다. 앞서 본 AWS의 secure-by-default 검사가 "순수한 와일드카드"만 거부하는 것도 같은 맥락입니다. 저장소를 특정하는 접두어가 살아 있는 한, 뒤쪽을 패턴으로 여는 것은 허용됩니다. 정확 일치는 배포·apply처럼 대상이 하나로 특정되는 작업에, 패턴 일치는 PR이나 브랜치처럼 여러 실행을 아우르되 경계는 유지해야 하는 작업에 각각 어울립니다.

권한 정책 쪽에도 이 "필요한 만큼만"의 태도가 이어집니다. 배포 역할의 `iam:PassRole`은 아무 역할이나 넘길 수 있는 게 아니라 `{project}-*-task-*`와 `{project}-*-task-execution-*` 패턴의 역할로 한정되고, 그마저도 `iam:PassedToService`가 `ecs-tasks.amazonaws.com`일 때만 허용됩니다. 배포 역할이 ECS 태스크에 태스크 역할을 넘기는 것 외의 용도로 PassRole을 남용하지 못하도록 조건으로 묶어 둔 것입니다. ECR 푸시 권한도 계정 전체가 아니라 `techai/*` 리포로 좁혀져 있습니다.

---

## 쪼갠 것의 비용과 정직한 트레이드오프

역할을 넷으로 나누면 관리할 대상이 늘어납니다. 신뢰 정책도 넷, 권한 정책도 넷, GitHub 환경 설정도 그만큼 늘어나고, 워크플로마다 어떤 역할을 assume 할지 정확히 지정해 줘야 합니다. 이 복잡도는 분명한 비용입니다.

그 비용을 지불하고 얻는 것은 사고가 났을 때 노출되는 범위를 좁히는 일입니다. 만약 배포 역할 하나에 apply 권한까지 몰아 두었다면, PR을 여는 것만으로 트리거되는 워크플로 어딘가에 문제가 생겼을 때 그 자격증명이 prod 인프라를 바꿀 힘까지 쥐고 있었을 것입니다. 지금 구조에서는 PR plan이 assume 하는 것은 읽기 전용 역할이라, 그 경로가 잘못 흘러도 인프라를 바꾸지는 못합니다. 각 자격증명이 그 잡에서만 유효하고 곧 만료된다는 OIDC의 성질 위에, "그 잡이 애초에 할 수 있는 일의 범위"까지 역할 단위로 좁혀 둔 셈입니다.

다만 이 설계를 완벽한 최소 권한이라고 말하기는 어렵습니다. readonly 역할이 붙이고 있는 `ReadOnlyAccess`와 apply 역할의 `PowerUserAccess`는 모두 AWS 관리형 정책으로, 개별 리소스 단위로 잘게 좁힌 것이 아니라 상당히 넓은 범위를 한 번에 허용합니다. `terraform plan`이 어떤 리소스를 읽어야 할지 미리 다 알기 어렵기 때문에 read 전반을 열어 두는 선택은 현실적이지만, "읽기 전용이니 안전하다"와 "이 역할이 읽을 수 있는 것이 정확히 무엇인가"는 다른 질문입니다. 이 부분은 넓은 관리형 정책에 기댄 트레이드오프이고, 접근 경계는 관리형 정책의 범위 그 자체에 묶여 있다고 정직하게 보는 편이 맞습니다. 역할을 쪼갠 것과, 각 역할의 권한을 리소스 단위까지 좁히는 것은 서로 다른 작업이고, 이 설계는 전자를 확실히 해 두고 후자는 관리형 정책 수준에서 절충한 상태입니다.

---

## 마무리하며

이 조사를 하면서 개인적으로 남은 인상은, "키를 없앴다"는 문장과 "안전해졌다"는 문장 사이에 생각보다 긴 거리가 있다는 것이었습니다. OIDC 페더레이션은 장기 자격증명이라는 큰 위험을 없애 주지만, 그 자리를 채우는 것은 신뢰 정책의 조건들입니다. `aud`를 `sts.amazonaws.com`으로 고정하고, `sub`를 저장소·트리거·환경까지 좁히고, 정확 일치와 패턴 일치를 작업 성격에 맞게 고르고, 역할마다 권한의 넓이를 다르게 두는 이 모든 결정이 모여서야 비로소 "안전한 CI/CD"에 가까워집니다. AWS가 `sub` 조건을 강제로 검사해 순수 와일드카드를 거부하는 것도, 결국 이 조건 설정이 이 방식의 알맹이라는 점을 도구 차원에서 인정한 것으로 읽혔습니다.

역할을 넷으로 쪼갠 결정도 같은 결에서 이해했습니다. 이것은 CI/CD를 복잡하게 만들려는 선택이 아니라, "PR에서 읽는 일"과 "prod에 쓰는 일"이 같은 힘을 가져서는 안 된다는 판단을 자격증명 구조 자체로 강제한 것에 가깝습니다. 완벽한 최소 권한까지 간 것은 아니지만, 적어도 자격증명 하나가 새어도 그 피해가 계정 전체가 아니라 그 역할이 감당하는 좁은 범위에 머무르도록 경계를 그어 두었습니다.

배포 시점에만 잠깐 assume 되는 이 네 역할이 CI/CD의 신뢰 경계를 지킨다면, 정작 컨테이너가 떠서 요청을 처리하는 동안 상시 쓰이는 권한은 또 다른 경계에서 다뤄집니다. 실행 중인 각 서비스가 어떤 시크릿과 데이터에 닿을 수 있는지를 결정하는 ECS 태스크 역할이 그것인데, 같은 최소 권한 원칙이 이번에는 "배포 시점"이 아니라 "실행 시점"에 어떻게 새겨지는지는 다음 글에서 이어 보려 합니다.

---

## 참고한 공식 문서

- IAM과 OpenID Connect 페더레이션을 위한 역할 생성: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-idp_oidc.html
- GitHub Actions — OpenID Connect를 통한 보안 강화 개요: https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect
- GitHub Actions — Amazon Web Services에서 OpenID Connect 구성: https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services
- AWS STS — AssumeRoleWithWebIdentity: https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRoleWithWebIdentity.html
