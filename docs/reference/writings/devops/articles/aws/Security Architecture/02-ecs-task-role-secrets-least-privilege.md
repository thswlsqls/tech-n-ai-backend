# 자물쇠 여섯 개와 화살표가 없는 시크릿 하나 — ECS Task Role로 최소 권한을 코드에 새긴 기록

## SEO 제목 후보

- **ECS Task Role과 Task Execution Role, 무엇이 어떻게 다른가 — 6개 마이크로서비스로 나눠본 최소 권한 설계** — Fargate에서 서비스마다 IAM 권한을 쪼개려는데 두 역할의 경계가 헷갈리는 백엔드·플랫폼 엔지니어를 위한 글입니다.
- **Secrets Manager 시크릿을 서비스별로 최소 권한으로 나누는 법 — GetSecretValue와 KMS Decrypt가 함께 필요한 이유** — 시크릿 읽기 권한을 서비스 단위로 좁히면서 KMS 키 권한까지 같이 챙겨야 하는지 궁금한 인프라 엔지니어를 위한 글입니다.
- **아직 켜지 않은 기능의 시크릿은 만들지 않는다 — Terraform 조건부 생성으로 읽는 최소 권한** — `enable_amplify=false`처럼 기능 플래그로 자격증명 리소스 자체를 만들지 않는 패턴이 최소 권한과 어떻게 이어지는지 보고 싶은 DevOps·SRE를 위한 글입니다.

---

인프라 보안 다이어그램(`devops/aws/prod/security.png`)을 다시 들여다보다가, 상자 하나에 눈이 오래 머물렀습니다. "Runtime trust boundary(least-privilege ECS task roles)"라고 적힌 상자 안에는 서비스마다 자물쇠 아이콘이 하나씩, 모두 여섯 개가 들어 있습니다. 화살표를 따라가 보면 api-gateway는 SSM Parameter 하나만 읽고, api-chatbot은 `openai-api-key`와 `mongodb-uri` 두 개만 읽습니다. 서비스마다 뻗어 나오는 화살표의 개수와 방향이 다 다릅니다.

그런데 그 상자 바깥, "Secrets Manager(per-env)" 쪽에 회색으로 처리된 아이콘이 하나 있습니다. `github-pat-amplify`라는 시크릿인데, 세 환경(dev·beta·prod) 어느 Task Role에서도 이 아이콘으로 향하는 화살표가 없습니다. 다른 시크릿은 전부 어떤 서비스가 읽는지 화살표로 연결돼 있는데, 이것만 아무와도 이어져 있지 않습니다. "읽는 사람이 없는 시크릿"이 굳이 다이어그램에 그려져 있는 이유가 궁금해서 Terraform 코드를 따라 들어가 봤습니다. 이 글은 그 과정에서 정리한, 여섯 개 서비스가 각각 무엇을 읽도록 코드에 새겨져 있는지에 대한 기록입니다.

## 이미지를 받아오는 권한과 코드가 쓰는 권한을 나누는 이유

ECS에서 컨테이너 하나를 띄우려면 서로 성격이 다른 IAM 역할 두 개가 관여합니다. 처음 이 구조를 봤을 때는 왜 역할을 두 개나 두는지 잘 와닿지 않았는데, 각 역할의 자격증명을 "누가 쥐고 있는가"를 기준으로 보면 경계가 분명해집니다.

먼저 Task Execution Role이 있습니다. Amazon ECS 공식 문서는 이 역할을 "ECS 컨테이너 에이전트와 Fargate 에이전트가 사용자를 대신해 AWS API를 호출하도록 권한을 부여하는 역할"이라고 설명합니다. 컨테이너 이미지를 Amazon ECR 프라이빗 저장소에서 받아오고, `awslogs` 드라이버로 컨테이너 로그를 CloudWatch Logs로 보내고, 태스크 정의가 참조하는 Secrets Manager 시크릿이나 SSM Parameter Store 값을 컨테이너가 시작하기 전에 환경 변수로 넣어 주는 일이 여기에 속합니다. 중요한 점은, 공식 문서가 분명히 밝히듯 이 권한은 에이전트에게만 임시 자격증명 형태로 전달되고 컨테이너 안에서 도는 애플리케이션 코드는 이 자격증명을 직접 만질 수 없다는 것입니다.

