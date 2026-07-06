# 클러스터가 없는 환경에 남아 있는 Kafka 권한 — 리소스가 사라지면 IAM 정책은 무엇을 가리키는가

> 시리즈: security-architecture 다이어그램 읽기 — 최소 권한이 코드에 어떻게 남는가

---

## SEO 제목 후보

- **클러스터가 없는 환경에 남아 있는 Kafka 권한 — 리소스가 사라지면 IAM 정책은 무엇을 가리키는가** — dev·beta·prod에 같은 Terraform 모듈을 재사용하면서 IAM 정책이 리소스 존재 여부와 어떻게 얽히는지 알고 싶은 인프라 엔지니어에게
- **`kafka-cluster:Connect` 권한이 가리킬 대상이 없을 때 — MSK 미생성 환경의 IAM 정책 읽기** — MSK IAM 접근 제어와 리소스 ARN이 정책에서 어떻게 맞물리는지 확인하려는 백엔드 엔지니어에게
- **byte 단위로 같은 `.tf`가 환경마다 다른 IAM 정책을 만드는 이유 — 조건부 리소스와 `compact()`** — Terraform 조건부 모듈이 렌더링되는 정책을 어떻게 바꾸는지 궁금한 IaC 담당자에게

---

## 들어가며

AWS 보안 아키텍처 다이어그램을 dev, beta, prod 순서로 넘겨 보다가 dev 다이어그램에만 붙어 있는 노란 메모 한 줄에 눈이 멈췄습니다. 대략 이런 내용이었습니다. "MSK is OFF (enable_msk=false) — api-agent의 kafka-cluster 권한이 role에는 있지만 dev에는 이 권한이 향할 MSK 대상이 없다(unused)." 세 환경의 태스크 역할이 서로 같다는 주석도 함께 달려 있었습니다.

메모만 읽으면 이야기는 단순해 보입니다. 권한은 있는데 그 권한이 붙을 대상이 없으니, 아무 데도 쓰이지 않는 권한이 dev에 그대로 남아 있다는 것입니다. 그런데 정말로 "쓰이지 않는 권한이 코드에 방치돼 있는" 상황일까요. 이 메모가 가리키는 실제 코드를 열어 보면, 이야기는 메모가 압축한 것보다 한 겹 더 들어가 있습니다. api-agent의 IAM 정책은 고정된 와일드카드 권한을 복사해 둔 것이 아니라, MSK 클러스터가 존재할 때에만 실제 대상을 갖도록 짜여 있고, 클러스터가 없는 dev에서는 그 대상이 스스로 비워지도록 설계돼 있었습니다.

이 글에서는 그 한 겹을 따라가 보려 합니다. 같은 Terraform 파일이 환경마다 어떻게 다른 정책을 만들어 내는지, MSK의 IAM 접근 제어에서 `kafka-cluster` 권한이 실제로 무엇을 허용하는지, 그리고 대상 리소스가 없는 이 권한을 최소 권한 원칙의 눈으로 어떻게 판단해야 할지를, 저장소의 Terraform 코드와 AWS 공식 문서를 근거로 하나씩 짚어 보겠습니다. 결론부터 말하면 "이건 버그다"라거나 "전혀 문제없다"로 딱 잘라 말하기 어려운, 확인이 필요한 지점이 남습니다.

---

## 같은 `.tf` 파일이 환경마다 다른 정책을 만든다

이 프로젝트의 Terraform은 환경 구성 방식이 조금 특이합니다. `envs/dev`, `envs/beta`, `envs/prod` 세 폴더의 `.tf` 파일들은 byte 단위로 동일합니다. `diff`를 돌려 보면 차이가 없습니다. 환경별 차이는 오직 각 환경의 `terraform.tfvars`와 `variables.tf`의 default 값에서만 생깁니다. 다시 말해 "코드는 한 벌, 값만 세 벌"인 구조입니다.

이 구조를 처음 보면 "그럼 IAM 정책도 세 환경이 완전히 똑같겠구나"라고 생각하기 쉽습니다. 실제로 다이어그램의 주석도 세 환경의 태스크 역할이 서로 같다고 적어 두었습니다. 그런데 api-agent의 태스크 역할 정책을 정의하는 코드를 열어 보면, 같은 파일이 환경마다 다른 결과를 낳도록 짜여 있다는 것을 알 수 있습니다.

