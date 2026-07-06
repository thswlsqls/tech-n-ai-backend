# Aurora MySQL의 계단형 진화 — Serverless v2에서 Provisioned로

> 시리즈: reference-architecture 다이어그램 읽기 — 다이어그램 라벨 한 줄에 담긴 인프라 결정 읽기

---

## SEO 제목 후보

- **Aurora Serverless v2에서 Provisioned로 — 언제, 왜 갈아타야 하는가** — 서비스가 커지면서 Aurora 모드 전환 시점을 고민하는 백엔드·DBA 엔지니어를 위한 후보
- **dev는 서버리스, prod는 고정 인스턴스 — Terraform 변수 하나가 가르는 Aurora 아키텍처** — Terraform으로 환경별 Aurora 구성을 분기하는 IaC 패턴을 찾는 엔지니어를 위한 후보
- **Aurora Capacity Unit(ACU)이란 무엇인가 — 서버리스와 프로비저닝드 인스턴스 비교** — ACU 개념 자체를 검색해 들어오는 입문 엔지니어를 위한 후보
- **Performance Insights는 왜 prod에만 켜져 있을까 — Aurora 환경별 관측성 설계** — 관측성 관점에서 환경별 DB 설정 차이를 찾는 엔지니어를 위한 후보

---

## 들어가며

dev와 beta의 아키텍처 다이어그램을 나란히 두고 Aurora 아이콘만 보면 라벨이 거의 같습니다. dev는 "Serverless v2 (0.5-2.0 ACU)", beta는 "Serverless v2 (0.5-4.0 ACU)"라고 적혀 있어서, 용량 범위 숫자만 다를 뿐 같은 종류의 데이터베이스라는 인상을 줍니다. 그런데 prod로 넘어가면 라벨이 통째로 바뀝니다. "Provisioned 3x db.r7g.large", 그 아래 줄에는 "writer + 2 readers"라고 적혀 있습니다. 자동으로 늘고 줄던 서버리스 인스턴스 한 대가, prod에서는 고정된 세 대의 인스턴스로 바뀌어 있는 것입니다.

처음 이 다이어그램을 봤을 때는 "prod는 트래픽이 많으니까 인스턴스를 더 썼겠지" 정도로 넘어갔습니다. 하지만 실제로 Terraform 코드를 열어 보면, 이 전환이 인스턴스 개수만 바뀌는 문제가 아니라는 걸 알게 됩니다. `engine_mode`라는 변수 값 하나가 클러스터 안의 스케일링 설정, 인스턴스 개수, 인스턴스 클래스, 심지어 변경 사항이 즉시 반영되는지 여부까지 한꺼번에 갈라놓고 있었습니다. 이 글에서는 Serverless v2와 Provisioned가 운영 모델 관점에서 실제로 무엇이 다른지, 그리고 이 프로젝트의 Terraform 모듈이 그 차이를 어떤 방식으로 코드에 담아냈는지를 짚어 보려 합니다.

---

## ACU라는 단위 하나로 요약되는 서버리스의 사고방식

Aurora Serverless v2를 이해하는 가장 빠른 방법은 ACU(Aurora Capacity Unit)라는 단위를 이해하는 것입니다. AWS 공식 문서는 ACU 하나를 대략 2GiB의 메모리와 그에 상응하는 CPU, 네트워크 자원의 조합으로 정의합니다. 프로비저닝드 클러스터에서는 `db.r7g.large`처럼 미리 정해진 사양의 인스턴스 클래스를 선택해야 하지만, 서버리스 클러스터에서는 이 클래스 대신 최소·최대 ACU 범위만 지정해 두면 됩니다. 실제 용량은 그 범위 안에서 워크로드에 맞춰 자동으로 오르내립니다.

이 자동 조정의 세밀함도 문서에 구체적으로 나와 있습니다. 프로비저닝드 클러스터에서 용량을 늘리려면 인스턴스 클래스를 통째로 바꾸거나 인스턴스를 새로 추가해야 하지만, 서버리스는 0.5 ACU 단위로 필요한 만큼만 늘리거나 줄일 수 있습니다. dev의 tfvars 기본값은 0.5~2.0 ACU, beta는 0.5~4.0 ACU로 설정돼 있는데, 이 범위 자체가 "이 환경에서는 최악의 경우에도 이 정도면 충분하다"는 상한선을 정해 두는 역할을 합니다. AWS 문서가 서버리스의 대표적인 적합 사례로 꼽는 것도 트래픽이 언제 튈지 예측하기 어려운 개발·테스트 환경, 그리고 사용량이 들쭉날쭉한 신규 서비스입니다. dev와 beta가 정확히 이 조건에 들어맞습니다. 매일 사람이 붙어서 확인하는 환경이 아니고, 배포 파이프라인이 정상 동작하는지, 코드가 제대로 뜨는지를 확인하는 것이 목적이기 때문에, 트래픽 패턴을 미리 알고 인스턴스 클래스를 골라 둘 이유가 크지 않습니다.

