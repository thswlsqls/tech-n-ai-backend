---
name: refactor-terraform
description: tech-n-ai-backend의 devops/terraform 세팅(bootstrap·modules·envs)을 CLAUDE.md 지침을 지키며 리팩토링한다. 인프라 동작은 그대로 두고 이름·중복·파일 구성·모듈 경계만 다듬는다. terraform plan 이 no-op 인지로 베이스라인을 잡은 뒤 리팩토링 후보와 안전 등급을 한국어 계획으로 보여주고, 승인받으면 외과적으로 적용한 다음 plan 으로 재검증한다. "terraform 리팩토링해줘", "network 모듈 정리해줘", "devops 세팅 다듬어줘" 처럼 IaC 정리·개선을 요청할 때 적용한다.
---

# Terraform 리팩토링

`tech-n-ai-backend/devops/terraform` 의 IaC 코드를 받아 리팩토링한다.

이 스킬은 형제 스킬 `refactor-module`(자바 모듈용)을 Terraform 으로 옮긴 것이다.
핵심은 "무엇을 바꾸는가"가 아니라 **CLAUDE.md 의 4대 행동 지침을 매 단계 강제하는 절차**이며,
특히 다음 둘을 어기지 않는다.

- **Surgical Changes**: 바뀐 모든 줄은 사용자의 리팩토링 요청으로 곧장 설명돼야 한다. 멀쩡한 인접 코드·주석·포맷은 건드리지 않는다.
- **Goal-Driven Execution**: 리팩토링은 "전후로 `terraform plan` 이 no-op 인지 확인하는" 작업이다. plan 무변경을 성공 기준으로 삼는다.

## 리팩토링의 정의 (인프라 동작 불변)

여기서 리팩토링은 **만들어지는 AWS 리소스를 하나도 바꾸지 않고 코드만 다듬는 것**이다.
판단 기준은 단순하다. **`terraform plan` 에 한 줄이라도 변경이 뜨면 그건 리팩토링이 아니다.**

바꾸는 것:

- 이름: 의도가 안 드러나는 변수·`local`·output 이름
- 중복 제거: 반복되는 문자열·표현식을 `locals` 나 변수로 한 곳에 모음 (예: 매번 조립하는 ARN prefix)
- 파일 구성: 책임이 섞인 리소스를 알맞은 `*.tf` 로 옮김 (리소스 라벨은 그대로 — 아래 상태 주소 주의)
- 모듈 경계: 같은 리소스 묶음이 여러 곳에서 반복되면 모듈로 추출 (단, 한 번만 쓰는 코드에 억지로 만들지 않음)

바꾸지 않는 것 (= 범위 밖, 발견하면 보고만 하고 멈춤):

- `terraform plan` diff 에 나타나는 모든 것 — 그건 실제 인프라 변경이다
- 리소스 속성값: `instance_class`, `cidr_block`, `retention_in_days`, ACU, 노드 수 등
- 모듈의 입출력 인터페이스(`variables.tf` / `outputs.tf`) — 다른 `envs/*` 가 호출하는 공개 계약이다
- 보안 그룹 규칙·IAM 정책 권한 — `00-cross-cutting-matrix.md` §2·§3 가 단일 정의처
- KMS 키 개수·용도, Secret 이름 — 같은 매트릭스 §1·§4 가 정의
- 새 기능·새 추상화·"미래 대비" 코드 (CLAUDE.md "오버엔지니어링 금지")

설정 오류나 잠재 버그(예: `enable_msk=false` 일 때 비는 IAM statement)를 발견하면 고치지 말고 **별도로 보고한다.** 그건 plan 을 바꾸는 동작 변경이고, 리팩토링이 아니라 다른 작업이다.

## Terraform 고유 함정 — 상태 주소 (state address)

자바와 가장 다른 지점이다. 코드상 동작이 같아도 **리소스의 상태 주소가 바뀌면** Terraform 은 옛 리소스를 destroy 하고 새로 create 한다. 즉 `for_each` 로 중복을 줄이는 "당연한" 리팩토링이 운영상 인프라 파괴가 된다.

후보를 두 등급으로 가른다.

