# HCL은 작은 언어입니다 — 블록 다섯 종과 반복 세 가지로 모듈을 읽기

> 시리즈: Terraform 입문 — Java 개발자가 데브옵스로 넘어갈 때 (3편)

---

## SEO 제목 후보

- **HCL은 작은 언어입니다 — 블록 다섯 종과 반복 세 가지로 모듈을 읽기** — Terraform 문법을 처음부터 정리하고 싶은 입문 독자에게
- **Terraform HCL 문법 핵심: resource·variable·output·local·data 한 번에 잡기** — 블록별 역할을 레퍼런스로 빠르게 확인하려는 독자에게
- **count와 for_each, 언제 무엇을 쓰나 — Java 개발자를 위한 HCL 입문** — 반복 메타 인자와 `dynamic` 블록 선택 기준을 검색하는 독자에게

---

## 들어가며 — 외울 키워드가 적은 언어

Terraform을 처음 열어 보면 낯선 중괄호 덩어리에 먼저 눈이 갑니다. 그런데 한 주쯤 모듈 코드를 읽다 보면 의외로 빨리 익숙해집니다. HCL(HashiCorp Configuration Language)이 작은 언어이기 때문입니다. 외워야 할 키워드가 많지 않습니다. 핵심 블록은 다섯 종이고, "여러 개를 만드는" 반복은 세 가지뿐입니다. 이 여덟 개의 쓰임만 손에 익으면, 이 저장소의 거의 모든 모듈이 그냥 읽힙니다.

이 글은 그 여덟 개를 실제 코드로 한 바퀴 도는 글입니다. 가짜 예제를 새로 짓기보다, 이 프로젝트의 `devops/terraform/` 안에 이미 들어 있는 모듈 코드를 그대로 가져와 설명합니다. 앞선 1편(선언형 사고)과 2편(state)에서 잡은 멘탈 모델은 전제로 두고, 여기서는 "그래서 코드가 어떻게 생겼나"에 답합니다.

Java 미들 개발자로 일하다 데브옵스로 넘어온 분이라면, 블록을 메서드의 구성 요소에 대응시키는 방식이 가장 빠릅니다. `variable`은 메서드 파라미터, `output`은 반환값, `local`은 메서드 안의 지역 변수, `resource`는 무언가를 만드는 부수효과, `data`는 읽기 전용 조회입니다. 이 대응표를 머리 한쪽에 띄워 두고 읽으면 길을 잃지 않습니다. 다만 한 가지는 계속 떠올려야 합니다. HCL에는 "위에서 아래로 실행되는 순서"가 없습니다. 자원 사이의 의존 관계로 그래프가 짜이고, Terraform이 그 그래프를 보고 순서를 정합니다. 이 점은 1편에서 다룬 선언형 사고의 연장입니다.

---

## resource — 만들고 싶은 것 하나

`resource`는 HCL의 중심입니다. "이런 자원이 있어야 한다"고 선언하면, Terraform이 실제로 그 자원을 만들고 state로 추적합니다. 공식 문서는 리소스 블록을 두고 "VPC, 컴퓨트 인스턴스, DNS 레코드 같은 하나 이상의 인프라 객체를 기술한다"고 설명합니다.

이 저장소의 S3 버킷 모듈에서 가장 기본이 되는 선언은 이렇게 생겼습니다.

```hcl
resource "aws_s3_bucket" "this" {
  bucket              = var.bucket_name
  object_lock_enabled = var.object_lock_enabled
  force_destroy       = var.force_destroy

  tags = local.common_tags
}
```

블록의 첫 줄에 두 개의 문자열이 붙습니다. 앞의 `"aws_s3_bucket"`은 자원의 타입입니다. AWS Provider가 제공하는 타입 이름이고, 이 이름으로 Terraform은 어떤 AWS API를 호출할지 압니다. 뒤의 `"this"`는 그 자원에 붙이는 로컬 이름입니다. 같은 코드 안에서 이 버킷을 가리킬 때 `aws_s3_bucket.this`라고 쓰면 됩니다. 실제로 같은 모듈의 다른 자원들이 이 버킷의 ID를 참조할 때 `aws_s3_bucket.this.id`라고 적습니다. 이 참조가 곧 의존 관계가 되고, Terraform은 버킷을 먼저 만든 뒤에 버전 관리 설정을 붙입니다. 우리가 순서를 적지 않아도, 참조가 순서를 만들어 냅니다.

