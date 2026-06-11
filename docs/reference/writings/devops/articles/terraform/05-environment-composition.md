# 환경 조립 — 변수 한두 개로 dev/beta/prod를 가른다

> 시리즈: Terraform 입문 다이어리 — Java 개발자가 IaC를 배우며 정리한 것들

---

## SEO 제목 후보

- **Terraform으로 dev/beta/prod를 한 벌의 모듈로 만드는 법 — tfvars 분기 패턴** — 환경마다 코드를 복붙하지 않고 재사용하는 구조를 찾는 입문자에게
- **Terraform Workspace를 환경 분리에 쓰면 안 되는 이유 — 공식 문서가 말하는 한계** — `terraform workspace`로 환경을 나눠도 되는지 고민하는 데브옵스 엔지니어에게
- **Spring의 application-prod.yml과 Terraform tfvars는 어디까지 같고 어디서 갈라지나** — 프로파일 분리에 익숙한 Java 개발자가 IaC 환경 분리를 처음 접할 때

---

## 들어가며 — dev에 prod와 똑같은 돈을 쓸 수는 없다

인프라를 코드로 짜기 시작하면 곧장 마주치는 모순이 하나 있습니다. dev와 prod는 같은 설계여야 하지만 같은 비용일 수는 없다는 점입니다. 개발 환경에서 운영처럼 가용 영역마다 NAT Gateway를 따로 띄우고, Aurora를 Multi-AZ로 굴리고, Kafka 브로커를 세 대씩 세우면 설계의 동질성은 얻지만 매달 나가는 비용이 감당이 안 됩니다. 반대로 dev를 너무 단출하게 만들면 "dev에서는 됐는데 prod에서 안 되는" 환경 차이가 사고로 돌아옵니다.

이 모순을 푸는 가장 쉬운 길은 사실 가장 나쁜 길이기도 합니다. dev용 코드와 prod용 코드를 따로 복사해 두고 각자 손보는 것입니다. 처음엔 빠르지만, 시간이 지나면 두 벌의 코드가 조금씩 어긋나기 시작합니다. dev에만 적용한 보안 설정이 prod에는 빠지고, prod에서 고친 버그가 dev에는 반영되지 않습니다. 복붙은 환경 차이를 푸는 게 아니라 환경 차이를 방치하는 방법입니다.

Terraform이 권하는 방향은 다릅니다. 설계는 모듈 한 벌에 담아 공유하고, 환경별로 다른 것은 변수 값으로만 갈라 두는 것입니다. 같은 `modules/network`를 dev도 호출하고 prod도 호출하되, NAT를 한 개 만들지 세 개 만들지는 변수 하나로 결정합니다. 이렇게 해 두면 환경 사이의 진짜 차이가 변수 파일 한 곳에 모이고, 나머지 설계는 자동으로 같아집니다. 이 글에서는 이 저장소가 dev·beta·prod를 어떻게 한 벌의 모듈로 조립하는지, 그 차이가 어디에 모이는지, 그리고 환경을 나눌 때 흔히 손이 가는 Workspace를 왜 쓰지 않았는지를 실제 코드를 보며 정리해 보려 합니다. 모듈을 만드는 법과 state 개념은 이 시리즈의 앞 글들에서 다뤘으니 여기서는 전제로 두겠습니다.

---

## envs는 조립만 한다 — 레이어를 나누는 이유

먼저 디렉터리 구조에서 출발하겠습니다. 이 저장소의 Terraform 코드는 크게 세 층으로 나뉩니다. `modules/*`는 설계를 담는 층입니다. network, aurora-mysql, ecs-service처럼 "이 자원을 이렇게 만든다"는 방법이 여기 들어 있습니다. `envs/*`는 그 모듈들을 환경별로 불러다 조립하는 층입니다. `envs/dev`, `envs/beta`, `envs/prod`가 각각 한 환경을 담당합니다. 그리고 `bootstrap/*`는 state를 담을 S3 버킷처럼 Terraform 자신이 돌아가기 위한 바닥 인프라를 만드는 층입니다.

여기서 중요한 규칙이 하나 있습니다. `envs/*`는 자원을 직접 만들기보다 모듈을 호출하는 데 집중한다는 것입니다. `envs/dev/main.tf`를 열어 보면 대부분이 `module "network"`, `module "aurora"`처럼 모듈을 부르는 호출문입니다. 자원의 세부는 모듈 안에 있고, envs는 "어느 모듈을 어떤 값으로 부를지"만 정합니다.