핵심은 `kafka-cluster` 권한 구문의 대상 리소스가 고정 문자열이 아니라 계산식이라는 점입니다. 코드는 대략 이런 모양입니다.

```hcl
resources = compact([
  try(module.msk[0].cluster_arn, ""),
  try("${module.msk[0].cluster_arn}/topic/${var.project}.conversation.*", ""),
  try("${module.msk[0].cluster_arn}/group/${var.project}.*", ""),
])
```

여기서 `module.msk`는 조건부로 생성되는 모듈입니다. `enable_msk`가 켜진 환경에서만 만들어지고, 꺼진 환경에서는 아예 생성되지 않습니다. beta와 prod는 `enable_msk=true`라 MSK 모듈이 존재하고, 그래서 `module.msk[0].cluster_arn`이 실제 클러스터 ARN을 돌려줍니다. 반면 dev는 `enable_msk`가 default인 false라서 MSK 모듈 자체가 만들어지지 않습니다. 이때 `module.msk[0]`을 참조하면 오류가 나는데, `try(...)`가 그 오류를 삼키고 빈 문자열을 돌려줍니다. 그리고 바깥을 감싼 `compact()`는 리스트에서 빈 문자열을 걷어냅니다. 결과적으로 dev에서는 이 리소스 리스트가 빈 배열이 됩니다.

정리하면 이렇습니다. 세 환경의 `.tf` 파일은 정말로 byte 단위로 같지만, 그 파일이 만들어 내는 IAM 정책 JSON은 환경마다 다릅니다. 허용하는 액션 목록은 셋 다 같아도, 그 액션이 향하는 대상 리소스는 클러스터가 있느냐 없느냐에 따라 달라집니다. beta·prod에서는 실제 클러스터 ARN과 토픽·컨슈머 그룹 패턴이 채워지고, dev에서는 그 자리가 비워집니다. "소스는 하나인데 렌더링 결과는 환경마다 다르다"는 것이 이 코드가 조건부 모듈과 `compact()`로 만들어 내는 결과입니다.

여기서 다이어그램 메모를 다시 읽으면 인상이 조금 바뀝니다. 메모는 "권한이 대상 없이 남아 있다(unused)"고 적었는데, 코드를 보면 이건 방치의 흔적이 아니라 의도된 동작에 가깝습니다. 정책 작성자는 대상 리소스를 클러스터의 실제 ARN에서 끌어오도록 만들어서, 클러스터가 없으면 대상도 자연스럽게 비워지도록 해 두었습니다. 즉 "쓰지 않는 권한을 그냥 복사해 둔" 것이 아니라, "쓸 대상이 생기면 그때 대상이 채워지도록" 짜 둔 셈입니다.

---

## `kafka-cluster` 권한이 실제로 허용하는 일

그렇다면 이 권한이 채워졌을 때(beta·prod) 실제로 무엇을 허용하는지부터 정확히 봐 두는 게 좋겠습니다. api-agent 정책이 나열하는 액션은 와일드카드 하나가 아니라 일곱 개의 구체적인 액션입니다. `kafka-cluster:Connect`, `kafka-cluster:DescribeCluster`, `kafka-cluster:DescribeTopic`, `kafka-cluster:DescribeGroup`, `kafka-cluster:AlterGroup`, `kafka-cluster:ReadData`, `kafka-cluster:WriteData`입니다. 이벤트를 프로듀스하고 컨슘하는 서비스에 필요한 만큼만 고른 목록입니다.

이 액션들이 어떻게 맞물리는지는 AWS 공식 문서에서 확인할 수 있습니다. `kafka-cluster`는 "Apache Kafka APIs for Amazon MSK clusters"의 서비스 접두어이고, 각 액션은 Apache Kafka의 ACL 하나에 대응합니다. 예를 들어 `ReadData`는 "Grants permission to read data from topics on a cluster, equivalent to Apache Kafka's READ TOPIC ACL", `WriteData`는 "Grants permission to write data to topics on a cluster, equivalent to Apache Kafka's WRITE TOPIC ACL"로 설명돼 있습니다. 그리고 `Connect`는 "Grants permission to connect and authenticate to the cluster", 즉 클러스터에 접속하고 인증하는 가장 기초가 되는 액션입니다.