중괄호 안의 `bucket`, `object_lock_enabled` 같은 줄은 그 자원의 인자입니다. 어떤 값을 넣을지를 채워 넣는 자리인데, 보다시피 대부분 `var.`로 시작하는 변수에서 값을 받아 옵니다. 모듈을 호출하는 쪽에서 값을 주입한다는 뜻인데, 그 입구가 바로 다음에 볼 `variable`입니다.

---

## variable — 밖에서 받는 입력, 그리고 잘못된 입력을 막는 자리

`variable`은 모듈이 밖에서 받는 입력을 선언하는 블록입니다. 메서드 시그니처의 파라미터에 해당합니다. 다만 HCL의 변수에는 Java 파라미터보다 한 가지가 더 붙습니다. 타입뿐 아니라 "허용되는 값의 조건"까지 선언으로 박을 수 있습니다.

이 저장소의 S3 버킷 모듈은 버킷 이름을 이렇게 받습니다.

```hcl
variable "bucket_name" {
  description = "S3 버킷 이름. 전역 유일."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.bucket_name))
    error_message = "S3 버킷 이름 규칙 위반."
  }
}
```

`type = string`은 이 변수가 문자열이어야 한다는 제약입니다. 타입은 `string`, `number`, `bool` 같은 기본형과 `list`, `map`, `object` 같은 복합형을 쓸 수 있습니다. 여기에 `validation` 블록이 붙으면, 타입을 통과한 값이라도 한 번 더 조건을 검사합니다. 위 코드는 정규식으로 버킷 이름이 AWS의 이름 규칙을 따르는지 확인하고, 어긋나면 `error_message`를 보여 주며 멈춥니다. 공식 문서는 검증 블록을 두고 "사용자가 입력한 값이 모듈의 요구 사항을 만족하는지 확인한다"고 설명합니다.

이 장치가 왜 중요한지는 Java의 방어적 프로그래밍을 떠올리면 분명해집니다. 메서드 첫 줄에서 `if (name == null || !name.matches(...)) throw ...`를 적는 그 습관입니다. 차이가 있다면, HCL의 검증은 자원을 실제로 만들기 전 단계에서 걸러진다는 점입니다. 잘못된 버킷 이름으로 AWS에 요청을 보내 실패를 받는 게 아니라, 그 전에 Terraform이 막아 줍니다. 검증을 선언으로 박아 두면, 모듈을 쓰는 사람이 규칙을 몰라도 잘못된 값이 운영까지 새어 나가지 않습니다.

타입은 더 깊이 들어갈 수도 있습니다. 같은 모듈의 라이프사이클 규칙 변수를 보면 이런 모양이 나옵니다.

```hcl
variable "lifecycle_rules" {
  description = "라이프사이클 규칙 목록. 미지정 시 빈 리스트."
  type = list(object({
    id                                     = string
    enabled                                = bool
    prefix                                 = optional(string, "")
    abort_incomplete_multipart_upload_days = optional(number, 7)
    transition_to_standard_ia_days         = optional(number)
    expiration_days                        = optional(number)
    # ... (이하 생략)
  }))
  default = []
}
```

`list(object({...}))`는 "이런 모양의 객체들이 담긴 목록"이라는 타입 선언입니다. Java로 치면 DTO의 `List`를 받는 셈입니다. 눈여겨볼 곳은 `optional(...)`입니다. `optional(string, "")`은 이 필드를 안 줘도 되고, 안 주면 빈 문자열을 기본값으로 채운다는 뜻입니다. 입문 단계에서는 타입 시스템을 끝까지 파고들 필요는 없습니다. "필수와 선택을 구분하고, 기본값을 선언으로 박아 둔다"는 가치까지만 챙기면 충분합니다. 나머지는 모듈을 직접 만들어 보는 4편에서 다시 만납니다.

