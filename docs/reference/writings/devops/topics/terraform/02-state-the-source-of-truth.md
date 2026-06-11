# 02. State — Terraform이 현실을 기억하는 법

> 1차 소스: [`09-iac-terraform.md` §3 상태 및 백엔드](../../../../../../devops/results/09-iac-terraform.md)

## 한줄 요약(Hook)

> Terraform이 `plan`에서 "무엇이 바뀌는지"를 알 수 있는 건, 자기가 만든 리소스를 적어 둔 장부(state)가 있기 때문이다. 이 장부를 어디에 두고 누가 동시에 못 건드리게 막느냐가 팀 단위 Terraform의 첫 갈림길이다.

## 핵심 질문

- State는 정확히 무엇을 담고 있고, 왜 없으면 안 되는가?
- State 파일을 로컬에 두면 팀에서 무슨 일이 생기는가?
- 원격 백엔드(S3 + DynamoDB 잠금)는 무엇을 막아 주는가?
- drift(코드와 실제 인프라의 어긋남)는 어떻게 생기고 어떻게 보나?

## 다루는 관점

- ✅ 개념 이해(Why) — state의 존재 이유, 잠금이 푸는 동시성 문제
- ✅ 실전 사용(기본기) — 원격 백엔드 구성, drift 확인
- ✅ Java 개발자 대비 — Java에 대응물이 없는 개념이라는 점을 정면으로 다룸

## 09 문서 근거

- §3.1 원격 상태 아키텍처 — S3 버킷(버전 관리·KMS·Object Lock·BPA) + DynamoDB Lock 테이블
- §3.2 상태 분리 전략 — Workspace vs 별도 state, Blast Radius 축소
- §3.4 상태 Import 절차 — 이미 있는 리소스를 코드로 가져오기(이 편에서는 개념만, 06에서 상세)
- §3.5 상태 손상 복구 — `force-unlock`, S3 버전에서 복구
- 실제 코드: [`bootstrap/state.tf`](../../../../../../devops/terraform/bootstrap/state.tf), [`envs/beta/backend.tf`](../../../../../../devops/terraform/envs/beta/backend.tf)

## 타깃 독자 & 난이도

- Terraform을 처음 쓰는, Java 미들 개발자 출신 데브옵스 엔지니어
- ★★☆☆☆ (사전지식: 01편의 plan/apply, S3·DynamoDB가 무엇인지 정도)

## 예상 분량

- 김 (~4,500자) — 시리즈에서 가장 개념 밀도가 높은 편

## 글 아웃라인

1. **들어가며 — plan은 무엇과 무엇을 비교하는가**
   - 01편에서 미룬 질문: plan의 diff는 "코드 vs 실제 클라우드"가 아니라 "코드 vs state vs 실제"의 3자 비교다
2. **State란 무엇인가 — Terraform이 만든 것들의 장부**
   - 리소스 하나가 어떤 실제 ID(예: `vpc-0abc...`)에 매핑되는지 적어 둔 JSON
   - 코드의 `aws_s3_bucket.this`와 실제 버킷을 잇는 다리
3. **로컬 state의 문제 — 혼자일 땐 안 보이는 벽**
   - 팀원 둘이 각자 로컬 state로 apply하면 서로의 변경을 모른다
   - state 파일을 잃으면 Terraform은 자기가 만든 걸 "남의 것"으로 본다
4. **원격 백엔드 — S3에 장부를 두고 모두가 같은 걸 본다**
   - 이 저장소의 구성(§3.1): state는 S3 버킷, 버전 관리·KMS 암호화·Object Lock으로 보호
   - `backend.tf`가 "이 환경의 state는 S3의 이 키에 둔다"를 가리킨다
5. **잠금(Lock) — 동시에 못 건드리게**
   - DynamoDB Lock 테이블(`techai-tflock`)이 한 번에 한 사람만 apply하도록 막는다
   - Java로 치면 분산 락. 잠금이 없으면 두 apply가 같은 state를 덮어써 깨진다