- **안전 (상태 주소 불변)** — plan 이 자연히 no-op
  - `locals` / 변수로 표현식·문자열 추출
  - 변수 `default` 정리, 주석·`terraform fmt` 포맷
  - 리소스를 다른 `*.tf` 파일로 이동하되 **라벨(`resource "x" "this"`)은 그대로**
- **주의 (상태 주소 이동)** — 그대로 두면 destroy/create
  - `count`/`for_each` 전환, 리소스 라벨 변경, 모듈 추출·이름 변경
  - 이 경우 **반드시 `moved {}` 블록**(또는 `terraform state mv`)으로 주소를 보존하고, plan 이 no-op 인지 확인해야 "인프라 동작 불변"이 성립한다.
  - moved 블록으로 no-op 을 증명할 수 없으면 적용하지 말고 후보로만 보고한다.

## 대상 인자 해석

사용자가 어느 범위를 주든 받는다. 헷갈리면 후보를 보여주고 묻는다.

- `network`, `modules/network` → 재사용 모듈 하나 (`modules/network/`)
- `dev`, `envs/dev` → 환경 조립 계층 하나
- `bootstrap` → state·OIDC·ECR 부트스트랩 (1회성, 변경에 특히 신중)
- 인자 없음 / "전체" → `devops/terraform` 트리 전체

실제 구조는 추측하지 말고 확인한다:

```bash
cd devops/terraform
find . -name '*.tf' | sed 's|/[^/]*$||' | sort -u   # 디렉토리(모듈) 목록
```

이 레포 고유 사실 두 가지를 미리 알고 들어간다.

- **`envs/{dev,beta,prod}` 의 `.tf` 10개는 환경 간 글자 단위로 동일한 복사본**이다 (차이는 `variables.tf`/`providers.tf`/`backend.tf` 와 `*.tfvars` 뿐). 이건 "환경별 디렉토리" 패턴이라는 의도된 설계다. 공유 root module 로 묶는 것은 리팩토링이 아니라 아키텍처 결정(ADR) 사안이므로 **범위 밖**으로 두고 보고만 한다. envs 안의 안전한 후보(예: ARN prefix 추출)를 적용한다면 **세 환경 파일에 동일하게** 넣는다.
- **`results/00-cross-cutting-matrix.md` 가 KMS·IAM·SG·Secret 의 단일 정의처**다. 이 자원들의 정의를 바꾸면 매트릭스 위반이자 동작 변경이다. 리팩토링이 `results/_validation-report-*.md` 가 잡아둔 코드↔문서 일치를 깨지 않게 한다.

## 절차

### 1. 대상 확정과 베이스라인 (Goal-Driven Execution)

손대기 전에 현재 코드가 깨끗한지, plan 이 no-op 인지 확인한다. 베이스라인을 못 잡으면 전후 비교가 무의미하다.

먼저 도구가 있는지 확인한다. 이 환경엔 없을 수 있다.

```bash
terraform version && tflint --version    # 둘 다 없으면 자동 검증 불가
```

도구가 있으면 베이스라인을 잡는다 (대상이 모듈이면 그 모듈을 쓰는 `envs/<env>` 에서 실행):

```bash
cd devops/terraform/envs/<env>
terraform fmt -check -recursive          # 포맷 드리프트 확인
terraform init -backend=false            # 또는 실제 backend (자격증명 있을 때)
terraform validate                       # 문법·참조 검증
tflint                                   # 린트
terraform plan                           # no-op(0 add/change/destroy) 이어야 베이스라인 그린
```

- plan 이 no-op 이면 진행한다.
- plan 에 변경이 떠 있으면(코드와 실제 상태가 이미 다름) 멈추고 사용자에게 알린다. 내가 만들지 않은 드리프트를 떠안고 리팩토링하지 않는다.
- **`terraform`/`tflint` 가 없거나 `plan` 에 AWS 자격증명이 없으면**, 그 한계를 명확히 알린다 — "자동 검증 수단이 `validate`(있으면)와 코드 리뷰뿐이고, plan no-op 으로 동작 불변을 증명할 수 없다". 그 상태에서 **상태 주소를 바꾸는 '주의' 등급 후보는 적용하지 않는다.** 진행 여부를 묻는다.

### 2. 코드 파악 (Think Before Coding)

대상 코드를 읽고 구조와 관례를 파악한다.