---

## output — 밖으로 내보내는 반환값

`output`은 모듈이 밖으로 내보내는 값입니다. 메서드의 반환값에 해당합니다. 모듈이 만든 자원의 ID나 ARN, 엔드포인트 중에서 "다른 모듈이 실제로 쓸 값"만 골라 내보냅니다.

이 저장소의 network 모듈은 자기가 만든 VPC와 서브넷의 식별자를 이렇게 내보냅니다.

```hcl
output "vpc_id" {
  description = "VPC ID."
  value       = aws_vpc.this.id
}

output "private_subnet_ids" {
  description = "Private-App 서브넷 ID 목록. ECS Fargate Task ENI 가 배치된다."
  value       = aws_subnet.private_app[*].id
}
```

`vpc_id`라는 이름으로 VPC의 ID를, `private_subnet_ids`라는 이름으로 사설 서브넷 ID 목록을 내보냅니다. 이 값들은 환경 조립 계층(`envs/dev/main.tf`)에서 `module.network.vpc_id`처럼 참조됩니다. network 모듈은 자기 안에서 무엇을 어떻게 만들었는지 다 드러내지 않고, 다른 모듈이 필요로 하는 값만 깔끔하게 노출합니다. 캡슐화의 감각과 같습니다. 내부 구현은 숨기고, 인터페이스만 공개하는 것입니다.

여기에 한 가지 운영 규칙이 붙습니다. 민감한 값을 내보낼 때는 `sensitive = true`를 명시해야 합니다. 그러면 Terraform이 plan과 apply 로그에 그 값을 그대로 찍지 않습니다. 비밀번호나 토큰 같은 값이 콘솔 출력이나 CI 로그에 노출되는 사고를 막는 장치입니다. 다른 모듈이 쓸 값만 내보내고, 그중 민감한 것은 가린다는 두 규칙이 output 설계의 뼈대입니다.

---

## local — 코드 안의 지역 변수

`local`은 모듈 안에서만 쓰는 지역 변수입니다. 밖에서 받지도 않고 밖으로 내보내지도 않습니다. 같은 값을 여러 군데서 반복해 쓸 때, 그 계산을 한 곳에 모아 두는 자리입니다. 메서드 안에서 중간 계산 결과를 변수에 담아 두는 습관과 같습니다.

S3 버킷 모듈은 모든 자원에 공통으로 붙일 태그를 local로 한 번 계산해 둡니다.

```hcl
locals {
  common_tags = merge(
    {
      ManagedBy = "Terraform"
      Module    = "s3-bucket"
    },
    var.tags,
  )
}
```

`merge`는 여러 맵을 하나로 합치는 함수입니다. 여기서는 모듈이 기본으로 박는 태그(`ManagedBy`, `Module`)와 호출하는 쪽이 추가로 준 태그(`var.tags`)를 합쳐 `common_tags`라는 이름에 담습니다. 그 뒤로는 자원마다 `tags = local.common_tags`라고만 적으면 됩니다. 만약 local 없이 자원마다 `merge(...)`를 직접 적었다면, 공통 태그의 규칙이 바뀔 때마다 모든 자원을 일일이 고쳐야 합니다. 반복되는 계산을 한 줄로 모아 두는 것, 그 효용이 local의 전부입니다. 더 복잡한 의미를 찾을 필요가 없습니다.

---

## data — 이미 존재하는 것을 읽는다

`resource`가 자원을 만든다면, `data`는 이미 있는 것을 읽기만 합니다. 만들지 않습니다. Terraform이 관리하지 않는 자원이나, 지금 이 환경의 메타정보를 조회할 때 씁니다.

이 저장소의 dev 환경은 현재 작업 중인 AWS 계정 ID를 이렇게 읽어 옵니다.

```hcl
data "aws_caller_identity" "current" {}
```

이 한 줄이면 `data.aws_caller_identity.current.account_id`로 현재 계정 ID를 꺼내 쓸 수 있습니다. 실제로 이 값은 IAM 정책 문서에서 ARN을 조립할 때 쓰입니다.

