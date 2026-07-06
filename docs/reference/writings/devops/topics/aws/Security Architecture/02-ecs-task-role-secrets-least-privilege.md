# 02. Task Role 6개, 시크릿 5개 — 런타임 신뢰 경계가 최소 권한을 코드로 강제하는 법

> 1차 소스: [`devops/aws/{dev,beta,prod}/security.png`](../../../../../../../devops/aws/prod/security.png) · [`architecture-facts.md` §5 IAM·Secrets Manager](../../../../../../../devops/aws/architecture-facts.md)

## 한줄 요약(Hook)

> "Runtime trust boundary" 상자 안에는 서비스마다 자물쇠 아이콘이 하나씩, 총 6개가 있다. 화살표를 따라가 보면 api-gateway는 SSM Parameter 하나만 읽고, api-chatbot은 `openai-api-key`와 `mongodb-uri` 두 개만 읽는다. 그런데 상자 밖 회색 아이콘 하나, `github-pat-amplify`는 어느 Task Role에서도 화살표가 뻗어 나오지 않는다 — "만들어지지 않은 시크릿"이 다이어그램에 그려져 있는 이유는 무엇인가.

## 핵심 질문

- 6개 ECS Task Role은 각각 정확히 어떤 시크릿·API만 읽도록 제한돼 있는가?
- Task Execution Role(공유 1개)과 Task Role(서비스별 6개)의 책임은 어떻게 나뉘는가?
- `github-pat-amplify` 시크릿이 세 환경 모두에서 "생성되지 않음(DISABLED)"으로 그려지는 이유는 무엇이며, 이것이 코드에 어떻게 반영돼 있는가?

## 다루는 관점

- ✅ 구현 — IAM 인라인 정책과 Secrets Manager 시크릿의 서비스별 매핑
- ✅ 운영 — 최소 권한을 서비스 단위로 쪼갠 결과가 실제로 어떻게 읽히는가

## 근거

- 다이어그램: prod `security.png`의 "Runtime trust boundary(least-privilege ECS task roles)" 상자 — 6개 Task Role 노드와 각각의 읽기 대상 라벨(예: `api-auth Task Role — Aurora secret + jwt-signing-key, rds-db:connect, KMS(auth,data)`), "Secrets Manager(per-env)" 상자의 5개 시크릿 노드, `github-pat-amplify` 노드의 회색 처리("DISABLED (enable_amplify=false)")
- `architecture-facts.md` §5 IAM(174~183행) — Task Execution Role(env당 1개 공유, `AmazonECSTaskExecutionRolePolicy` + 인라인 Secrets/SSM read·KMS Decrypt data·s3-app) / Task Role 6개별 권한: api-gateway(SSM Parameter read만) · api-auth(Aurora master secret+jwt-signing-key read, `rds-db:connect`(dbuser api_auth), KMS Decrypt auth·data) · api-chatbot(openai-api-key·mongodb-uri read, KMS Decrypt ai, Bedrock 권한 없음(D-12)) · api-agent(`kafka-cluster:*`, mongodb-uri read) · api-bookmark(`rds-db:connect`(dbuser api_bookmark), elasticache-auth-token read, KMS Decrypt data) · api-emerging-tech(openai-api-key read, KMS Decrypt ai)
- `architecture-facts.md` §5 Secrets Manager 표(191~199행) — `jwt-signing-key`(KMS `{env}-auth`) · `openai-api-key`(KMS `{env}-ai`) · `mongodb-uri`(KMS `{env}-data`) · `elasticache-auth-token`(enable_elasticache 시, KMS `{env}-data`) · `github-pat-amplify`(enable_amplify 시, KMS `{env}-auth`) — "Aurora master 비밀번호는 Managed Master User Password가 자동 생성(별도 secret 리소스 없음)"

## 타깃 독자 & 난이도

- MSA에서 서비스별로 IAM 권한을 세분화하는 설계를 검토하는 백엔드·플랫폼 엔지니어
- ★★★☆☆ (사전지식: IAM 인라인 정책, Secrets Manager 기본 개념, ECS 태스크 정의의 Execution Role·Task Role 차이)

## 예상 분량

- 보통 (~4,000자)

## 글 아웃라인

1. **들어가며 — 화살표가 없는 시크릿 하나**
   - `github-pat-amplify` 노드만 다른 색으로 그려져 있다는 관찰에서 출발
2. **Task Execution Role과 Task Role — 이미지를 받아오는 권한과 애플리케이션이 쓰는 권한을 나누는 이유**
   - 공유 1개(Execution) vs 서비스별 6개(Task)의 책임 경계
3. **표로 펼치기 — 6개 서비스가 각각 읽는 시크릿·API 권한**
   - `architecture-facts.md`의 서비스별 권한을 매트릭스로 재구성
4. **`github-pat-amplify`가 세 환경 모두에서 비어 있는 이유**
   - `enable_amplify=false`가 시크릿 리소스 자체를 만들지 않는다는 조건부 생성 패턴, 그리고 Aurora master 비밀번호처럼 "자동 생성돼 별도 관리 대상이 아닌" 시크릿과의 대비
5. **결론 — 서비스 단위로 쪼갠 권한이 사고 조사 범위를 좁히는 방식**
   - 특정 서비스의 자격증명이 유출됐을 때, "이 서비스가 애초에 읽을 수 있던 것"이 무엇인지 표 하나로 바로 답할 수 있다는 실무적 이점

## 참고할 1차 출처

- Amazon ECS 태스크 IAM 역할: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html
- Amazon ECS 태스크 실행 IAM 역할: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html
- AWS Secrets Manager란 무엇인가: https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html

## 시리즈 인용 관계

**[01 — GitHub Actions OIDC 페더레이션](./01-github-actions-oidc-four-roles.md)**이 다룬 CI/CD 4개 역할과 같은 최소 권한 원칙을 반복하지 않고, 컨테이너가 실행되는 동안 상시 쓰는 런타임 6개 Task Role로 초점을 옮긴다. 이 단편이 표로 정리한 api-agent의 `kafka-cluster:*` 권한은 **[03 — dev에는 없는 MSK를 향한 권한](./03-orphaned-kafka-permission-in-dev.md)**에서 "이 권한이 dev에서는 무엇을 가리키는가"라는 질문으로 이어진다.

## 작성 메모

- 6개 서비스의 권한을 단순히 나열하는 데 그치지 않고, 각 권한이 그 서비스의 역할(쓰기 담당, 읽기 담당, 이벤트 담당)과 어떻게 맞물리는지 설명한다.
- `github-pat-amplify`를 "결함"으로 다루지 않는다. 아직 켜지 않은 기능을 위해 조건부로 시크릿 리소스 자체를 만들지 않는 것은 불필요한 자격증명을 미리 만들어 두지 않는다는 최소 권한의 연장선이라는 톤을 유지한다.
