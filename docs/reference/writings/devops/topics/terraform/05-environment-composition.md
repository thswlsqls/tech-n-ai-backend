# 05. 환경 조립 — 변수 한두 개로 dev/beta/prod를 분기한다

> 1차 소스: [`09-iac-terraform.md` §2.2 레이어 규칙·§2.3 호출 패턴·§3.2 상태 분리](../../../../../../devops/results/09-iac-terraform.md)

## 한줄 요약(Hook)

> dev와 prod는 같은 설계여야 하지만 같은 비용일 수는 없다. 이 모순을 푸는 방법은 코드를 복제하는 게 아니라, 같은 모듈을 `tfvars` 한 장으로 다르게 호출하는 것이다.

## 핵심 질문

- 똑같은 인프라 설계를 dev/beta/prod에 어떻게 한 벌의 모듈로 재현하나?
- 환경 차이(NAT 개수, ACU, Multi-AZ)는 코드 어디에서 갈라지나?
- 환경 분리에 Workspace를 쓰면 왜 위험한가? 왜 별도 state·별도 계정인가?
- `count`로 리소스를 켜고 끄는 토글은 언제 쓰나?

## 다루는 관점

- ✅ 개념 이해(Why) — DRY와 환경별 차이를 동시에 만족시키는 구조
- ✅ 실전 사용(기본기) — envs/modules 레이어, tfvars 분기, count 토글
- ✅ Java 개발자 대비 — `application-{env}.yml` 프로파일과의 유비, 한계까지

## 09 문서 근거

- §2.2 레이어 규칙 — `envs/*`는 모듈 호출 조립 계층, 리소스 직접 선언 지양(예외: 환경 고유 KMS/Route53/Cluster)
- §2.3 모듈 호출 패턴 — `envs/prod/main.tf`가 `module "network"`를 호출하는 형태
- §3.2 상태 분리 전략 — 환경은 별도 계정 + 별도 state, Workspace 비권장
- 실제 코드:
  - [`envs/dev/main.tf`](../../../../../../devops/terraform/envs/dev/main.tf) — 모듈 호출 + `count = var.enable_aurora ? 1 : 0` 토글
  - [`envs/beta/terraform.tfvars`](../../../../../../devops/terraform/envs/beta/terraform.tfvars) — `single_nat_gateway`, `aurora_max_acu`, `cache_multi_az_enabled` 등 환경 값
  - [`devops/terraform/README.md`](../../../../../../devops/terraform/README.md) — CIDR dev `10.10/16` / beta `10.20/16` / prod `10.30/16`

## 타깃 독자 & 난이도

- Terraform을 처음 쓰는, Java 미들 개발자 출신 데브옵스 엔지니어
- ★★★☆☆ (사전지식: 02편 state, 04편 모듈)

## 예상 분량

- 보통 (~3,800자)

## 글 아웃라인

1. **들어가며 — dev에 prod와 똑같은 돈을 쓸 수는 없다**
   - 설계는 같게, 규모·비용은 다르게. 복붙은 답이 아니다
2. **레이어 분리 — envs는 조립만 한다**
   - `modules/*`(설계) / `envs/*`(조립) / `bootstrap/*`(state 인프라)의 역할(§2.2)
   - envs는 리소스를 직접 만들기보다 모듈을 호출한다. Java 대비: `main()`이 객체를 조립하는 자리
3. **같은 모듈, 다른 변수 — tfvars가 환경의 차이를 담는다**
   - `envs/dev/main.tf`는 dev·beta·prod가 거의 동일, 차이는 `terraform.tfvars`에 모인다
   - beta tfvars 실값: `single_nat_gateway = true`, `aurora_max_acu = 4.0`, `cache_multi_az_enabled = true`
   - Java 대비: `application-dev.yml` / `application-prod.yml` 프로파일과 같은 결
4. **환경별 분기의 실제 — NAT를 예로**
   - dev/beta는 `single_nat_gateway=true`(비용), prod는 false(AZ 격리). 변수 하나가 토폴로지를 가른다
5. **count 토글 — 리소스를 환경별로 켜고 끈다**
   - `module "aurora" { count = var.enable_aurora ? 1 : 0 }`로 시드 단계엔 끄고 나중에 켠다
   - MSK Serverless vs Provisioned를 `use_msk_provisioned`로 분기하는 패턴