```hcl
resources = [
  "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${var.project}/${var.environment}/*",
]
```

계정 ID를 코드에 하드코딩하지 않고 조회로 채워 넣으면, 같은 코드를 계정이 다른 환경에서도 그대로 쓸 수 있습니다. `resource`와 `data`의 차이는 한 문장으로 정리됩니다. `resource`는 "이게 있어야 한다"고 선언해 만드는 쪽이고, `data`는 "이미 있는 저것의 값을 읽겠다"는 조회 쪽입니다. Java로 비유하면 한쪽은 객체를 생성하는 일이고, 다른 쪽은 외부 시스템을 조회하는 read-only 호출입니다.

---

## 반복 — count와 for_each

같은 자원을 여러 개 만들거나, 조건에 따라 만들거나 안 만들고 싶을 때 반복 메타 인자를 씁니다. 여기서 입문자가 가장 많이 헷갈리는 갈림길이 나옵니다. `count`와 `for_each` 중 무엇을 쓸 것인가입니다.

`count`는 개수를 받습니다. 공식 문서는 "`count` 인자에 정수를 주면 Terraform이 그만큼의 인스턴스를 만든다"고 설명합니다. 가장 흔한 쓰임은 0과 1을 오가는 토글입니다. 이 저장소도 그렇게 씁니다.

```hcl
resource "aws_s3_bucket_public_access_block" "this" {
  count = var.block_public_access ? 1 : 0
  # ...
}
```

`block_public_access`가 참이면 `count`가 1이 되어 자원이 하나 생기고, 거짓이면 0이 되어 아예 생기지 않습니다. 기능을 켜고 끄는 스위치를 자원 개수로 표현하는 방식입니다. 환경 조립 계층에서도 `count = var.enable_aurora ? 1 : 0`처럼 모듈 자체를 켜고 끄는 데 같은 수법을 씁니다.

`for_each`는 개수가 아니라 집합을 받습니다. 공식 문서에 따르면 "`for_each`는 맵이나 문자열 집합을 받아, 그 안의 각 항목마다 인스턴스를 만든다"고 합니다. 그리고 각 인스턴스 안에서는 `each.key`와 `each.value`로 지금 항목의 키와 값을 꺼내 씁니다.

두 인자의 차이를 가르는 핵심은 "인스턴스를 무엇으로 식별하느냐"입니다. `count`는 0부터 시작하는 위치 인덱스로 인스턴스를 식별합니다. `for_each`는 위치가 아니라 키로 식별합니다. 공식 문서는 이 점을 두고 "Terraform이 인스턴스를 맵 키나 집합 원소로 식별한다"고 적습니다. 이 차이가 실전에서 함정으로 드러납니다.

`count`로 만든 자원 목록의 중간 항목을 하나 지우면 어떻게 될까요. 그 뒤의 인덱스가 한 칸씩 앞으로 밀립니다. 3번이 2번 자리로 오면, Terraform 입장에서는 "원래 2번 자리에 있던 것이 3번 것으로 바뀌었다"고 보입니다. 그래서 멀쩡히 두려던 자원이 지워지고 다시 만들어지는 일이 생길 수 있습니다. 키로 식별하는 `for_each`에는 이 문제가 없습니다. 중간 항목을 빼도 나머지 인스턴스의 키는 그대로라, 정체성이 흔들리지 않습니다. 그래서 판단 기준은 단순합니다. 똑같은 N개거나 단순 토글이면 `count`로 충분하고, 항목마다 의미 있는 키가 있고 목록이 바뀔 수 있다면 `for_each`가 안전합니다. 인덱스가 밀려 엉뚱한 자원이 지워지는 사고를 피하려면, 컬렉션에는 `for_each`를 기본으로 두는 편이 좋습니다.

여기서 한 가지는 분명히 해 두고 싶습니다. `count`와 `for_each`는 Java의 for 루프가 아닙니다. "순서대로 N번 돈다"가 아니라 "여러 개를 한꺼번에 선언한다"는 어법입니다. 1편에서 잡은 선언형 사고가 여기서도 이어집니다. 우리는 절차를 적는 게 아니라 최종 상태를 적습니다.