반대로 프로비저닝드 클러스터는 사용량 패턴이 안정적일 때 유리하다고 AWS 문서는 설명합니다. 인스턴스 클래스를 한 번 정해 두면 그 사양이 그대로 고정되고, 트래픽이 바뀌면 사람이 직접 클래스를 바꿔야 합니다. 대신 어떤 순간에도 얼마의 자원이 배정돼 있는지가 명확하고, 요금도 예측하기 쉽습니다. 이 프로젝트의 Terraform 모듈에서 이 두 모드는 완전히 다른 리소스 구조가 아니라, 하나의 `aws_rds_cluster` 모듈 안에서 `engine_mode`라는 변수 하나로 분기되도록 짜여 있습니다. 겉보기엔 값 하나의 차이지만, 이 값이 실제로 무엇을 갈라놓는지는 코드를 더 들여다봐야 보입니다.

---

## prod가 Provisioned로 넘어가는 세 가지 근거

prod의 tfvars를 보면 Aurora 관련 설정이 dev·beta보다 훨씬 많습니다. `instance_count = 3`, `instance_class = db.r7g.large`, `storage_type = aurora-iopt1`, `backup_retention_period = 30`, `deletion_protection = true`, `skip_final_snapshot = false`, `performance_insights_enabled = true`. 이 값들을 하나씩 따라가 보면, prod가 Provisioned를 선택한 이유는 인스턴스 개수 하나가 아니라 세 갈래로 나뉩니다.

첫 번째는 가시성입니다. `performance_insights_enabled`는 dev·beta에서는 기본값 false, prod에서만 true로 켜져 있습니다. AWS 문서에 따르면 Performance Insights는 데이터베이스 부하를 대기 이벤트·SQL 문·호스트·사용자 단위로 나눠 시각화해 주는 도구입니다. 보관 기간은 기본 7일이 무료이고, 그 이상(최대 24개월)은 유료로 늘릴 수 있습니다. 이 프로젝트의 모듈 변수 설명에도 "PI 보관 일수(7 또는 731), 7=무료"라고 적혀 있어서, 무료 구간과 유료 구간의 경계를 명확히 인식하고 설정값을 선택했다는 것을 알 수 있습니다. 다만 이 기능은 최근 AWS가 단계적 전환을 공지한 상태입니다. 2026년 7월 31일 이후 Performance Insights 콘솔은 CloudWatch Database Insights로 리다이렉트되고, Standard 모드가 기존 경험과 요금을 그대로 이어받는다고 공식 문서에 명시돼 있습니다. API 자체는 그대로 남고 Terraform 설정도 변경 없이 동작한다고 안내하고 있지만, prod에서 이 기능에 의존해 온콜 대응을 하고 있다면 이 전환 시점은 한 번쯤 점검할 필요가 있어 보입니다.

두 번째는 읽기 부하 분산입니다. AWS 문서는 Aurora Replica를 최대 15개까지 클러스터에 둘 수 있고, 리더 엔드포인트로 접속하면 읽기 전용 쿼리를 여러 Replica에 나눠 보낼 수 있다고 설명합니다. prod의 `instance_count = 3`은 writer 1대와 reader 2대로 구성되는데, 이 reader들은 단순히 읽기 부하만 나눠 받는 게 아니라 장애 대응 역할도 겸합니다. writer 인스턴스에 문제가 생기면 Aurora가 자동으로 Replica 하나를 writer로 승격시키기 때문에, Replica가 없는 클러스터라면 장애 복구 동안 클러스터 전체가 응답하지 못하는 공백이 생깁니다. 다만 이 인스턴스들이 실제로 서로 다른 가용 영역에 정확히 하나씩 배치된다고 코드가 강제하고 있는지는 별개의 질문입니다. 이 모듈에는 Multi-AZ를 명시하는 별도 플래그가 없고, 3개 가용 영역에 걸친 서브넷 그룹 위에 인스턴스가 놓이는 방식으로만 처리돼 있습니다. AWS 문서는 Replica가 "클러스터가 걸쳐 있는 가용 영역들에 분산될 수 있다"고 설명할 뿐이어서, 이 프로젝트의 설정만으로 세 인스턴스가 각각 다른 AZ에 하나씩 정확히 배치된다고 단정하기는 어렵습니다. 이 부분은 코드 확인만으로는 완전히 답하기 어려운 지점으로 남겨 둡니다.