6. **환경 분리 전략 — Workspace는 왜 권장하지 않나**
   - Workspace는 `select` 오인 위험. 환경은 **별도 계정 + 별도 state**로 Blast Radius를 줄인다(§3.2)
   - 디렉터리(`envs/dev`, `envs/prod`) 자체가 컨텍스트가 되게 한다
7. **프로파일 유비의 한계 — 어디서 깨지나**
   - Spring 프로파일은 한 앱 안의 설정이지만, Terraform 환경은 state·계정까지 갈라진다
   - tfvars로 못 담는 차이(환경 고유 KMS 키, Route53 레코드)는 envs에 직접 선언(§2.2 예외)
8. **마무리 — 좋은 환경 분리는 diff가 tfvars에 모인다**

## Java 개발자 대비 포인트

`tfvars`를 Spring의 `application-{env}.yml`에 비유하면 출발이 쉽다. 단, 유비는 6~7번 절에서 일부러 깬다. Spring 프로파일은 같은 프로세스 안의 설정 차이지만, Terraform의 환경은 **state 파일과 AWS 계정까지 분리**되어 한 환경의 사고가 다른 환경으로 번지지 않게 막는다(02편 state·Blast Radius와 연결). 유비를 주되 한계를 분명히 하는 게 이 단편의 정직함이다.

## 참고할 1차 출처 (공식 문서)

- Module Sources / Calling Modules — https://developer.hashicorp.com/terraform/language/modules/sources
- Assigning Values to Variables (`.tfvars`) — https://developer.hashicorp.com/terraform/language/values/variables#assigning-values-to-root-module-variables
- Workspaces — https://developer.hashicorp.com/terraform/language/state/workspaces
- Terraform Recommended Practices — https://developer.hashicorp.com/terraform/cloud-docs/recommended-practices
- AWS Prescriptive Guidance — Terraform AWS Provider Best Practices — https://docs.aws.amazon.com/prescriptive-guidance/latest/terraform-aws-provider-best-practices/

## 시리즈 인용 관계

**[04 — 모듈 설계](./04-module-design.md)** 가 만든 재사용 모듈을, **[02 — State](./02-state-the-source-of-truth.md)** 의 "환경별 별도 state" 위에서 dev/beta/prod로 조립하는 편이다. 두 단편을 전제로 하므로 모듈 작성법·state 개념은 다시 설명하지 않는다. 환경별로 갈리는 운영 항목 중 **품질 게이트·CI/CD 승인**은 **[06 — 팀 운영](./06-operate-safely-in-team.md)** 으로 넘긴다.

## 작성 메모

- 추상론으로 흐르지 않게, beta `terraform.tfvars`의 **실제 값**(`single_nat_gateway`, `aurora_max_acu` 등)을 표로 펼쳐 dev/beta/prod를 나란히 비교한다.
- 정확성 주의: `dev/terraform.tfvars`는 `single_nat_gateway`를 **명시하지 않고 변수 기본값(`true`)에 기댄다**. beta·prod만 명시값을 둔다(beta `true`, prod `false`). 글에서 "dev=true"라고 쓸 땐 "기본값으로 true"임을 정확히 적는다. 분기 로직 자체는 `modules/network`의 `nat_count = enable_nat_gateway ? (single_nat_gateway ? 1 : length(azs)) : 0`에 있으니, prod가 AZ별 NAT 3개를 만드는 근거로 이 줄을 인용한다.
- 네트워크 설계 시리즈(`devops/topics/`의 NAT·CIDR 단편)와 주제가 닿지만, 이 단편의 초점은 **"같은 모듈을 환경별로 다르게 호출하는 IaC 패턴"** 이다. 네트워크 설계 자체는 그쪽 시리즈로 넘기고 여기서 반복하지 않는다.
- Workspace를 "쓰지 마라"로 단정하지 말고, §3.2 표처럼 **언제 위험한지**를 근거와 함께 보인다.
- 프로파일 유비는 강력하지만 과신 금지. 한계 절(7번)을 빼면 독자가 "그냥 yml이네"로 오해한다.
</content>