그다음이 Task Role입니다. 같은 문서 계열의 Task IAM Role 설명은 이 역할을 "태스크 안에서 실행되는 컨테이너에 부여되어, 애플리케이션 코드가 다른 AWS 서비스를 호출할 때 쓰는 권한"이라고 정의합니다. 컨테이너 안 코드가 AWS SDK나 CLI로 S3에 접근하거나 Kafka 클러스터에 붙을 때 사용하는 자격증명이 바로 이것입니다. 공식 문서는 이 방식의 이점으로 관심사 분리와 감사 가능성을 듭니다. 특히 감사 쪽이 흥미로운데, Task Role로 발급된 자격증명에는 `taskArn`이라는 컨텍스트가 세션에 붙어서, CloudTrail 로그를 보면 어떤 태스크에 그 권한이 발급됐는지 되짚을 수 있다고 적혀 있습니다.

정리하면 Execution Role은 "컨테이너를 띄우기까지의 준비 작업"에 필요한 권한이고, Task Role은 "띄워진 컨테이너 안 코드가 실제로 일하는 동안" 쓰는 권한입니다. 이 프로젝트의 `envs/prod/main.tf`를 보면 Task Execution Role은 환경마다 딱 한 개만 두고 여섯 서비스가 공유합니다. 관리형 정책인 `AmazonECSTaskExecutionRolePolicy`에 더해, 인라인으로 `{project}/{env}/*` 경로의 Secrets·SSM 읽기 권한과 데이터·s3-app KMS 키의 Decrypt 권한이 붙어 있습니다(`envs/prod/main.tf:81`, `:199`). 이미지를 받고 로그를 보내고 시크릿을 주입하는 준비 작업은 어느 서비스든 똑같으니, 이 부분은 하나로 공유해도 최소 권한 원칙에서 크게 벗어나지 않는다는 판단으로 읽힙니다.

권한을 서비스별로 잘게 쪼갠 쪽은 반대편, Task Role입니다. `envs/prod/task_roles.tf`에는 여섯 서비스에 대응하는 여섯 개의 Task Role이 각각 별도 모듈로 선언돼 있고, 여기서부터 다이어그램의 자물쇠 여섯 개가 시작됩니다.

## 여섯 개의 자물쇠 — 서비스별로 무엇을 읽도록 새겨져 있는가

`task_roles.tf`를 한 줄씩 따라가며 각 서비스의 인라인 정책이 어떤 리소스를 대상으로 어떤 액션을 허용하는지 확인했습니다. 여섯 서비스의 권한을 나란히 놓으면 이렇습니다.

| 서비스 | Task Role이 읽거나 호출하는 대상 | KMS Decrypt |
|---|---|---|
| api-gateway | SSM Parameter read만 | — |
| api-auth | Aurora master secret · `jwt-signing-key` read, `rds-db:connect`(dbuser `api_auth`) | auth · data |
| api-chatbot | `openai-api-key` · `mongodb-uri` read | ai |
| api-agent | `kafka-cluster:*`(Connect·Describe·Read·WriteData 등), `mongodb-uri` read | — |
| api-bookmark | `rds-db:connect`(dbuser `api_bookmark`), `elasticache-auth-token` read | data |
| api-emerging-tech | `openai-api-key` read | ai |

이 표가 흥미로운 것은, 각 줄이 그 서비스가 시스템에서 맡은 역할과 거의 그대로 겹친다는 점입니다. 권한을 나열한 목록이 아니라 서비스의 책임을 옆에서 설명해 주는 목록처럼 읽힙니다.

api-gateway는 외부 트래픽의 진입점입니다. 라우팅과 JWT 검증이 주된 일이고 자체적으로 데이터를 저장하지는 않으니, Task Role에는 SSM Parameter를 읽는 권한 하나만 있습니다. 여섯 서비스 중 가장 권한이 얇습니다.

api-auth는 인증을 맡으므로 다루는 자격증명의 밀도가 가장 높습니다. Aurora의 마스터 시크릿과 `jwt-signing-key`를 읽고, `rds-db:connect` 권한으로 `api_auth`라는 데이터베이스 사용자로 Aurora에 IAM 인증 접속을 합니다(`task_roles.tf:83`). 여기서 KMS 권한이 왜 둘(auth·data)인지가 봉투 암호화로 자연스럽게 설명됩니다. `jwt-signing-key`는 `{env}-auth` KMS 키로 암호화돼 있고, Aurora 데이터 계열 시크릿은 `{env}-data` 키에 묶여 있습니다. Secrets Manager는 시크릿을 KMS 키로 암호화해 저장하므로, 시크릿을 읽으려면 `secretsmanager:GetSecretValue`만으로는 부족하고 그 시크릿을 감싼 KMS 키에 대한 `kms:Decrypt` 권한이 함께 있어야 값이 실제로 복호화됩니다. api-auth가 두 계열의 시크릿을 읽으니 두 KMS 키의 Decrypt가 필요한 것이고, 이건 설계자가 권한을 후하게 준 게 아니라 읽어야 하는 시크릿이 두 키에 나뉘어 있어서 생긴 필연입니다.