Java 개발자라면 이 구도가 낯설지 않습니다. 클래스(설계)는 따로 정의해 두고, `main()`이나 설정 클래스가 그 객체들을 엮어 애플리케이션을 조립하는 모습과 결이 같습니다. envs는 인프라의 조립 지점, 즉 의존성을 모아 객체 그래프를 완성하는 자리에 가깝습니다. 조립하는 자리에서 비즈니스 로직을 직접 짜지 않듯이, envs에서도 자원을 직접 선언하기보다 모듈을 불러 쓰는 편이 층의 책임을 깔끔하게 지킵니다.

물론 예외는 있습니다. 그 환경에서만 쓰는 KMS 키나 Route53 레코드처럼 모듈로 빼기엔 환경 고유성이 강한 자원은 envs에 직접 선언하기도 합니다. 실제로 `envs/dev/main.tf`의 맨 위에는 dev 전용 KMS 키가 모듈 호출이 아니라 `resource` 블록으로 직접 적혀 있습니다. 규칙은 "envs는 조립만 한다"이지만, 환경에 단단히 묶인 소수의 자원까지 억지로 모듈로 만들지는 않는다는 현실적인 타협이 함께 있습니다.

---

## 같은 모듈, 다른 변수 — 차이가 tfvars에 모인다

세 환경의 `main.tf`는 거의 같습니다. 같은 모듈을 같은 순서로 부릅니다. 그렇다면 dev와 prod의 차이는 어디로 갔을까요. `terraform.tfvars`로 모였습니다. 이 파일은 같은 모듈에 넘길 값을 환경별로 다르게 적어 두는 곳이고, 환경 사이의 진짜 차이가 여기 한곳에 응축됩니다. Terraform 공식 문서도 `.tfvars` 파일을 루트 모듈 변수에 값을 할당하는 표준 방법으로 안내합니다.

이 저장소의 beta와 prod tfvars에서 실제 값 몇 개를 나란히 놓으면 차이가 한눈에 보입니다.

| 항목 | dev | beta | prod |
|---|---|---|---|
| VPC CIDR | `10.10.0.0/16` | `10.20.0.0/16` | `10.30.0.0/16` |
| `single_nat_gateway` | (기본값 `true`) | `true` | `false` |
| Aurora 엔진 | serverlessv2 | serverlessv2 (max 4.0 ACU) | provisioned (`db.r7g.large` × 3) |
| Aurora 백업 보관 | — | 7일 | 30일 |
| `cache_multi_az_enabled` | — | `true` | `true` |
| MSK | Serverless(기본 비활성) | Serverless | Provisioned (브로커 3) |
| ECS max task | — | 4 | 6 |

표로 잠깐 펼쳤지만, 핵심은 단순합니다. 설계(어떤 모듈을 부르는가)는 세 환경이 같고, 규모와 비용(그 모듈에 어떤 값을 넘기는가)만 tfvars에서 갈립니다. dev는 비용을 줄이려 NAT를 하나만 두고 Aurora도 가벼운 serverless로 띄우는 반면, prod는 가용 영역마다 NAT를 따로 두고 Aurora를 provisioned로 세 대 굴립니다. 같은 코드가 값만 바꿔 두 얼굴을 갖는 것입니다.

여기서 Java 개발자에게 익숙한 비유가 자연스럽게 떠오릅니다. Spring의 `application-dev.yml`과 `application-prod.yml`입니다. 같은 애플리케이션 코드를 두고 프로파일별 yml에서 커넥션 풀 크기나 로그 레벨을 다르게 주는 그 방식과, tfvars로 환경별 값을 다르게 주는 방식은 출발점이 같습니다. 이 비유는 처음 이해를 잡는 데 꽤 도움이 됩니다. 다만 이 비유는 뒤에서 일부러 깨야 하는 지점이 있는데, 그 이야기는 잠시 미루겠습니다.

---

## 환경별 분기의 실제 — NAT 하나가 토폴로지를 가른다

추상적으로 "변수로 갈린다"고만 말하면 와닿지 않으니, NAT Gateway 하나를 끝까지 따라가 보겠습니다. 세 환경의 tfvars에서 `single_nat_gateway`라는 변수가 dev는 사실상 `true`, beta는 `true`, prod는 `false`로 갈립니다. 이 불리언 하나가 네트워크 토폴로지를 통째로 바꿉니다.