문서에서 눈여겨볼 부분은 이 액션들 사이의 의존 관계입니다. 대부분의 액션은 "Dependent actions" 항목에 `kafka-cluster:Connect`를 함께 두고 있습니다. `ReadData`는 `kafka-cluster:AlterGroup`, `kafka-cluster:Connect`, `kafka-cluster:DescribeTopic`을 함께 요구하고, `WriteData`는 `kafka-cluster:Connect`와 `kafka-cluster:DescribeTopic`을 함께 요구합니다. 데이터를 읽거나 쓰려면 먼저 클러스터에 접속(`Connect`)이 성립해야 한다는 뜻입니다. api-agent 정책이 `Connect`부터 `ReadData`·`WriteData`까지 한 세트로 들고 있는 것은 이 의존 관계를 그대로 반영한 구성입니다.

리소스 쪽도 중요합니다. 공식 문서는 `kafka-cluster` 액션이 붙을 수 있는 리소스 유형을 네 가지로 정의합니다. cluster, topic, group, transactional-id입니다. 그리고 각 ARN 형식을 보면 흥미로운 지점이 있습니다. cluster 리소스의 ARN은 `arn:${Partition}:kafka:${Region}:${Account}:cluster/${ClusterName}/${ClusterUuid}` 형태이고, topic·group도 이 뒤에 이름을 덧붙이는 구조입니다. 여기서 `${ClusterUuid}`는 클러스터를 만들 때 함께 생성되는 고유 식별자입니다. 다시 말해 특정 클러스터를 정확히 가리키는 ARN은 그 클러스터가 실제로 만들어진 뒤에야 확정됩니다.

이 사실이 앞 절의 코드와 자연스럽게 이어집니다. api-agent 정책이 대상 리소스를 고정 문자열로 박아 둘 수 없었던 이유가 여기에 있습니다. 클러스터 ARN에는 생성 시점에야 정해지는 UUID가 들어가므로, 정책은 그 값을 클러스터 모듈의 출력(`module.msk[0].cluster_arn`)에서 끌어오는 수밖에 없습니다. 그리고 그 끌어옴이 topic·group ARN까지 이어져서, 이 서비스가 접근할 토픽은 `{project}.conversation.*` 패턴, 컨슈머 그룹은 `{project}.*` 패턴으로 좁혀집니다. 액션도 일곱 개로 골랐고, 리소스도 클러스터·토픽·그룹 패턴으로 좁혔으니, 클러스터가 존재하는 환경에서는 나름대로 대상을 조인 최소 권한 구성이라고 읽을 수 있습니다.

---

## dev에서 이 권한이 가리키는 대상

이제 클러스터가 없는 dev로 돌아옵니다. 앞서 봤듯 dev에서는 리소스 리스트가 빈 배열입니다. 액션 일곱 개는 정책 구문에 그대로 있는데, 그 액션이 향할 대상이 하나도 없는 상태입니다.

이 상태를 이해하려면 IAM 정책이 대상 리소스와 맺는 관계를 짚어야 합니다. AWS 공식 문서는 정책의 `Resource` 요소를 이렇게 설명합니다. "The `Resource` element in an IAM policy statement defines the object or objects that the statement applies to." 그리고 리소스는 ARN으로 지정하며, "Although the ARN format varies you always use an ARN to identify a resource"라고 적혀 있습니다. 여기서 중요한 점은, 아이덴티티 기반 정책의 `Resource`는 "이 권한이 어떤 ARN에 적용되는가"를 적어 두는 자리일 뿐, 그 ARN이 가리키는 리소스가 실제로 존재하는지를 정책이 스스로 확인하지는 않는다는 것입니다. 정책은 "만약 이런 대상에 이런 행동을 하려 하면 허용한다"는 규칙을 적어 둘 뿐이고, 대상이 실제로 있는지는 그 행동을 시도하는 순간에 판가름 납니다.