6. **state에는 민감 정보가 평문으로 들어간다 — 그래서 암호화·접근 제한**
   - DB 비밀번호 같은 값이 state에 평문으로 남을 수 있다(§6과 연결)
   - 그래서 KMS 암호화 + 퍼블릭 액세스 차단 + 접근 Role 분리
7. **drift — 코드와 실제가 어긋날 때**
   - 누가 콘솔에서 손으로 바꾸면 실제 ≠ state가 된다. `plan`이 이걸 diff로 드러낸다
   - drift를 숨기지 않고(예: 무분별한 `ignore_changes` 금지) 코드로 되돌리는 게 원칙(§4.6)
8. **state를 잃거나 깨졌을 때 — 복구의 기본기**
   - S3 버전 관리에서 직전 정상 버전 복구, `force-unlock`은 신중히(§3.5)
9. **마무리 — state를 이해하면 Terraform의 절반을 이해한 것**

## Java 개발자 대비 포인트

State는 Java/Gradle 세계에 **깔끔한 대응물이 없는** 개념이다. 굳이 비유하면 ORM의 영속성 컨텍스트(현재 관리 중인 객체와 DB row의 매핑)에 가깝지만, Terraform의 state는 파일로 떨어지고 팀이 공유한다는 점이 다르다. 이 "대응물 없음"을 숨기지 말고, 오히려 "여기서부터는 Java 직관이 안 통한다"고 못 박는 게 독자에게 정직하다.

## 참고할 1차 출처 (공식 문서)

- Terraform State — https://developer.hashicorp.com/terraform/language/state
- S3 Backend — https://developer.hashicorp.com/terraform/language/settings/backends/s3
- Sensitive Data in State — https://developer.hashicorp.com/terraform/language/state/sensitive-data
- Terraform Recommended Practices — https://developer.hashicorp.com/terraform/cloud-docs/recommended-practices
- AWS Prescriptive Guidance — Terraform AWS Provider Best Practices — https://docs.aws.amazon.com/prescriptive-guidance/latest/terraform-aws-provider-best-practices/

## 시리즈 인용 관계

**[01 — 왜 Terraform인가](./01-why-terraform-declarative-shift.md)** 가 미뤄 둔 "plan은 무엇과 비교하나"에 답하며 출발한다. 본 단편이 잡은 state 개념은 뒤에서 두 번 다시 쓰인다: **[05 — 환경 조립](./05-environment-composition.md)** 의 "환경별 별도 state"(§3.2), **[06 — 팀 운영](./06-operate-safely-in-team.md)** 의 "state에 시크릿 평문 0건"(§6)·"state 접근 Role 분리"(§3.3)·import. 본 단편에서는 그 둘을 "뒤에서 다룬다"고만 가리키고 깊이 들어가지 않는다.

## 작성 메모

- state를 "그냥 캐시"로 가볍게 말하지 말 것. 잃으면 Terraform이 자기 리소스를 남의 것으로 보고 다시 만들려 한다는 위험을 분명히 한다.
- `backend.tf`의 `dynamodb_table`은 이 저장소의 Terraform 1.9 기준 잠금 방식이다. 다만 **공식 S3 백엔드 문서는 현재 DynamoDB 기반 잠금을 deprecated로 표기**하고, S3 네이티브 잠금(`use_lockfile`, Terraform 1.10에서 도입)을 권장한다(공식 문서 확인: developer.hashicorp.com/terraform/language/backend/s3). 그러니 글에서는 "이 저장소는 1.9라 DynamoDB 잠금을 쓰지만, 1.10+에서는 `use_lockfile`로 대체되는 추세"라고 **버전 맥락을 분명히** 한다 — DynamoDB 잠금을 현재 베스트 프랙티스인 양 적지 않는다.
- drift는 06의 "주 1회 drift 탐지"(§8)와 겹칠 수 있다. 여기서는 **drift가 무엇이고 왜 생기는가**(개념)까지만, 자동 탐지 운영은 06으로 넘긴다.
- 시크릿이 state에 평문으로 남는다는 사실은 충격 포인트라 반드시 짚되, 해결책(런타임 조회)은 06의 몫이므로 여기선 "그래서 암호화한다"까지만.
</content>