세 번째는 데이터 보존과 삭제 방지입니다. `backup_retention_period`가 dev의 1일, beta의 7일에서 prod는 30일로 늘어나고, `deletion_protection`과 `skip_final_snapshot=false`가 함께 켜지면서 실수로든 의도적으로든 클러스터가 통째로 사라지는 상황에 여러 겹의 제동이 걸립니다. 이 값들 자체는 engine_mode와 직접 연결돼 있지 않지만, prod의 tfvars 안에서 Provisioned 전환과 나란히 조정됐다는 점에서 "이 환경은 이제 실수를 되돌리기 어려운 단계로 들어간다"는 하나의 태도로 읽힙니다.

---

## 코드로 보는 전환 스위치 — 변수 하나, 분기는 셋

`modules/aurora-mysql/main.tf`를 열어 보면 이 전환이 실제로 어디서 갈라지는지 확인할 수 있습니다. 먼저 눈에 띄는 것은 `aws_rds_cluster` 리소스의 `engine_mode` 속성이 `"provisioned"`로 고정돼 있다는 점입니다. 이 프로젝트에서 사용자가 고르는 변수 이름도 `engine_mode`이고 값은 `serverlessv2` 아니면 `provisioned`인데, 정작 AWS 리소스에 실제로 전달되는 속성값은 서버리스를 쓰든 안 쓰든 항상 `"provisioned"`입니다. 코드에는 이유가 주석으로 남아 있습니다. "serverlessv2 도 engine_mode=provisioned + serverlessv2_scaling_configuration". AWS 문서를 보면 `serverless`라는 문자열은 지금은 쓰이지 않는 구버전 Aurora Serverless v1의 아키텍처를 가리키는 이름으로 남아 있고, 지금 쓰는 v2는 API 상으로는 provisioned 클러스터에 스케일링 설정 블록 하나가 추가된 형태로 다뤄집니다. 변수 이름과 실제 API 값이 서로 다른 문자열을 쓴다는 사실은, 이 계층을 처음 보는 사람이 헷갈리기 쉬운 지점이라 코드 주석으로 남겨 둔 것으로 보입니다.

진짜 분기는 `local.is_serverless = var.engine_mode == "serverlessv2"`라는 한 줄에서 시작해서 세 곳으로 뻗어 나갑니다. 첫 번째는 `serverlessv2_scaling_configuration` 블록입니다. `dynamic` 블록으로 선언돼 있어서 `is_serverless`가 참일 때만 이 설정 블록 자체가 클러스터에 생성되고, 거짓이면 아예 존재하지 않는 것으로 취급됩니다. 두 번째는 인스턴스 개수입니다. `aws_rds_cluster_instance` 리소스의 `count` 인자가 `local.is_serverless ? 1 : var.instance_count`로 정의돼 있어서, 서버리스 모드에서는 인스턴스가 정확히 1개만 만들어지고, 프로비저닝드 모드에서는 `instance_count`에 지정한 개수(prod는 3)만큼 만들어집니다. 세 번째는 인스턴스 클래스입니다. 서버리스 모드에서는 `instance_class`가 `"db.serverless"`로 강제되고, 프로비저닝드 모드에서는 사용자가 지정한 값(prod는 `db.r7g.large`)이 그대로 쓰입니다.