---

## dynamic 블록 — 중첩 블록을 반복으로 만든다

`count`와 `for_each`가 자원 자체를 여러 개 만든다면, `dynamic` 블록은 한 자원 안의 중첩 블록을 반복으로 만듭니다. 공식 문서는 dynamic 블록을 두고 "`for` 표현식처럼 동작하되, 복합 값 대신 중첩 블록을 만들어 낸다"고 설명합니다.

S3 라이프사이클 설정이 좋은 예입니다. 입력으로 받은 규칙 목록의 길이에 따라 `rule` 블록이 0개에서 N개까지 생겨야 합니다. 그걸 손으로 다 적을 수는 없으니 dynamic으로 풉니다.

```hcl
resource "aws_s3_bucket_lifecycle_configuration" "this" {
  count = length(var.lifecycle_rules) > 0 ? 1 : 0

  bucket = aws_s3_bucket.this.id

  dynamic "rule" {
    for_each = var.lifecycle_rules
    content {
      id     = rule.value.id
      status = rule.value.enabled ? "Enabled" : "Disabled"

      dynamic "transition" {
        for_each = rule.value.transition_to_glacier_days != null ? [rule.value.transition_to_glacier_days] : []
        content {
          days          = transition.value
          storage_class = "GLACIER"
        }
      }
    }
  }
}
```

`dynamic "rule"`은 "`rule`이라는 중첩 블록을 반복해서 만들겠다"는 선언입니다. `for_each = var.lifecycle_rules`로 입력 목록을 돌고, `content` 안이 실제로 만들어질 블록의 본문입니다. 본문에서는 라벨 이름(`rule`)이 그대로 반복 변수 노릇을 합니다. 그래서 `rule.value.id`로 지금 항목의 값을 꺼냅니다. 공식 문서도 "iterator 인자를 생략하면 반복 변수 이름이 dynamic 블록의 라벨로 정해진다"고 안내합니다.

안쪽에 또 `dynamic "transition"`이 중첩돼 있는데, 이건 "값이 있을 때만 블록을 만든다"는 패턴입니다. `transition_to_glacier_days`가 null이면 빈 리스트 `[]`를 돌므로 블록이 하나도 안 생기고, 값이 있으면 그 값 하나짜리 리스트를 돌아 블록이 딱 하나 생깁니다. 조건부로 중첩 블록을 켜고 끄는 관용구입니다.

dynamic은 쓸모가 많지만, 공식 문서는 오히려 절제를 권합니다. "dynamic 블록을 남용하면 설정이 읽기 어렵고 유지보수하기 힘들어지므로, 깔끔한 인터페이스를 만들기 위해 세부를 숨겨야 할 때만 쓰라"고, 그리고 "가능하면 중첩 블록을 그대로 직접 적으라"고 분명히 적혀 있습니다. 위 라이프사이클 예처럼 입력 길이에 따라 블록 수가 진짜로 달라지는 자리에서는 dynamic이 정답이지만, 블록이 늘 한두 개로 고정이라면 그냥 풀어 쓰는 편이 읽기 좋습니다.

---

## 표현식 몇 가지 — 삼항, try, merge, for

마지막으로 모듈을 읽다 보면 자주 마주치는 표현식 몇 개를 짚어 둡니다. 앞에서 이미 본 것들도 있습니다.

삼항 연산자 `조건 ? a : b`는 Java와 똑같이 동작합니다. `var.versioning_enabled ? "Enabled" : "Suspended"`처럼 조건에 따라 값을 고릅니다. `count`의 토글에서 본 그 표현입니다.

`try(...)`는 안전한 참조에 씁니다. network 모듈의 출력에 이런 줄이 있습니다.

```hcl
output "vpce_security_group_id" {
  value = try(aws_security_group.vpce[0].id, null)
}
```