MSK의 IAM 접근 제어 방식이 이 판가름이 언제 일어나는지를 알려 줍니다. 공식 문서는 "when a client tries to write to your cluster, Amazon MSK uses IAM to check whether that client is an authenticated identity and also whether it is authorized to produce to your cluster"라고 설명합니다. 인증과 인가가 클라이언트가 클러스터에 실제로 접속해서 무언가를 하려는 그 시점에, 클러스터 쪽에서 평가된다는 뜻입니다. 그런데 dev에는 그 클러스터가 없습니다. 접속할 브로커 엔드포인트 자체가 존재하지 않으니, api-agent가 이 권한을 들고 있어도 인가 판정이 일어나는 지점까지 도달할 방법이 없습니다. 권한이 거부되는 것이 아니라, 권한을 시험해 볼 상대가 아예 없는 상태에 가깝습니다.

다만 여기서 코드가 스스로 남겨 둔 미결 지점 하나를 정직하게 밝혀 둬야겠습니다. dev에서는 이 권한 구문의 리소스 리스트가 빈 배열이 되는데, 액션은 있고 대상 리소스는 비어 있는 정책 구문이 실제로 어떻게 렌더링되고 적용되는지는 코드만 읽어서 단정하기 어렵습니다. 실제로 코드 주석에도 이 빈 리스트 상황과 그 처리를 두고 고민한 흔적이 남아 있습니다. 이 부분은 "dev에 apply했을 때 정책이 정확히 어떤 JSON으로 만들어지고 IAM이 그것을 어떻게 받아들이는가"를 실제로 확인해 봐야 할 지점이지, 코드를 읽은 것만으로 "이렇게 동작한다"고 못 박을 수 있는 지점은 아닙니다. 이 글에서는 이 부분을 확정된 사실이 아니라 확인이 필요한 열린 질문으로 남겨 두는 편이 정직하다고 생각합니다.

분명히 말할 수 있는 것은 이 정도입니다. dev에는 MSK 클러스터가 없고, 그래서 이 권한이 실제로 향할 대상 리소스도 없으며, 인가가 평가되는 접속 시점 자체가 성립하지 않습니다. 반대로 beta·prod에서는 같은 코드가 실제 클러스터 ARN과 토픽·그룹 패턴을 채워 넣어, 이 권한이 제 대상을 갖게 됩니다.

---

## 이걸 최소 권한 위반으로 볼까

그렇다면 dev에 남아 있는 이 권한 구문을 최소 권한 원칙의 눈으로 어떻게 봐야 할까요. 한쪽으로 성급히 기울지 않으려면 판단의 기준부터 정리하는 게 좋겠습니다.

먼저 최소 권한이 무엇을 요구하는지 공식 문서의 표현으로 확인해 둡니다. AWS의 IAM 보안 모범 사례는 "grant only the permissions required to perform a task"라고 못 박고, 이어서 현실적인 조언을 덧붙입니다. "You might start with broad permissions while you explore the permissions that are required for your workload or use case. As your use case matures, you can work to reduce the permissions that you grant to work toward least privilege." 처음부터 완벽하게 좁힌 권한을 갖기는 어렵고, 워크로드가 무르익어 가면서 점차 좁혀 가는 과정으로 최소 권한을 이해하라는 것입니다. 같은 문서는 "Regularly review and remove unused users, roles, permissions, policies, and credentials"도 별도 항목으로 두고, IAM의 마지막 사용 정보(last accessed information)로 더는 필요 없는 권한을 찾아 정리하라고 안내합니다.

이 기준을 dev의 상황에 대 보면, 판단을 가르는 질문은 "이 권한이 실제 공격 표면이 되는가"입니다. 만약 dev에서 api-agent의 임시 자격증명이 유출됐다고 가정해 보면, 공격자가 이 `kafka-cluster` 권한으로 도달할 수 있는 대상이 있어야 위험이 성립합니다. 그런데 dev에는 클러스터가 없으니 접속할 브로커도, 읽거나 쓸 토픽도 없습니다. 이 자격증명으로 Kafka 쪽에서 할 수 있는 일이 원천적으로 없는 셈입니다. 이 관점에서만 보면 dev의 이 권한 구문은 현재로선 무해에 가깝다고 말할 수 있습니다.

