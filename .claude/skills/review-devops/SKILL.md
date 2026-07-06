---
name: review-devops
description: tech-n-ai-backend의 devops/ 전체 또는 하위 폴더 N개(terraform, aws, results, prompts, reminder)를 받아 면밀히 검증하고 개선한다. AWS MCP(aws-docs, deploy-on-aws)로 문서·설계·IaC의 기술적 사실을 공식 문서에 대조하고, 실제 코드 구현(application.yml, docker-compose, monitoring 등)과 어긋나는 부분을 찾아 즉시 고친다. 검증 결과와 거짓 양성은 devops/review/ 아래 메모리로 쌓아 실행할수록 재검증을 줄인다. "devops 검증해줘", "devops 리뷰해줘", "terraform이랑 문서 정합성 확인해줘", "aws 설계 문서 맞는지 봐줘"처럼 devops 산출물의 검증·개선을 요청할 때 적용한다. 동작 불변 리팩토링은 refactor-terraform이 맡는다.
---

# devops 검증·개선 (review-devops)

`devops/` 산출물(IaC·다이어그램·설계 문서)을 세 축으로 검증하고, 찾은 개선사항을 즉시 적용하며, 결과를 `devops/review/`에 메모리로 쌓는다.

1. **AWS 정합성** — 문서·코드가 주장하는 AWS 동작이 공식 문서와 맞는가 (AWS MCP로 검증)
2. **코드 정합성** — devops 산출물이 실제 애플리케이션 구현과 맞는가 (포트, 프로필, Kafka·Redis 설정, 관측 스택)
3. **내부 정합성** — devops 안에서 terraform ↔ 다이어그램 ↔ results 문서가 서로 맞는가

`refactor-terraform`(동작 불변 정리)과 다르다. 이 스킬은 **틀린 것을 찾아 고치는** 작업이라 동작 변경이 생길 수 있다.

## 대상 인자 해석

- 인자 없음 / "전체" → `devops/` 트리 전체
- 폴더 이름 N개 (`terraform`, `aws`, `results`, `prompts`, `reminder`, 또는 `terraform/modules/network` 같은 하위 경로) → 해당 폴더만
- 헷갈리면 실제 구조를 확인하고 묻는다: `ls devops/`

폴더별 성격을 알고 들어간다.

- `terraform/` — bootstrap · modules(11개) · envs/{dev,beta,prod}. envs의 공통 `.tf`는 세 환경 간 글자 단위 복사본이 의도된 설계다.
- `aws/{dev,beta,prod}/` — 환경별 `.drawio` + `.png` 다이어그램 4종(network-topology, reference-architecture, security, observability). 인프라와 어긋나면 다이어그램이 틀린 것이다.
- `results/` — 설계 기준 문서 00~11. 특히 `00-cross-cutting-matrix.md`는 KMS·IAM·SG·Secret의 단일 정의처다.
- `reminder/` — 미해결 후속 작업 목록. 검증 중 해소된 항목이 있으면 지운다.
- `review/` — 이 스킬의 메모리 저장소. 검증 대상이 아니다.

## 절차

### 1. 메모리 로드 (실행할수록 똑똑해지는 핵심)

코드를 읽기 전에 `devops/review/MEMORY.md`를 읽는다. 없으면 첫 실행이므로 이 파일부터 만든다(아래 메모리 구조 참고).

인덱스에서 이번 대상과 관련된 knowledge 파일을 골라 읽고, 다음을 분류한다.

- **검증 완료 사실** — 검증일과 근거 URL이 있는 항목은 재검증하지 않는다. 단, 대상 파일이 그 검증일 이후 수정됐으면(`git log -1 --format=%ci -- <path>`) 다시 본다.
- **거짓 양성** — 과거에 "문제 같지만 아님"으로 판정된 패턴은 다시 지적하지 않는다.
- **미해결 항목** — 지난 실행에서 고치지 못한 것부터 우선 확인한다.

### 2. 면밀 분석

대상 폴더의 파일을 실제로 읽는다. 요약본이나 기억에 의존하지 않는다.

읽으면서 "검증 가능한 주장" 목록을 만든다. 주장 하나 = 한 줄:

```
- [파일:라인] 주장: <무엇이라고 적혀 있나/구현돼 있나>
  검증 축: <AWS 정합성 | 코드 정합성 | 내부 정합성>
```

예: "MSK Serverless는 IAM 인증만 지원한다"(AWS 축), "api-agent는 8086 포트"(코드 축), "observability 다이어그램의 OTLP 경로가 terraform observability 모듈과 일치"(내부 축).

### 3. AWS MCP 정합성 검증

AWS 관련 주장은 기억으로 판정하지 않고 MCP로 공식 문서를 확인한다. MCP 도구는 지연 로드되므로 먼저 ToolSearch로 스키마를 불러온다 (예: `select:mcp__aws-docs__search_documentation,mcp__aws-docs__read_documentation`).

