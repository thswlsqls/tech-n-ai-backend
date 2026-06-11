# 03. HCL 문법 핵심 — resource·variable·output·local·data와 표현식

> 1차 소스: [`09-iac-terraform.md` §4 모듈 표준](../../../../../../devops/results/09-iac-terraform.md)

## 한줄 요약(Hook)

> HCL은 배울 게 많은 언어가 아니다. 블록 다섯 종(resource·variable·output·local·data)과 반복 세 가지(count·for_each·dynamic)만 손에 익으면, 이 저장소의 모든 모듈이 읽힌다.

## 핵심 질문

- HCL을 이루는 핵심 블록 다섯 종은 각각 무슨 역할인가?
- 변수에 타입과 `validation`을 다는 건 왜 중요한가?
- `count`와 `for_each`는 언제 무엇을 쓰나?
- `dynamic` 블록은 어떤 반복을 풀어 주나?

## 다루는 관점

- ✅ 개념 이해(Why) — 선언형 블록의 의미
- ✅ 실전 사용(기본기) — 실제 모듈 코드로 문법 익히기
- ✅ Java 개발자 대비 — 블록을 메서드 시그니처·반환값·지역변수에 매핑

## 09 문서 근거

- §4.1 입력 규칙 — `required`/`optional`/`nullable`, `validation` 블록 필수
- §4.2 출력 규칙 — 다른 모듈이 쓰는 값만, `sensitive` 명시
- 실제 코드:
  - [`modules/s3-bucket/variables.tf`](../../../../../../devops/terraform/modules/s3-bucket/variables.tf) — `validation`, `optional(...)`, `list(object({...}))`
  - [`modules/s3-bucket/main.tf`](../../../../../../devops/terraform/modules/s3-bucket/main.tf) — `locals`, `merge`, `count`, `dynamic "rule"`
  - [`modules/network/outputs.tf`](../../../../../../devops/terraform/modules/network/outputs.tf) — `output`, `for ... in ...`, `try(...)`
  - [`envs/dev/main.tf`](../../../../../../devops/terraform/envs/dev/main.tf) — `data "aws_caller_identity"`, 모듈 호출

## 타깃 독자 & 난이도

- Terraform을 처음 쓰는, Java 미들 개발자 출신 데브옵스 엔지니어
- ★★☆☆☆ (사전지식: 01·02편, JSON/YAML 구조에 익숙)

## 예상 분량

- 김 (~4,500자) — 문법 레퍼런스 성격이라 예제가 많다

## 글 아웃라인

1. **들어가며 — HCL은 작은 언어다**
   - 외울 키워드가 적다. 대신 블록의 "역할"을 정확히 아는 게 핵심
2. **resource — 만들고 싶은 것 하나**
   - `resource "aws_s3_bucket" "this" { ... }`의 타입·이름·인자 구조
   - 같은 타입을 코드 안에서 `aws_s3_bucket.this`로 참조한다
3. **variable — 밖에서 받는 입력**
   - `type`(string/number/bool/list/map/object), `default`, `nullable`
   - `validation` 블록으로 잘못된 입력을 apply 전에 막는다(s3-bucket의 버킷 이름 정규식)
   - Java 대비: 메서드 파라미터 + 인자 검증(`@Valid`/방어적 체크)
4. **output — 밖으로 내보내는 반환값**
   - 다른 모듈이 쓸 ID·ARN·Endpoint만 노출, 민감 값은 `sensitive`(§4.2)
   - network 모듈이 `vpc_id`, `private_subnet_ids`를 내보내는 방식
5. **local — 코드 안의 지역 변수**
   - `locals { common_tags = merge(...) }`로 반복을 한 곳에 모은다
   - Java 대비: 메서드 내 지역 변수 + 헬퍼 계산
6. **data — 이미 존재하는 것을 조회(읽기 전용)**
   - `data "aws_caller_identity" "current"`로 현재 계정 ID를 읽는다
   - resource(만든다)와 data(읽는다)의 차이를 분명히
7. **반복 — count와 for_each**
   - `count`: 개수로 0개/1개 토글(`count = var.enable ? 1 : 0`)이나 동일한 N개
   - `for_each`: 키가 있는 집합을 돌 때. 키로 안정적으로 참조
   - 언제 무엇을 쓰는지 판단 기준(요소 추가/삭제 시 인덱스 흔들림)
8. **dynamic 블록 — 중첩 블록을 반복으로 생성**
   - s3-bucket의 `lifecycle_rules`가 `dynamic "rule"` + `dynamic "transition"`으로 풀리는 예
   - 입력 리스트 길이에 따라 블록이 0개~N개 생성된다
9. **표현식 몇 가지 — 삼항, `try`, `merge`, `for`**
   - `try(aws_security_group.vpce[0].id, null)` 같은 안전한 참조
10. **마무리 — 이 문법으로 04편의 모듈을 읽는다**

## Java 개발자 대비 포인트

블록을 메서드 추상화에 매핑하면 빠르게 잡힌다: **variable = 파라미터, output = 반환값, local = 지역 변수, resource = 생성 부수효과, data = 조회(read-only)**. 단, HCL에는 "실행 순서"가 없고 의존 관계로 그래프가 짜인다는 점만 계속 상기시켜야 한다(01편 멘탈 모델의 연장). `count`/`for_each`를 for 루프로 오해하지 않게, "여러 개를 선언한다"는 선언형 어법으로 설명한다.

## 참고할 1차 출처 (공식 문서)

- Resources — https://developer.hashicorp.com/terraform/language/resources
- Input Variables — https://developer.hashicorp.com/terraform/language/values/variables
- Output Values — https://developer.hashicorp.com/terraform/language/values/outputs
- Local Values — https://developer.hashicorp.com/terraform/language/values/locals
- Data Sources — https://developer.hashicorp.com/terraform/language/data-sources
- `count` — https://developer.hashicorp.com/terraform/language/meta-arguments/count
- `for_each` — https://developer.hashicorp.com/terraform/language/meta-arguments/for_each
- Dynamic Blocks — https://developer.hashicorp.com/terraform/language/expressions/dynamic-blocks

## 시리즈 인용 관계

**[01](./01-why-terraform-declarative-shift.md)·[02](./02-state-the-source-of-truth.md)** 의 멘탈 모델 위에서 "그래서 코드는 어떻게 생겼나"에 답한다. 본 단편의 블록·반복 문법은 **[04 — 모듈 설계](./04-module-design.md)** 가 그대로 재료로 쓴다(모듈은 variable로 받고 output으로 내보내는 단위). 04에서는 문법을 다시 설명하지 않고 "03 참고"로 넘긴다.

## 작성 메모

- 문법 나열로 흐르지 않게, 각 블록을 **이 저장소의 실제 코드 한 조각**으로 보여 준다(가짜 예제 최소화).
- `count` vs `for_each`는 입문자가 가장 헷갈리는 지점. "삭제 시 인덱스가 밀려 엉뚱한 리소스가 지워질 수 있다"는 실전 함정을 한 줄로 경고하되, 깊은 리팩토링 얘기는 범위 밖.
- `dynamic` 블록은 s3-bucket `lifecycle_rules` 예가 충분히 강력하다. 억지 예제를 새로 만들지 말고 실코드를 그대로 인용한다.
- 타입 시스템(`optional`, `object`)을 과하게 깊이 파지 말 것. 입문 독자에겐 "검증과 기본값을 선언으로 박는다"는 가치까지만.
</content>