그런데 "현재로선"이라는 단서를 떼면 이야기가 달라집니다. 두 가지 경우를 함께 고려해야 합니다. 하나는 dev에 나중에 MSK가 켜지는 경우입니다. `enable_msk`를 true로 바꾸는 순간, 앞서 본 코드가 실제 클러스터 ARN을 채워 넣어 이 권한이 곧바로 대상을 갖게 됩니다. 즉 지금 무해한 이유는 권한이 좁아서가 아니라 대상이 없어서일 뿐이고, 대상이 생기는 순간 권한은 그대로 작동합니다. 다른 하나는 dev의 관행이나 자격증명 취급 방식이 상위 환경으로 옮겨 가는 경우입니다. dev에서 검증한 정책을 그대로 상위 환경에 올리는 흐름이라면, dev에서 "어차피 대상이 없으니 신경 쓰지 않아도 된다"고 넘긴 판단이 prod까지 따라 올라갈 수 있습니다.

그래서 이 권한을 "위반"이라고 부르는 것도, "무해하다"고 잘라 말하는 것도 둘 다 조금씩 어긋난다고 느낍니다. 코드는 최소 권한을 나름대로 신경 쓴 흔적이 뚜렷합니다. 와일드카드 대신 액션 일곱 개를 골랐고, 리소스도 클러스터·토픽·그룹 패턴으로 좁혔으며, 대상이 없으면 리스트가 비워지도록까지 해 두었습니다. 동시에 dev에 대상 없는 권한 구문이 남는 것을 완전히 정리한 상태도 아닙니다. AWS 문서가 권하는 방향에 비춰 보면, 판단을 미루기보다 실제로 확인해 볼 두 가지가 남습니다. 하나는 dev에 apply했을 때 이 구문이 실제로 어떤 정책으로 렌더링되는지 확인하는 것이고, 다른 하나는 마지막 사용 정보나 IAM Access Analyzer 같은 도구로 이 권한이 실제 활동에서 쓰이는지를 근거로 정리 여부를 판단하는 것입니다. AWS는 CloudTrail에 남은 접근 활동을 바탕으로 실제로 쓰는 액션만 담은 정책을 만들어 주는 IAM Access Analyzer 정책 생성 기능도 안내하고 있는데, "지금 무해해 보인다"는 인상 대신 이런 활동 기반 근거로 판단하는 편이 최소 권한의 취지에 더 맞습니다.

---

## IaC 재사용성과 환경별 최소 권한 사이

한 걸음 물러나 보면, 이 작은 권한 구문 하나에 IaC를 여러 환경에 재사용할 때 늘 마주치는 긴장이 담겨 있습니다. 세 환경에 같은 코드를 쓰면 얻는 것이 분명합니다. 한 곳만 고치면 세 환경에 일관되게 반영되고, "왜 이 환경만 다르지?"라는 질문이 줄고, 리뷰할 표면도 좁아집니다. 이 프로젝트가 `.tf`를 byte 단위로 통일하고 값만 `tfvars`로 가른 것은 그 이점을 택한 결정입니다.

그 대가로 따라오는 것이, 어떤 환경에는 아직 필요 없는 구성 요소를 겨냥한 코드가 그 환경의 정의에도 함께 들어온다는 점입니다. api-agent의 `kafka-cluster` 권한 구문이 dev의 정책 정의에도 그대로 존재하는 것은 이 대가의 한 예입니다. 이 프로젝트는 그 대가를 무시하지 않고, 대상 리소스를 조건부 모듈의 출력에서 끌어오도록 만들어 클러스터가 없으면 대상이 비워지게 하는 방식으로 절충을 시도했습니다. 액션은 공유하되 대상은 환경 상태에 따라 갈리도록 한 것입니다. 완전히 매끄러운 해법은 아니고, 앞서 본 빈-리소스 엣지처럼 확인이 필요한 자리를 남기지만, "코드 재사용성"과 "환경별로 대상을 좁힌 최소 권한"을 한 코드 안에서 함께 잡아 보려는 시도라는 점은 읽어 둘 만합니다.

