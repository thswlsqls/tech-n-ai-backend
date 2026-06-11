# 04. 모듈 설계 — 재사용 단위와 입력 검증

> 1차 소스: [`09-iac-terraform.md` §2.2 레이어 규칙·§4 모듈 표준·§5 모듈 스펙](../../../../../../devops/results/09-iac-terraform.md)

## 한줄 요약(Hook)

> 모듈은 "환경에 독립적인 함수"다. 하드코딩된 계정 ID나 ARN이 한 줄이라도 들어가는 순간, 그 모듈은 dev에서 prod로 옮겨 심을 수 없는 반쪽짜리가 된다.

## 핵심 질문

- 언제 코드를 모듈로 뽑고, 언제 그냥 두는가?
- 모듈의 입력(variable)과 출력(output)은 무엇을 기준으로 정하나?
- "환경 독립"이란 구체적으로 무엇을 금지하는 규칙인가?
- `validation`·`sensitive`·`prevent_destroy`는 각각 무엇을 지키나?

## 다루는 관점

- ✅ 개념 이해(Why) — 모듈 = 재사용 단위, 레이어 분리의 이유
- ✅ 실전 사용(기본기) — 입력/출력/검증 설계, 모듈 호출
- ✅ Java 개발자 대비 — 모듈을 순수 함수·인터페이스 계약에 매핑

## 09 문서 근거

- §2.2 레이어 규칙 — `modules/*`는 환경·계정 독립, 하드코딩 ARN/ID/계정 금지
- §4.1 입력 규칙 — required/optional, `validation` 필수
- §4.2 출력 규칙 — 다음 계층이 쓰는 값만, `sensitive`
- §4.4 네이밍 — `techai-{env}-{component}-{purpose}`
- §4.6 라이프사이클 — DB/KMS/상태 버킷 `prevent_destroy`
- §5 모듈 스펙 — network/ecs-service/aurora/s3-bucket/iam-role-workload의 입력·출력·의존 표
- 실제 코드: [`modules/s3-bucket/`](../../../../../../devops/terraform/modules/s3-bucket/main.tf), [`modules/iam-role-workload/`](../../../../../../devops/terraform/modules/iam-role-workload/variables.tf), [`modules/network/outputs.tf`](../../../../../../devops/terraform/modules/network/outputs.tf)

## 타깃 독자 & 난이도

- Terraform을 처음 쓰는, Java 미들 개발자 출신 데브옵스 엔지니어
- ★★★☆☆ (사전지식: 03편의 블록 문법, 함수 추출·인터페이스 설계 감각)

## 예상 분량

- 김 (~4,500자)

## 글 아웃라인

1. **들어가며 — 같은 S3 버킷 설정을 다섯 번 복붙할 것인가**
   - 암호화·버전 관리·퍼블릭 차단을 매번 손으로 적으면 빠뜨린다. 그래서 모듈
2. **모듈은 함수다 — 입력·출력·본문**
   - `variables.tf`(입력) → `main.tf`(본문) → `outputs.tf`(출력)의 3분할
   - Java 대비: 시그니처(파라미터/반환)와 구현의 분리
3. **레이어 규칙 — modules / envs / bootstrap**
   - `modules/*`는 환경 독립(하드코딩 금지), `envs/*`는 조립, `bootstrap/*`은 state 인프라(§2.2)
   - 왜 모듈에 계정 ID를 박으면 안 되는가 — 재사용이 깨진다
4. **입력 설계 — 무엇을 받고, 무엇을 검증하나**
   - required는 `default` 없음, optional은 `default` + `nullable`(§4.1)
   - `validation`으로 잘못된 입력을 apply 전에 끊는다(s3-bucket 버킷 이름, object_lock_mode 화이트리스트)
   - Java 대비: 생성자 인자 검증을 호출 시점이 아니라 계약으로 못 박기
5. **출력 설계 — 꼭 필요한 값만 내보낸다**
   - 다음 계층이 실제로 쓰는 ID·ARN·Endpoint만(§4.2)
   - 민감 값은 `sensitive = true`(예: aurora `master_user_secret_arn`)
6. **iam-role-workload로 보는 "목적 단위 모듈"**
   - 하나의 워크로드 Role만 책임진다. 복수 Role은 호출 측에서 반복(§8 "모듈은 목적 단위")
   - 이름 충돌·과한 `for_each` 남용을 피하는 경계 감각
7. **라이프사이클 가드 — 지워지면 안 되는 것**
   - `prevent_destroy`로 DB/KMS/상태 버킷을 실수 삭제에서 보호(§4.6)
   - drift를 숨기는 `ignore_changes` 남용은 피한다(02편 drift와 연결)
8. **네이밍·태그 — 작지만 끝까지 따라오는 규칙**
   - `{project}-{env}-{component}` + 공통 태그(§4.3, §4.4)
9. **마무리 — 좋은 모듈은 호출부가 짧다**

## Java 개발자 대비 포인트

모듈은 **순수 함수**에 가깝다. 같은 입력이면 같은 결과를 만들고, 외부 상태(계정 ID, 다른 환경의 ARN)를 본문에 박지 않는다. 입력 `validation`은 메서드 진입부의 인자 검증이고, output은 반환 타입이다. "환경 독립"을 어기는 건 함수 안에서 전역 변수를 읽는 것과 같다고 설명하면 Java 출신 독자가 즉시 납득한다. 다만 `prevent_destroy` 같은 라이프사이클 가드는 Java에 없는, 인프라 특유의 안전장치라는 점은 따로 짚는다.

## 참고할 1차 출처 (공식 문서)

- Modules — Overview — https://developer.hashicorp.com/terraform/language/modules
- Module Creation — Standard Structure — https://developer.hashicorp.com/terraform/language/modules/develop/structure
- Custom Validation Rules — https://developer.hashicorp.com/terraform/language/values/variables#custom-validation-rules
- The `lifecycle` Meta-Argument — https://developer.hashicorp.com/terraform/language/meta-arguments/lifecycle
- Terraform Recommended Practices — https://developer.hashicorp.com/terraform/cloud-docs/recommended-practices

## 시리즈 인용 관계

**[03 — HCL 문법](./03-hcl-language-essentials.md)** 의 블록·검증 문법을 "재사용 단위"로 끌어올린다. 03을 읽었다는 전제로 문법은 다시 설명하지 않는다. 본 단편이 만든 모듈은 **[05 — 환경 조립](./05-environment-composition.md)** 에서 dev/beta/prod가 변수만 바꿔 호출하는 재료가 된다. `prevent_destroy`·태그 정책의 운영 측면(드리프트 탐지, 정책 강제)은 **[06 — 팀 운영](./06-operate-safely-in-team.md)** 으로 넘긴다.

## 작성 메모

- "모듈을 언제 만드나"에 과한 일반론을 늘이지 말 것. 이 저장소 기준은 **§8 "모듈은 목적 단위, count/for_each 남용 금지"** 다 — 한 번만 쓰는 코드는 모듈로 빼지 않는다(CLAUDE.md Simplicity First와도 맞물림).
- 입력/출력 스펙은 §5 표가 이미 정리돼 있으니, 글에서는 s3-bucket·iam-role-workload **두 모듈만** 깊게 보고 나머지는 표 참조로 가볍게.
- `validation` 정규식 예제는 s3-bucket 실코드를 그대로. 새 예제를 지어내지 않는다.
- `prevent_destroy`를 "무조건 다 걸어라"로 오해하게 두지 말 것. DB/KMS/상태 버킷 등 **불가역·고비용** 리소스에 한정(§4.6).
</content>