여기서 ALB의 HTTPS 리스너 토글과 비교해 보면 차이가 분명해집니다. HTTPS 토글은 리소스 하나의 존재 여부만 `count = 0 또는 1`로 결정하는 단순한 스위치였습니다. Aurora의 이 전환은 같은 변수 하나에서 출발하지만, 스케일링 블록의 존재 여부, 인스턴스 개수, 인스턴스 클래스라는 서로 다른 세 속성이 동시에 바뀝니다. 게다가 인스턴스 리소스의 `identifier`는 `"${cluster_name}-${count.index + 1}"`로 정해져 있어서, 서버리스에서 프로비저닝드로 전환할 때 인덱스 0번 인스턴스(현재의 유일한 writer)는 같은 주소를 유지한 채 클래스만 `db.serverless`에서 `db.r7g.large`로 바뀌고, 인덱스 1번과 2번은 이전에 존재하지 않았던 완전히 새로운 reader 인스턴스로 생성됩니다. 여기에 하나 더 얹히는 조건이 `apply_immediately = !local.is_prod`입니다. prod에서는 이 값이 거짓이라, 인스턴스 클래스를 바꾸는 변경 사항 대부분이 즉시 적용되지 않고 다음 유지보수 창을 기다리게 됩니다. 값 하나를 바꾸고 `terraform apply`를 돌리는 순간과, 그 변경이 실제로 클러스터에 반영되는 순간 사이에 시차가 생길 수 있다는 뜻입니다.

---

## "값 하나"가 아니라, 인스턴스 두 대가 새로 생기는 전환

같은 인프라 코드 안에서 앞서 다룬 적 있는 ALB HTTPS 토글은 인증서 ARN 하나만 채우면 되돌리기 쉬운 결정이었습니다. Aurora의 서버리스·프로비저닝드 전환은 겉보기엔 비슷한 모양의 변수 하나짜리 분기이지만, 실제로 되짚어 보면 훨씬 무거운 작업입니다. `engine_mode`를 바꾸는 순간 스케일링 설정이 사라지거나 생기고, 기존 writer 인스턴스의 클래스가 바뀌고, 이전엔 없던 reader 인스턴스 두 대가 새로 만들어집니다. AWS 문서는 개별 인스턴스를 서버리스와 프로비저닝드 사이에서 전환하는 것 자체는 클러스터나 인스턴스를 새로 만들 필요 없이 가능한 작업이라고 설명합니다. 클러스터 볼륨이 인스턴스 개수와 독립적인 공유 저장소로 설계돼 있어서, 인스턴스를 추가할 때 데이터를 복사할 필요가 없기 때문입니다. 그런 의미에서 이 전환을 "클러스터를 다시 짓는 일"이라고 부르는 건 과장입니다.

다만 이 프로젝트의 tfvars 값 하나를 바꾸는 일이 그만큼 가볍다고 보기도 어렵습니다. `engine_mode` 하나를 바꾸는 순간 인스턴스 개수·클래스·스케일링 설정이라는 서로 다른 세 값이 한꺼번에 재조정되고, 그 결과로 이전에 없던 인스턴스 두 대가 실제로 프로비저닝됩니다. prod처럼 `apply_immediately`가 꺼져 있는 환경이라면 이 변경 중 일부는 유지보수 창까지 기다려야 반영됩니다. "설정값 하나를 바꾸는 일"과 "그 값이 실제로 클러스터에 반영되기까지 관리해야 할 변수의 개수"는 이 코드에서 분명히 다른 이야기입니다.

이 글을 쓰면서 남은 인상은, 좋은 인프라 코드일수록 변수 하나가 숨기고 있는 파급 범위를 코드 밖에서도 짐작할 수 있어야 한다는 것이었습니다. `engine_mode = "serverlessv2"`라는 한 줄만 보고 이 값을 "provisioned"로 바꿨을 때 무엇이 몇 개나 새로 생기는지, 그리고 그중 무엇이 즉시 적용되고 무엇이 유지보수 창을 기다리는지는 모듈 코드를 한 단계 더 들어가 봐야 알 수 있었습니다. 다이어그램의 라벨 두 줄이 짧게 요약해 준 차이를, 실제 변수와 조건문까지 따라가 보고 나서야 이 전환이 지닌 무게를 제대로 가늠하게 된 느낌입니다.

---

## 참고한 공식 문서

- Using Aurora serverless: https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-serverless-v2.html
- How Aurora serverless works (ACU 정의·스케일링 단위): https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-serverless-v2.how-it-works.html
- Managing Aurora serverless DB clusters (프로비저닝드·서버리스 상호 전환): https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-serverless-v2-administration.html
- Replication with Amazon Aurora (Aurora Replica, 최대 개수, 승격): https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.Replication.html
- Monitoring DB load with Performance Insights on Amazon RDS (EOL 공지 포함): https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PerfInsights.html
- Pricing and data retention for Performance Insights: https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/USER_PerfInsights.Overview.cost.html
- Amazon Aurora storage (클러스터 볼륨, I/O-Optimized): https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.Overview.StorageReliability.html