api-chatbot과 api-emerging-tech는 둘 다 AI 기능을 쓰지만 필요한 범위가 다릅니다. RAG 챗봇인 api-chatbot은 OpenAI 키(`openai-api-key`)와 MongoDB 접속 문자열(`mongodb-uri`)을 둘 다 읽고, 두 시크릿의 KMS 키인 `{env}-ai`에 대한 Decrypt 권한을 가집니다. 반면 api-emerging-tech는 `openai-api-key`만 읽고 `mongodb-uri`는 읽지 않습니다. 같은 "AI를 쓰는 서비스"라도 실제로 만지는 시크릿의 목록이 다르면 Task Role의 권한도 다르게 그어지는 셈입니다. 한 가지 눈에 띈 것은 api-chatbot의 정책에 Bedrock 권한이 없다는 점인데(`task_roles.tf:102`, 주석 `D-12`), 챗봇이 OpenAI를 기본 LLM 제공자로 쓰고 Bedrock 경로를 아직 열지 않았다는 결정이 IAM 정책에도 그대로 반영돼 있는 것으로 보입니다.

api-agent는 이벤트를 다루는 서비스답게 MSK 관련 권한이 붙어 있습니다. `kafka-cluster:Connect`, `DescribeCluster`, `DescribeTopic`, `DescribeGroup`, `AlterGroup`, `ReadData`, `WriteData` 같은 액션이 나열돼 있고(`task_roles.tf:162`~`:168`), 여기에 `mongodb-uri` 읽기가 더해집니다. api-bookmark는 `api_bookmark` 사용자로 Aurora에 IAM 인증 접속을 하고(`task_roles.tf:214`), ElastiCache 인증 토큰(`elasticache-auth-token`)을 읽으며, 데이터 계열 KMS 키의 Decrypt 권한을 가집니다.

여기서 한 가지 짚어 둘 부분이 있습니다. 앞서 Task Execution Role도 `{project}/{env}/*` 아래 시크릿을 폭넓게 읽을 수 있다고 했는데, 그렇다면 Task Role에도 굳이 개별 시크릿 읽기 권한을 또 준 이유가 무엇이냐는 질문이 생깁니다. 코드만으로 확실히 말할 수 있는 것은, 시크릿을 얻는 경로가 두 갈래라는 사실입니다. 하나는 태스크 정의가 시크릿을 참조하면 Execution Role이 컨테이너 시작 전에 값을 환경 변수로 주입하는 경로이고, 다른 하나는 컨테이너 안 코드가 실행 중에 직접 AWS SDK로 `GetSecretValue`를 호출하는 경로입니다. Execution Role 쪽은 어느 서비스의 태스크 정의든 처리해야 하므로 대상이 `*`로 넓지만, Task Role 쪽은 그 서비스가 실제로 읽는 시크릿의 ARN만 정확히 짚어 좁혀 두었습니다. 어느 경로가 실제로 쓰이는지는 애플리케이션 코드까지 봐야 확정할 수 있으니 여기서 단정하지는 않겠습니다. 다만 넓은 공유 권한 옆에, 서비스별로 좁게 그은 권한이 겹으로 존재한다는 구조 자체가 최소 권한을 이중으로 새겨 둔 흔적으로 읽힙니다.

## 화살표가 없는 시크릿 — 아직 만들지 않았기 때문에 비어 있다

다시 처음의 회색 아이콘, `github-pat-amplify`로 돌아옵니다. 이 시크릿을 향하는 화살표가 없는 이유는 코드를 열어 보니 간단했습니다. 애초에 이 시크릿 리소스가 세 환경 모두에서 만들어지지 않았기 때문입니다.

`envs/prod/frontend.tf`를 보면 시크릿 선언이 이렇게 돼 있습니다(`frontend.tf:7`~`:8`).

```hcl
resource "aws_secretsmanager_secret" "github_pat" {
  count = var.enable_amplify ? 1 : 0
  name  = "${var.project}/${var.environment}/github-pat-amplify"
  # ...
}
```