- 레이어 규칙: `envs/*` 는 모듈 호출만 하고 리소스를 직접 선언하지 않는다 (09 §2.2).
- 명명: `{project}-{env}-{resource}` (예: `techai-dev-vpc`), 모든 리소스에 `local.common_tags`.
- Provider 핀: Terraform `~> 1.9.5`, AWS Provider `~> 5.60` — 바꾸지 않는다.
- 모듈 안의 기존 스타일을 따른다. 내 취향으로 통일하지 않는다.

읽으면서 후보를 모은다. 각 후보는 "어디가 / 왜 문제인지 / 어떻게 바꿀지 / 안전 등급(주소 불변인지)"이 한 줄로 설명돼야 한다.

### 3. 계획 제시 (사용자 승인)

코드를 바꾸기 전에 후보 목록을 한국어로 정리해 보여주고 **승인을 기다린다.**
해석이 여러 갈래거나 더 단순한 방법이 있으면 조용히 하나만 고르지 말고 모두 제시한다.

각 후보는 이렇게 적는다:

```
- [파일:라인] 현재: <무엇이 문제인가>
  변경: <어떻게 바꾸는가>
  안전: <안전(주소 불변) | 주의(주소 이동 — moved 블록 필요)>
  근거: <클린코드/중복 제거 중 무엇 — plan 이 no-op 인 이유>
```

부풀리지 않는다. 이 트리는 명명·태그·모듈 경계가 이미 깔끔한 편이라 고칠 게 적을 수 있다. "고칠 게 거의 없다"도 정직한 결론이다. '주의' 등급 후보는 가독성 이득과 상태 이전 비용을 견줘 권장 여부를 분명히 적는다.

### 4. 외과적 적용 (Surgical Changes)

승인받은 후보만 적용한다. 후보 하나 = 변경 하나로 묶어 작게 바꾼다.

- 승인 목록에 없는 변경을 끼워 넣지 않는다. `terraform fmt` 가 인접 줄까지 재포맷하면 그 줄은 되돌린다(요청 범위 밖).
- '주의' 등급을 적용할 땐 같은 변경에 `moved {}` 블록을 함께 넣는다. 주소 이동과 보존을 떼어 커밋하지 않는다.
- 내 변경으로 안 쓰이게 된 변수·local·output 만 제거한다. 기존부터 안 쓰이던 것은 건드리지 않는다.
- envs 공통 `.tf` 를 손대면 dev/beta/prod 세 파일에 동일하게 적용한다 (복사본 동기화 유지).
- 변경 도중 새 후보가 보이면 적용하지 말고 메모해 뒀다가 마지막에 보고한다.

### 5. 재검증 (Goal-Driven Execution)

성공 기준은 **`terraform plan` 이 no-op** 이다. 적용 전과 같은 환경에서 다시 돌린다.

```bash
cd devops/terraform/envs/<env>
terraform fmt -check -recursive
terraform validate
tflint
terraform plan                           # 0 to add, 0 to change, 0 to destroy 여야 통과
```

- plan 이 no-op 이면 완료. 변경 요약과 (있다면) 미적용 후보·발견한 버그를 보고한다.
- plan 에 변경이 떠 있으면 내 리팩토링이 인프라를 바꾼 것이다. '주의' 등급이면 `moved {}` 가 빠졌는지 먼저 확인하고, 그래도 안 잡히면 되돌린다. plan 이 떠 있는 채로 끝내지 않는다.
- 도구·자격증명이 없어 plan 을 못 돌리면, `validate` 결과와 "어떤 변경이 상태 주소를 건드리지 않아 no-op 이 보장되는지"의 근거를 보고에 명시한다. 보장할 수 없으면 적용하지 않는다.

### 6. 보고

- 한국어로, 사람이 검증하는 텍스트 규칙(CLAUDE.md)을 지켜 쓴다. 상투어·번역투·키워드 나열을 피한다.
- 무엇을 왜 바꿨는지, plan 전후 결과, 미적용으로 남긴 것, 별도로 발견한 버그·드리프트를 적는다.
- 커밋은 하지 않는다. 사용자가 따로 시킬 때만 한다. (커밋 메시지가 필요하면 `commit-message` 스킬을 쓴다.)