분기의 실제 로직은 `modules/network`에 있습니다. 모듈 안에서 NAT를 몇 개 만들지는 다음 한 줄이 결정합니다.

```hcl
nat_count = var.enable_nat_gateway ? (var.single_nat_gateway ? 1 : length(var.azs)) : 0
```

읽어 보면 의도가 분명합니다. NAT를 아예 끄면 0개, 켜되 단일 NAT면 1개, 켜고 단일 NAT가 아니면 가용 영역 수만큼 만듭니다. dev와 beta는 `single_nat_gateway`가 `true`라 NAT가 한 개만 생기고, 세 가용 영역이 그 하나를 공유합니다. 비용은 아끼지만 그 NAT가 있는 가용 영역이 통째로 죽으면 나머지 영역의 바깥 통신도 함께 끊깁니다. 반면 prod는 `false`라 가용 영역마다 NAT를 따로 두어 세 개가 생기고, 한 영역의 장애가 다른 영역으로 번지지 않습니다. 변수 하나가 "비용을 아낄 것이냐, 영역 격리를 살 것이냐"라는 운영 트레이드오프를 가르는 셈입니다.

한 가지는 정확히 짚어 두겠습니다. dev의 `terraform.tfvars`에는 `single_nat_gateway`가 아예 적혀 있지 않습니다. 값을 명시하는 대신 변수의 기본값에 기댑니다. `envs/dev/variables.tf`를 보면 이 변수의 `default`가 `true`로 잡혀 있어서, dev tfvars가 침묵해도 결과적으로 단일 NAT가 됩니다. 그래서 "dev는 NAT가 하나"라고 말할 때는 "tfvars에 적어서가 아니라 기본값이 `true`라서"라는 단서를 붙이는 게 정확합니다. beta와 prod만 이 값을 tfvars에 또렷이 적어 두고, dev는 기본값에 맡긴 것입니다. 환경 차이를 읽을 때 명시값과 기본값을 구분하지 않으면 코드를 오해하기 쉬운 지점이라, 일부러 분명히 적어 둡니다.

---

## count 토글 — 자원을 환경별로 켜고 끈다

환경 차이는 "값을 다르게 주는 것"만이 아닙니다. 어떤 자원은 특정 환경에서 아예 만들지 말아야 합니다. 이때 쓰는 것이 `count` 토글입니다. Terraform에서 `count = 0`이면 그 자원이나 모듈은 생성되지 않고, `count = 1`이면 만들어집니다. 이 성질을 삼항 연산과 묶으면 변수로 자원을 켜고 끄는 스위치가 됩니다.

`envs/dev/main.tf`의 Aurora 호출이 정확히 이 패턴입니다.

```hcl
module "aurora" {
  count  = var.enable_aurora ? 1 : 0
  source = "../../modules/aurora-mysql"
  # ...
}
```

`enable_aurora`가 참이면 Aurora 모듈이 조립되고, 거짓이면 통째로 빠집니다. 인프라를 처음 깔아 나가는 초기 단계에서는 데이터베이스를 아직 띄우지 않고 네트워크만 먼저 세우고 싶을 때가 있는데, 이럴 때 변수 하나를 꺼 두면 Aurora만 빼고 나머지를 만들 수 있습니다. 나중에 준비가 되면 그 변수를 켜서 같은 코드로 데이터베이스를 추가합니다.

같은 토글이 자원의 종류를 고르는 데에도 쓰입니다. 이 저장소는 Kafka를 dev·beta에서는 MSK Serverless로, prod에서는 MSK Provisioned로 운영합니다. 둘은 별개의 모듈인데, 두 모듈에 조건을 엇갈리게 걸어 한쪽만 켜지도록 만들어 두었습니다.

```hcl
module "msk" {
  count = var.enable_msk && !var.use_msk_provisioned ? 1 : 0
  # ... MSK Serverless
}

module "msk_provisioned" {
  count = var.enable_msk && var.use_msk_provisioned ? 1 : 0
  # ... MSK Provisioned
}
```