여기서 `count = var.enable_amplify ? 1 : 0`이 핵심입니다. `enable_amplify` 변수가 참이면 리소스를 한 개 만들고, 거짓이면 개수를 0으로 두어 아예 만들지 않습니다. 세 환경 모두 아직 Amplify 프런트엔드 호스팅을 켜지 않아 이 값이 거짓이고, 그래서 시크릿 리소스 자체가 존재하지 않습니다. 다이어그램의 회색 아이콘은 "권한은 있는데 아직 안 쓰는 시크릿"이 아니라 "설계상 자리는 잡혀 있지만 아직 생성되지 않은 시크릿"을 표시한 것이었습니다. 이 시크릿을 참조하는 Amplify 앱 모듈(`frontend.tf:32`, `:60`)도 같은 `count` 조건으로 함께 묶여 있어서, 기능을 켜는 순간 시크릿과 그것을 쓰는 앱이 한 덩어리로 생성되도록 돼 있습니다.

이 지점을 결함으로 부르고 싶지는 않습니다. 오히려 반대에 가깝다고 봅니다. 최소 권한을 이야기할 때 흔히 "필요한 권한만 준다"에 초점을 맞추지만, 그 앞에 "필요하지 않은 자격증명은 애초에 만들어 두지 않는다"는 단계가 있습니다. GitHub Personal Access Token은 잘못 노출되면 저장소 접근으로 이어질 수 있는 민감한 자격증명이라, 아직 쓰지도 않는 시점에 미리 만들어 두면 그만큼 관리하고 지켜야 할 대상이 하나 늘어납니다. 기능 플래그로 시크릿 리소스 자체를 조건부로 두는 방식은, 안 쓰는 자격증명을 미리 만들어 공격 표면으로 남겨 두지 않겠다는 태도의 연장으로 읽힙니다.

비슷한 결의 결정이 하나 더 있습니다. Secrets Manager 표(`architecture-facts.md` §5)를 보면 다른 시크릿은 모두 명시적으로 선언돼 있는데, Aurora의 마스터 비밀번호만 별도 시크릿 리소스가 없습니다. 대신 Aurora의 Managed Master User Password 기능이 비밀번호를 자동으로 만들고 관리합니다(`secrets.tf` 주석). 사람이 만들어 관리하는 시크릿의 개수를 하나라도 줄이는 방향인데, `github-pat-amplify`를 조건부로 두는 것과 방향이 같습니다. 만들어 관리할 자격증명 자체를 줄일 수 있으면 줄인다는 것입니다.

한편 나머지 시크릿들은 초깃값을 어떻게 다루는지도 눈여겨볼 만합니다. `jwt-signing-key`, `openai-api-key`, `mongodb-uri` 모두 Terraform이 처음 만들 때는 `ghp_PLACEHOLDER_REPLACE_WITH_REAL_PAT` 같은 자리표시자 문자열을 넣어 두고, `lifecycle.ignore_changes = [secret_string]`으로 실제 값의 변경을 Terraform이 덮어쓰지 않도록 보호합니다(`secrets.tf:32` 등). 실제 비밀 값은 보안 담당자가 별도로 넣고, IaC 코드에는 진짜 시크릿이 절대 커밋되지 않도록 경계를 그은 것입니다. 이것도 넓게 보면 "코드 저장소라는 표면에 민감한 값을 남기지 않는다"는 같은 원칙의 다른 표현입니다.

## 표 하나가 사고 조사의 범위를 좁힌다

여섯 서비스의 권한을 표로 펼쳐 놓고 나니, 이 구조가 실무에서 어떤 값을 하는지가 조금 더 또렷해졌습니다.

만약 어느 날 특정 서비스의 컨테이너 자격증명이 유출됐다는 신호가 잡혔다고 해 봅니다. 이때 가장 먼저 답해야 하는 질문은 "그 자격증명으로 무엇까지 할 수 있었는가"입니다. 권한이 모든 서비스에 뭉뚱그려 하나로 부여돼 있었다면, 이 질문의 답은 "시스템이 접근하는 거의 모든 것"이 되어 버립니다. 그런데 이 프로젝트처럼 Task Role이 서비스별로 쪼개져 있으면, 예컨대 유출된 것이 api-emerging-tech의 자격증명이라면 그 영향 범위는 `openai-api-key` 읽기와 `{env}-ai` 키의 Decrypt로 곧장 좁혀집니다. api-emerging-tech의 Task Role에는 Aurora 접속 권한도, MongoDB 접속 문자열 읽기 권한도, Kafka 권한도 없으니, 조사 초기에 "이 서비스는 애초에 그쪽을 건드릴 수 없었다"를 표 한 줄로 바로 배제할 수 있습니다.