여기에 정답이 하나 있는 것 같지는 않습니다. 환경마다 IAM 정책을 아예 따로 관리하면 각 환경에 꼭 필요한 권한만 남길 수 있지만, 세 벌의 정책을 따로 유지하는 부담과 환경 간 표류(drift)의 위험을 떠안게 됩니다. 반대로 한 코드로 통일하면 관리는 단순해지지만, 어떤 환경에는 대상 없는 권한 구문이 남는 것을 감수해야 합니다. 이 프로젝트가 택한 조건부 리소스 방식은 그 사이에서 균형점을 찾으려는 한 가지 답이고, 그 답에는 확인해 두어야 할 열린 질문이 함께 붙어 있습니다.

---

## 남는 생각

이 권한 구문을 들여다보기 전까지 저는 다이어그램의 "unused" 메모를 액면 그대로 받아들였습니다. 쓰이지 않는 권한이 dev에 방치돼 있다는 이야기로요. 코드를 열어 보니 실제는 그보다 한 겹 더 들어가 있었습니다. 그 권한은 방치된 와일드카드가 아니라 액션과 리소스를 나름대로 좁힌 구성이었고, 대상이 없는 이유도 누가 잊고 지운 탓이 아니라 대상 리소스를 클러스터의 실제 ARN에서 끌어오도록 만든 설계의 결과였습니다. 요약 문서의 `kafka-cluster:*`라는 표기 하나가, 실제 코드에서는 일곱 개의 구체 액션과 동적으로 계산되는 리소스로 펼쳐져 있었던 셈입니다.

여기서 개인적으로 남은 것은, 요약된 표현과 실제 코드 사이의 간격을 습관적으로 한 번 더 확인하는 일의 값어치입니다. 다이어그램 메모나 아키텍처 요약 문서는 이해를 돕기 위해 사실을 압축하는데, 그 압축이 때로는 "고정 와일드카드"와 "동적으로 계산돼 환경마다 달라지는 리소스"처럼 판단을 가르는 차이까지 뭉개 버립니다. 최소 권한을 이야기할 때 이 차이는 사소하지 않습니다. 전자라면 "쓰지 않는 넓은 권한을 방치했다"는 문제가 되고, 후자라면 "대상이 생기면 채워지도록 좁혀 둔 구성"이라는 다른 평가가 되기 때문입니다.

그리고 이 사례는 판단을 서두르지 않는 태도의 값어치도 다시 확인시켜 주었습니다. dev의 이 권한을 두고 "위험하다"와 "무해하다" 중 하나를 지금 당장 고르기보다는, 실제로 apply했을 때 정책이 어떻게 렌더링되는지, 그리고 활동 기반 근거로 이 권한이 정말 쓰이는지를 확인한 뒤에 판단하는 편이 최소 권한의 취지에 더 가깝다고 느꼈습니다. 인프라 코드를 읽는 일이 코드 리뷰와 닮은 지점이 여기에 있는 것 같습니다. "이 권한이 여기 왜 있는가"뿐 아니라 "이 권한이 지금 무엇을 가리키는가", 그리고 "이 판단을 코드만으로 내려도 되는가"까지 함께 묻는 습관이, 요약과 실제 사이의 간격에서 미끄러지지 않게 해 주는 것 같습니다.

---

## 참고한 공식 문서

- Amazon MSK IAM 접근 제어(인증·인가가 클러스터 접속 시점에 평가됨): https://docs.aws.amazon.com/msk/latest/developerguide/iam-access-control.html
- Apache Kafka APIs for Amazon MSK clusters — 액션·리소스 유형·ARN 형식(`kafka-cluster` 접두어, cluster/topic/group ARN과 ClusterUuid): https://docs.aws.amazon.com/service-authorization/latest/reference/list_apachekafkaapisforamazonmskclusters.html
- IAM JSON 정책 요소: Resource(ARN으로 대상을 지정, 대상 존재 여부와 무관하게 규칙을 기술): https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_elements_resource.html
- IAM 보안 모범 사례(최소 권한 적용, 쓰지 않는 권한 정기 검토·제거, IAM Access Analyzer 활동 기반 정책 생성): https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html