`use_msk_provisioned`가 거짓인 dev·beta에서는 위쪽 Serverless 모듈만 켜지고, 참인 prod에서는 아래쪽 Provisioned 모듈만 켜집니다. 두 모듈을 모두 코드에 적어 두되 환경에 따라 한쪽만 살아나게 하는 방식입니다. 같은 "Kafka를 쓴다"는 설계 의도를 공유하면서, 환경별로 다른 구현을 고르는 분기를 변수 두 개로 표현한 것입니다.

---

## Workspace는 왜 권장하지 않나

여기까지 보면 한 가지 의문이 듭니다. Terraform에는 `terraform workspace`라는 기능이 있어서 같은 코드로 여러 상태를 나눠 가질 수 있는데, dev·beta·prod를 이 Workspace로 나누면 더 간단하지 않을까요. 디렉터리를 세 벌 두는 대신 워크스페이스만 바꿔 가며 쓰면 될 것 같습니다.

이 저장소는 그 길을 택하지 않았고, 그 판단의 근거는 HashiCorp 공식 문서에 있습니다. Workspace 문서는 "워크스페이스는 시스템 분해나, 별도의 자격증명과 접근 제어가 필요한 배포에는 적합하지 않다(Workspaces are not appropriate for system decomposition or deployments requiring separate credentials and access controls)"고 분명히 적고 있습니다. dev·beta·prod를 별도 AWS 계정으로 나누고 환경마다 다른 접근 권한을 거는 구성은, 바로 이 문장이 가리키는 "별도 자격증명과 접근 제어가 필요한 배포"에 해당합니다. 따라서 환경 분리에는 Workspace가 맞지 않습니다.

이유를 좀 더 풀면 이렇습니다. Workspace로 환경을 나누면 같은 디렉터리에서 워크스페이스 이름만 바꿔 가며 작업하게 되는데, 지금 어느 워크스페이스에 있는지를 사람이 매번 의식해야 합니다. `workspace select`를 잘못 눌러 prod 워크스페이스에서 dev인 줄 알고 apply하는 사고가 구조적으로 가능해집니다. 워크스페이스는 화면 어딘가의 작은 표시일 뿐, 디렉터리처럼 눈에 띄는 경계가 아니기 때문입니다.

이 저장소가 택한 별도 디렉터리 + 별도 state + 별도 계정 방식은 이 위험을 구조로 막습니다. `envs/prod`라는 디렉터리에 들어가 있다는 사실 자체가 "지금 prod를 만지고 있다"는 또렷한 맥락이 됩니다. state도 환경별로 따로 떨어져 있어 한 환경의 작업이 다른 환경의 장부에 닿지 못하고, 계정까지 갈라져 있어 한 환경의 사고가 다른 환경으로 번질 길이 애초에 끊깁니다. Workspace를 "절대 쓰지 마라"는 게 아니라, 강한 격리가 필요한 환경 분리라는 용도에는 맞지 않는다는 정도가 정확한 정리입니다. 같은 환경 안에서 잠깐 분기를 시험해 보는 가벼운 용도라면 Workspace도 제 쓸모가 있습니다.

---

## 프로파일 비유의 한계 — 어디서 깨지나

앞에서 tfvars를 Spring의 `application-{env}.yml`에 비유했습니다. 이 비유는 시작점으로는 훌륭하지만, 끝까지 밀면 반드시 어긋나는 지점이 있어서 그 한계를 분명히 해 두어야 합니다. 이 한계를 짚지 않으면 "결국 환경별 yml 한 장과 같은 거네"라고 가볍게 넘겨 버리기 쉽습니다.

가장 큰 차이는 격리의 깊이입니다. Spring 프로파일은 같은 프로세스, 같은 애플리케이션 안에서의 설정 차이입니다. dev 프로파일로 뜨든 prod 프로파일로 뜨든 코드도 한 벌이고 실행 주체도 한 덩어리입니다. 반면 Terraform의 환경 분리는 설정값만 다른 게 아니라 state 파일이 갈리고 AWS 계정까지 갈립니다. dev의 apply가 참조하는 장부와 prod의 장부는 물리적으로 다른 파일이고, 둘은 다른 계정에 살면서 다른 권한으로 보호됩니다. 프로파일이 "한 집 안의 방을 나눈 것"이라면, Terraform 환경은 "아예 다른 집"에 가깝습니다. 그래서 한 환경에서 무언가 크게 잘못되어도 그 여파가 다른 환경의 자원에 닿지 못합니다. 이 격리는 yml 프로파일이 제공하지 않는 종류의 안전입니다.