앞서 공식 문서가 Task Role의 이점으로 든 감사 가능성도 여기에 맞물립니다. CloudTrail 로그가 `taskArn` 컨텍스트로 어느 태스크가 그 권한을 썼는지 되짚어 주기 때문에, 권한이 서비스별로 나뉘어 있을수록 "누가 이 API를 호출했는가"를 서비스 단위로 좁혀 읽을 수 있습니다. 권한을 잘게 나누는 일은 평소에는 번거롭게만 느껴지지만, 뭔가 잘못됐을 때 조사 범위를 좁혀 주는 형태로 값을 돌려준다는 것을 이번에 표를 만들며 다시 실감했습니다.

## 마치며

이 여섯 개의 자물쇠를 코드로 따라가기 전까지는, 최소 권한이라는 말을 "필요한 것만 허용한다"는 한 문장으로만 기억하고 있었습니다. 그런데 `task_roles.tf`와 `secrets.tf`, `frontend.tf`를 나란히 놓고 보니, 이 원칙이 코드 안에서는 여러 겹으로 나타나고 있었습니다. 서비스마다 읽는 시크릿의 ARN을 정확히 짚어 좁히는 겹이 있고, 시크릿을 읽으려면 그 시크릿을 감싼 KMS 키의 Decrypt까지 함께 있어야 한다는 봉투 암호화의 겹이 있고, 아직 쓰지 않는 기능의 자격증명은 리소스 자체를 만들지 않는 겹이 있었습니다. 그리고 IaC 코드에는 자리표시자만 남기고 진짜 값은 밖에서 넣는 겹까지 있었습니다.

개인적으로 남은 습관은, "이 서비스에 이 권한을 주자"를 결정할 때 그 권한이 그 서비스의 역할과 한 줄로 겹쳐 설명되는지 스스로 물어보는 것입니다. api-gateway가 SSM 하나만 읽는 것, api-auth가 두 KMS 키를 함께 쥐는 것은 모두 그 서비스가 하는 일로 자연스럽게 설명됩니다. 반대로 어떤 권한이 서비스의 역할로 설명되지 않는다면, 그건 남아 있는 이유를 다시 물어야 할 신호일 수 있습니다. 화살표가 없는 시크릿 하나에서 시작해 여섯 개의 자물쇠를 다 열어 본 끝에, 최소 권한이란 결국 "권한 하나하나를 그 주인의 역할로 설명할 수 있는 상태"에 가깝다는 정리를 얻었습니다.

## 시리즈 인용 관계

이 글은 [01 — 장기 자격증명 없는 CI/CD, GitHub Actions OIDC 페더레이션](./01-github-actions-oidc-four-roles.md)이 다룬 배포 시점의 네 개 역할과 같은 최소 권한 원칙을, 이번에는 컨테이너가 실행되는 동안 상시 쓰는 런타임 여섯 개 Task Role로 옮겨 온 것입니다. 배포할 때 쓰는 권한(01)과 실행 중에 쓰는 권한(02)은 주체도 수명도 다르지만, "역할을 잘게 나눠 각 권한을 그 주인의 일로 설명한다"는 태도는 같습니다. 그리고 이 표에서 api-agent에만 붙어 있던 `kafka-cluster:*` 권한은, [03 — dev에는 없는 MSK를 향한 권한](./03-orphaned-kafka-permission-in-dev.md)에서 "이 권한이 정작 MSK가 없는 dev 환경에서는 무엇을 가리키는가"라는 질문으로 이어집니다.

## 참고한 공식 문서

- Amazon ECS 태스크 실행 IAM 역할(Execution Role의 정의와 역할, 에이전트에게만 자격증명이 전달된다는 설명): https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html
- Amazon ECS 태스크 IAM 역할(Task Role의 정의, 관심사 분리·감사 가능성, `taskArn` 컨텍스트): https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html
- AWS Secrets Manager란 무엇인가(시크릿의 KMS 암호화 저장 개념): https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html
- Aurora MySQL의 IAM 데이터베이스 인증(`rds-db:connect` 정책): https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/UsingWithRDS.IAMDBAuth.IAMPolicy.html

이 글에서 다룬 여섯 서비스의 Task Role 권한, `github-pat-amplify`의 조건부 생성, 시크릿의 자리표시자 처리는 외부 문서가 아니라 이 저장소의 Terraform 코드(`devops/terraform/envs/prod/task_roles.tf`, `secrets.tf`, `frontend.tf`)와 `devops/aws/architecture-facts.md` §5, `devops/aws/prod/security.png` 다이어그램에서 직접 확인한 내용입니다.