- 1순위: `mcp__aws-docs__search_documentation` → `read_sections`/`read_documentation`. 근거 URL을 반드시 남긴다.
- 보조: deploy-on-aws 플러그인의 `awsknowledge`(문서 검색), `awspricing`(비용 주장 검증 — `analyze_terraform_project`, `get_pricing`). `awsiac`는 CloudFormation/CDK용이라 이 레포(Terraform)에는 거의 안 쓴다.
- 문서로 확인 못 한 주장은 "미확인"으로 남기고 사실처럼 판정하지 않는다 (CLAUDE.md 외부 자료 참조 원칙).

같은 주장을 매번 다시 찾지 않도록, 확인한 사실은 근거 URL·검증일과 함께 knowledge에 적는다(6단계).

### 4. 코드 대조 검증

devops 산출물이 애플리케이션 현실과 맞는지 실제 코드에서 확인한다. 대조 지점의 예:

- 서비스 목록·포트: 각 `api-*/build.gradle`(bootJar 여부), `application*.yml`의 `server.port` ↔ terraform `ecs-service` 정의·다이어그램
- 프로필: `local`/`dev`/`beta`/`prod` ↔ envs 구성
- Kafka·Redis·DB: `application*.yml`의 연결 설정 ↔ `msk-*`, `elasticache-valkey`, `aurora-mysql` 모듈
- 관측: `monitoring/` 설정, OTel 의존성 ↔ `observability` 모듈, `results/08-observability.md`, observability 다이어그램
- 내부 호출 규약(X-Internal-Api-Key), gateway 라우팅 ↔ security 다이어그램·`06-security-and-iam.md`

### 5. 즉시 개선 (Surgical Changes 유지)

발견 즉시 고치는 것이 기본이다. 단, 영향 등급으로 가른다.

- **즉시 수정** — 문서·다이어그램(.drawio, 가능하면 .png 재추출은 보고만)·주석·reminder의 틀린 내용, terraform의 명백한 결함(오타, 잘못된 참조, validate 실패). 고치고 나서 `terraform validate`(모듈이면 해당 env에서)로 확인한다.
- **수정하되 검증 필수** — plan diff를 만드는 terraform 변경. `terraform plan`으로 의도한 변경만 뜨는지 확인한다. 자격증명·도구가 없어 plan을 못 돌리면 적용하지 말고 보고로 돌린다.
- **보고 후 승인 대기** — 리소스 destroy/replace를 유발하는 변경, 비용이 늘어나는 변경, `00-cross-cutting-matrix.md` 정의를 바꾸는 변경.

고친 줄은 전부 발견한 결함으로 곧장 설명돼야 한다. 인접 코드 정리·리팩토링을 끼워 넣지 않는다(그건 `refactor-terraform` 몫). envs 공통 `.tf`를 고치면 dev/beta/prod 세 곳에 동일하게 적용한다. 커밋은 사용자가 시킬 때만 한다.

### 6. 메모리 축적

실행을 마치면 `devops/review/` 아래에 결과를 남긴다. 구조:

```
devops/review/
├── MEMORY.md                  # 인덱스 — knowledge·runs 한 줄 요약 목록
├── knowledge/<주제>.md        # 주제별 누적 지식 (예: msk.md, ecs-ports.md, observability.md)
└── runs/<YYYYMMDDHHMMSS>.md   # 실행 1회 보고서
```

knowledge 파일 항목 형식 — 세 종류를 구분해 적는다:

```markdown
## 검증 완료
- <사실 한 줄> — 근거: <공식 문서 URL> (검증일 YYYY-MM-DD, 대상: <파일 경로>)

## 거짓 양성 (다시 지적하지 말 것)
- <문제처럼 보이는 패턴> — 실제로는: <왜 의도된 설계인지> (판정일 YYYY-MM-DD)

## 미해결
- <발견했지만 못 고친 것> — 이유: <승인 대기 | 도구 없음 | ...>
```

runs 보고서에는 대상, 검증한 주장 수, 고친 것, 보고로 남긴 것, 메모리에 새로 적은 것을 적는다. 새 knowledge 파일이나 항목을 만들면 `MEMORY.md` 인덱스에 한 줄을 추가한다. 이미 있는 항목과 겹치면 새로 만들지 말고 그 항목을 갱신한다. 틀렸다고 판명된 기존 항목은 지운다.

### 7. 보고

한국어로, CLAUDE.md의 "사람이 검증하는 텍스트 작성 규칙"을 지켜 쓴다.

- 고친 것: 무엇이 왜 틀렸고 어떻게 고쳤는지, 검증 결과(validate/plan)
- 승인 대기: 파괴적·비용 변경 후보와 근거
- 미확인: 공식 문서로 판정 못 한 주장
- 메모리: 이번에 쌓인 지식 요약 (다음 실행이 무엇을 건너뛰게 되는지)