또 하나의 차이는 변수로 담을 수 없는 환경 고유 자원의 존재입니다. yml은 어디까지나 값을 바꾸는 수단이라, 값이 아니라 자원 자체가 환경마다 다르면 표현하기 어렵습니다. Terraform에서는 환경에만 존재하는 KMS 키나 그 환경의 도메인에 묶인 Route53 레코드처럼, tfvars로는 담기 힘든 환경 고유 자원이 생깁니다. 이런 자원은 앞에서 본 레이어 규칙의 예외로 envs 디렉터리에 직접 선언합니다. 환경 차이의 대부분은 tfvars 값으로 모이지만, 값으로 환원되지 않는 소수의 차이는 그 환경의 디렉터리 안에 자원으로 직접 남는 것입니다.

정리하면 프로파일 비유는 "환경별로 다른 값을 한곳에 모은다"는 핵심을 잡는 데까지는 유효하고, "환경이 state와 계정 수준에서 격리된다"는 지점에서 깨집니다. 비유를 주되 깨지는 자리를 분명히 하는 것이, 이 개념을 정직하게 옮기는 방법이라고 생각합니다.

---

## 마무리 — 좋은 환경 분리는 diff가 tfvars에 모인다

환경을 조립하는 패턴을 정리하면서 가장 크게 남은 인상은, 좋은 환경 분리일수록 환경 사이의 차이가 한곳으로 모인다는 점이었습니다. dev와 prod를 비교하고 싶을 때 봐야 할 곳이 main.tf 수십 줄이 아니라 tfvars 한 장이면, 두 환경이 정확히 무엇이 같고 무엇이 다른지가 즉시 읽힙니다. 설계는 모듈에서 공유되어 자동으로 같아지고, 차이는 변수 파일에 응축됩니다. 환경 사이의 diff가 tfvars에 모이는 구조 자체가, 환경 차이를 관리 가능한 크기로 묶어 두는 장치였습니다.

Java를 하던 사람으로서 이 패턴은 처음에 프로파일 yml로 이해를 잡았고, 한참 지나서야 그 비유가 닿지 않는 자리를 알게 되었습니다. tfvars는 yml처럼 환경별 값을 모으지만, 그 환경은 yml과 달리 state와 계정 수준에서 격리되어 한 환경의 사고가 다른 환경으로 번지지 않습니다. 처음엔 이 격리가 과하게 느껴졌는데, Workspace로 환경을 나눴을 때 생길 수 있는 `select` 오인 사고를 떠올려 보면 디렉터리와 계정으로 경계를 못 박아 두는 편이 사람을 덜 믿어도 되는 안전한 설계라는 생각이 들었습니다.

결국 환경 조립이라는 주제는 "어떻게 코드를 덜 복사하면서 환경을 다르게 만들 것인가"라는 질문이었고, 답은 같은 모듈을 변수로 다르게 부르는 것이었습니다. 복붙으로 두 벌을 유지하는 대신 한 벌을 공유하고 차이만 tfvars로 가르면, 환경이 늘어나도 설계는 한곳에 남습니다. 이 시리즈의 다음 편에서는 이렇게 조립한 환경들을 팀에서 안전하게 운영하는 이야기 — 누가 어느 환경에 apply할 수 있는지, 품질 게이트와 승인을 어떻게 거는지 — 로 이어 가 보려 합니다. 비슷한 자리에서 환경 분리를 처음 설계하는 분께, "차이를 tfvars 한곳에 모으는 것"이라는 이 글의 정리가 작은 출발점이 된다면 좋겠습니다.

---

## 참고한 공식 문서

- Calling Modules / Module Sources: https://developer.hashicorp.com/terraform/language/modules/sources
- Assigning Values to Variables (`.tfvars`): https://developer.hashicorp.com/terraform/language/values/variables#assigning-values-to-root-module-variables
- Workspaces: https://developer.hashicorp.com/terraform/language/state/workspaces
- The count Meta-Argument: https://developer.hashicorp.com/terraform/language/meta-arguments/count
- Terraform Recommended Practices: https://developer.hashicorp.com/terraform/cloud-docs/recommended-practices
- AWS Prescriptive Guidance — Terraform AWS Provider Best Practices: https://docs.aws.amazon.com/prescriptive-guidance/latest/terraform-aws-provider-best-practices/