`aws_security_group.vpce`는 조건에 따라 안 만들어질 수도 있는 자원입니다. 그러면 `[0]` 참조가 실패하는데, `try(...)`로 감싸 두면 첫 인자가 에러를 내도 두 번째 인자인 `null`로 대신합니다. 만들어졌으면 그 ID를, 안 만들어졌으면 null을 내보내는 식입니다. 조건부 자원을 참조할 때 코드가 깨지지 않게 막아 주는 장치입니다.

`merge(...)`는 local에서 본 대로 여러 맵을 합칩니다. `for` 표현식은 목록이나 맵을 변형할 때 씁니다. network 모듈의 출력에서 인터페이스 엔드포인트 ID들을 맵으로 모으는 줄이 그 예입니다.

```hcl
value = { for k, ep in aws_vpc_endpoint.interface : k => ep.id }
```

"`aws_vpc_endpoint.interface`를 돌면서 키 `k`는 그대로 두고, 값은 각 엔드포인트의 ID로 바꾼 맵을 만들라"는 뜻입니다. Java의 스트림 `collect(toMap(...))`와 결이 비슷합니다.

---

## 마무리 — 이 문법으로 다음 글의 모듈을 읽습니다

여기까지가 HCL의 거의 전부입니다. 블록 다섯 종은 역할로 외우면 됩니다. `resource`는 만들고, `data`는 읽고, `variable`은 받고, `output`은 내보내고, `local`은 안에서 모읍니다. 반복은 세 가지입니다. `count`는 개수로, `for_each`는 키 있는 집합으로 자원을 여러 개 선언하고, `dynamic`은 한 자원 안의 중첩 블록을 반복으로 만듭니다.

개인적으로 HCL을 익히면서 가장 도움이 됐던 태도는, 새 문법을 외우려 하기보다 이미 아는 메서드 개념에 한 칸씩 대어 보는 것이었습니다. 파라미터·반환값·지역 변수·생성·조회라는 다섯 자리에 블록 다섯 종을 얹으면, 처음 보는 모듈도 "이건 무엇을 받아서 무엇을 만들고 무엇을 내보내는 단위구나"로 빠르게 읽힙니다. 다만 그 유비가 깨지는 한 지점은 끝까지 기억해야 합니다. HCL에는 실행 순서가 없고, 참조가 곧 의존이 되어 그래프로 순서가 정해집니다. `count`를 for 루프로 착각하는 순간 코드가 안 읽히는 이유도 거기 있습니다.

또 하나 남는 인상은, 입문 단계에서 가장 비싼 실수가 문법을 몰라서가 아니라 `count`와 `for_each`를 잘못 골라서 생긴다는 점이었습니다. 단순 토글에는 `count`로 충분하지만, 바뀔 수 있는 컬렉션에 `count`를 쓰면 인덱스가 밀려 엉뚱한 자원이 지워질 수 있습니다. 이 한 가지 판단 기준만 몸에 익혀도 운영에서 만나는 사고의 상당수를 피할 수 있다고 느꼈습니다.

이 여덟 개의 쓰임을 손에 익혔다면, 다음 4편에서 다룰 모듈 설계는 사실상 이 문법의 조합입니다. 모듈은 `variable`로 받고 `output`으로 내보내는 단위이고, 그 안을 `resource`·`local`·반복으로 채우는 일입니다. 그래서 4편에서는 문법을 다시 설명하지 않고, 이 글을 전제로 "그 블록들을 어떻게 재사용 가능한 단위로 묶을 것인가"로 바로 들어갑니다.

---

## 참고한 공식 문서

- Resources — https://developer.hashicorp.com/terraform/language/resources
- Input Variables — https://developer.hashicorp.com/terraform/language/values/variables
- Output Values — https://developer.hashicorp.com/terraform/language/values/outputs
- Local Values — https://developer.hashicorp.com/terraform/language/values/locals
- Data Sources — https://developer.hashicorp.com/terraform/language/data-sources
- The `count` Meta-Argument — https://developer.hashicorp.com/terraform/language/meta-arguments/count
- The `for_each` Meta-Argument — https://developer.hashicorp.com/terraform/language/meta-arguments/for_each
- Dynamic Blocks — https://developer.hashicorp.com/terraform/language/expressions/dynamic-blocks
