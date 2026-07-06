# 없음에서 Serverless로, Serverless에서 Provisioned로 — 세 장의 다이어그램에 그려진 MSK 도입 단계

> 시리즈: network-topology 다이어그램 읽기 — 세 환경이 함께 움직이는 방향

---

## SEO 제목 후보

- **없음에서 Serverless로, Serverless에서 Provisioned로 — 세 장의 다이어그램에 그려진 MSK 도입 단계** — 환경별로 Kafka 인프라를 다르게 가져가는 근거를 찾는 백엔드/인프라 엔지니어에게
- **MSK Serverless vs Provisioned, 언제 바꿔야 하는가 — dev·beta·prod 3단계 도입 기록** — Kafka를 처음 프로덕션에 올리며 클러스터 형태를 고민하는 엔지니어에게
- **`enable_msk`와 `use_msk_provisioned` — Terraform 변수 두 개로 갈리는 세 가지 Kafka 인프라** — Terraform으로 환경별 MSK 분기를 설계하려는 인프라 엔지니어에게

---

## 들어가며

AWS 네트워크 토폴로지 다이어그램을 dev, beta, prod 순서로 넘겨 보다가 눈에 걸리는 지점이 하나 있었습니다. dev 다이어그램의 Private-Data 구역에는 Aurora MySQL과 ElastiCache Valkey, MongoDB Atlas Endpoint 아이콘만 있고 MSK가 없습니다. 부제에도 "no MSK"라고 명시돼 있습니다. beta로 넘어가면 MSK 아이콘이 등장하는데 부제는 "MSK Serverless"입니다. prod에 가서야 부제가 "MSK Provisioned"로 바뀌고, 세 가용 영역 모두의 Private-Data 구역에 MSK 브로커가 그려집니다.

세 장을 나란히 보면 이게 우연한 배치가 아니라는 게 분명해집니다. 저장소의 Terraform 코드를 열어 보면 이 차이를 만드는 것은 `enable_msk`와 `use_msk_provisioned`라는 불리언 변수 두 개뿐입니다. dev는 `enable_msk`가 꺼져 있어 MSK 자체가 생성되지 않고, beta는 켜져 있지만 `use_msk_provisioned`가 꺼져 있어 Serverless로, prod는 둘 다 켜져 있어 Provisioned로 만들어집니다. 변수 이름만 보면 단순한 토글처럼 보이지만, 그 뒤에는 "지금 이 환경에 Kafka가 얼마나 필요한가"라는 질문에 대한 세 가지 서로 다른 답이 있습니다. 이 글에서는 그 답이 어떻게 갈렸는지를 다이어그램과 설계 문서, 그리고 AWS 공식 문서를 근거로 따라가 보려 합니다.

---

## dev에는 왜 MSK 아이콘 자체가 없는가

가장 먼저 눈에 띄는 것은 dev 환경입니다. beta처럼 축소된 Serverless 클러스터라도 하나 떠 있을 법한데, dev 다이어그램에는 MSK가 아예 그려져 있지 않습니다. "일단 만들어는 두고 트래픽만 안 보낸다"가 아니라 "만들지 않는다"를 택한 셈입니다.

이 결정의 배경에는 두 가지가 겹쳐 있습니다. 하나는 프로젝트의 설계 문서가 밝히는 선정 근거입니다. dev와 beta는 트래픽이 낮고 운영 부담을 최소화하는 것이 목표라서, 관리형 인프라를 최소 단위로도 아예 켜지 않는 선택지를 열어 둔 것입니다. 다른 하나는 코드 레벨의 정직한 상태입니다. 설계 문서는 현재 IaC의 갭도 함께 기록해 두고 있는데, `api-agent`를 제외한 나머지 서비스(`api-chatbot`, `api-bookmark`, `api-auth`)는 MSK를 produce/consume하는 데 필요한 IAM 권한이 아직 부여돼 있지 않습니다. 실제로 Kafka를 통해 이벤트를 주고받는 코드 경로가 프로덕션 수준으로 완성되기 전이라는 뜻입니다. 이런 상태에서 dev에 클러스터를 미리 띄워 두면, 쓰이지 않는 브로커가 대기 상태로 비용만 발생시키는 자원이 됩니다.

여기서 흥미로운 점은 "일단 만들어 두고 나중에 쓴다"는 선택지가 왜 배제됐는가입니다. 클라우드 인프라, 특히 관리형 스트리밍 클러스터는 온프레미스의 유휴 장비와 다르게 켜져 있는 시간만큼 과금이 쌓입니다. 아직 실제로 프로듀스도 컨슘도 하지 않는 dev 환경에 클러스터를 미리 준비해 두는 일은 "언젠가 쓸 자원을 미리 사 둔다"는 감각보다는 "쓰지 않는 시간만큼 계속 돈을 내는" 감각에 가깝습니다. `enable_msk`가 dev의 기본값에서 꺼져 있다는 사실은, 이 프로젝트가 "완성되지 않은 기능을 위해 인프라를 먼저 만들어 두지 않는다"는 원칙을 코드 레벨에서 지키고 있다는 신호로 읽을 수 있습니다.

이 결정이 무리 없이 성립하는 데에는 로컬 개발 환경의 존재도 한몫합니다. 이 저장소는 루트의 `docker-compose.yml` 하나로 모듈별 MySQL과 Kafka, 관측 스택을 로컬에 띄울 수 있게 되어 있습니다. 즉 프로듀서·컨슈머 코드를 짜고 토픽 이름을 검증하는 작업은 굳이 AWS dev 계정까지 올라가지 않아도 로컬 Kafka로 충분히 반복할 수 있습니다. AWS의 dev 환경이 맡는 역할은 "기능을 처음 만들어 보는 자리"가 아니라 "이미 로컬에서 검증한 것을 클라우드 배포 파이프라인 위에서 다시 확인하는 자리"에 가깝고, 그렇다면 아직 Kafka 연동이 배포 파이프라인까지 올라오지 않은 시점에 AWS dev에 클러스터를 미리 켜 둘 이유는 더 옅어집니다. 실제로 이벤트를 주고받는 서비스는 현재 `api-agent` 하나뿐이고, 토픽은 `{project}.conversation.*` 패턴으로, 컨슈머 그룹은 `{project}.*` 패턴으로 명명하도록 설계돼 있습니다. 나머지 서비스가 이 패턴에 합류하는 시점이 곧 dev에도 MSK가 필요해지는 시점이 될 것입니다.

---

## beta: Serverless는 "축소판 prod"가 아니다

beta로 넘어가면 MSK가 등장하지만, 형태는 prod와 전혀 다른 Serverless입니다. AWS 공식 문서는 MSK Serverless를 "클러스터 용량을 직접 관리하거나 스케일링하지 않고 Apache Kafka를 실행할 수 있게 해 주는 클러스터 유형"으로 설명합니다. 파티션 관리를 포함한 용량 프로비저닝과 스케일링을 자동으로 처리하기 때문에, 사용자가 브로커 크기나 개수를 직접 정하지 않아도 됩니다. 과금 방식도 처리량 기반이라, 실제로 흘려보낸 데이터양만큼만 비용이 청구됩니다.

이 특성이 beta 환경의 목적과 정확히 맞아떨어집니다. beta는 트래픽이 낮고, 운영 인력을 브로커 튜닝에 쓸 이유가 적은 검증 환경입니다. 프로듀서와 컨슈머 코드가 실제로 동작하는지, 토픽 설계가 맞는지, 스키마가 호환되는지를 확인하는 것이 목적이지, 처리량 한계를 찾아내거나 파티션 배치를 최적화하는 것이 목적이 아닙니다. Serverless가 요구하는 유일한 제약은 인증입니다. AWS 문서는 "MSK Serverless는 모든 클러스터에 IAM 접근 제어를 요구하며, Apache Kafka 네이티브 ACL은 지원하지 않는다"고 명시합니다. 이 프로젝트는 애초에 모든 환경에서 IAM을 주 인증 수단으로 채택했기 때문에, 이 제약이 beta의 설계와 부딪히지 않습니다.

다만 Serverless에는 확인해 둘 만한 상한선도 있습니다. AWS가 공개한 서비스 쿼터를 보면 클러스터당 최대 처리량은 인그레스 200MBps, 이그레스 400MBps이고, 파티션 리더 수는 비압축 토픽 기준 2,400개, 압축 토픽 기준 120개로 제한됩니다. beta 규모의 트래픽에서는 이 상한이 문제가 될 가능성이 낮지만, "이 상한이 우리 트래픽 패턴에서 넉넉한가"를 검증하는 것 자체가 beta 환경이 맡아야 할 역할 중 하나라는 점은 짚어 둘 만합니다.

---

## prod가 beta의 연장이 아니라 Provisioned로 갈아타는 이유

prod에서 벌어지는 전환은 단순한 "스케일 업"이 아닙니다. Serverless를 그대로 키우는 대신 아예 다른 클러스터 유형인 Provisioned로 넘어갑니다. 설계 문서가 밝히는 근거는 세 갈래입니다.

첫째는 관측성입니다. prod는 Prometheus 기반 Open Monitoring으로 상세한 SLI를 확보해야 하는데, 이 수준의 관측은 Provisioned 클러스터에서만 가능합니다. 둘째는 메타데이터 관리 방식입니다. prod는 Kafka 3.9.x를 KRaft 모드로 새로 구축하려 하는데, AWS 공식 문서는 "3.9 버전이 ZooKeeper와 KRaft 메타데이터 관리 시스템을 모두 지원하는 마지막 버전"이라고 명시하고 있습니다. 즉 3.9는 두 체계를 오갈 수 있는 마지막 다리이자, 이후 버전에서는 KRaft가 사실상 유일한 선택지가 된다는 뜻입니다. 여기에 세그먼트 크기나 `num.replica.fetchers` 같은 브로커 파라미터를 세밀하게 튜닝해야 하는데, 이런 튜닝 여지는 Serverless에는 존재하지 않습니다. 셋째는 인증 확장성입니다. Serverless는 IAM 인증만 지원하므로, 향후 외부 파트너 연동에서 mTLS 같은 인증 방식이 필요해질 가능성을 고려하면 Provisioned가 유리합니다. 실제로 prod 클러스터는 IAM SASL과 TLS 인증을 함께 켜 두고 있어, 이 확장 여지를 코드에도 반영해 뒀습니다.

비용 비교는 설계 문서가 가장 조심스럽게 다루는 부분입니다. prod의 최대 부하(6MB/s) 기준으로 3개 가용 영역에 `kafka.m7g.large`를 하나씩 두는 Provisioned 구성이 Serverless보다 저렴할 수 있다는 추정은 있지만, 정확한 비교는 AWS Pricing Calculator로 워크로드별로 따로 계산해야 한다고 문서 스스로 못 박아 두고 있습니다. 이 글에서도 이 부분은 "확정된 사실"이 아니라 "검증이 필요한 가정"으로 남겨 두는 것이 정직하다고 생각합니다.

여기서 한 가지 정정해 둘 지점이 있습니다. "Serverless가 항상 더 간단하고 좋다"는 인상을 가지기 쉬운데, prod의 선택은 그 인상과 반대로 움직입니다. Provisioned는 브로커 개수부터 스토리지, 파라미터까지 직접 정의해야 하는 만큼 운영 부담이 더 큽니다. prod가 그 부담을 굳이 떠안는 이유는 Serverless가 줄 수 없는 것, 즉 세밀한 관측·세밀한 튜닝·인증 방식의 자유도가 필요하기 때문입니다. 관리 부담을 줄이는 방향이 항상 옳은 방향은 아니고, 어떤 환경은 그 부담을 감수하고서라도 얻어야 하는 것이 있다는 사실을, prod의 선택이 보여 주고 있습니다.

실제로 prod 클러스터의 사양을 뜯어보면 이 부담이 구체적인 숫자로 드러납니다. `kafka.m7g.large` 브로커를 세 가용 영역에 하나씩, 총 3대를 두고 복제 계수(RF)는 3, `min.insync.replicas`는 2로 잡아 브로커 한 대가 죽어도 쓰기가 끊기지 않도록 설계돼 있습니다. 토픽 기본 파티션 수는 6, 보존 기간은 168시간(7일)이고, `auto.create.topics`는 꺼 두어 의도치 않은 토픽이 코드 실수로 생겨나지 않게 막아 뒀습니다. 저장 데이터는 AWS 관리형 키가 아니라 고객 관리형 KMS 키(CMK)로 암호화됩니다. 이 키는 MSK 전용이 아니라 Aurora·MongoDB·ElastiCache가 함께 쓰는 `{env}-data` 키입니다. 이 값 하나하나가 Serverless였다면 애초에 사용자가 손댈 수 없었을 항목들입니다. Provisioned를 선택한다는 것은 이런 세부 항목을 마주하고 책임지겠다는 선택이기도 합니다.

---

## 하나의 모듈, 두 개의 스위치

이 세 가지 결과를 만들어 내는 코드는 놀랍도록 단순합니다. `modules/msk-serverless`와 `modules/msk-provisioned`라는 별도 모듈이 있고, 환경별 `.tf` 설정에서 `enable_msk`와 `use_msk_provisioned` 두 값만 바꿔서 호출합니다. dev는 `enable_msk = false`(기본값)로 아예 모듈을 호출하지 않는 것과 같은 효과를 내고, beta는 `enable_msk = true`에 `use_msk_provisioned = false`로 Serverless 모듈을, prod는 두 값을 모두 `true`로 두어 Provisioned 모듈을 호출합니다.

이 패턴이 좋은 이유는 "환경마다 다른 인프라"라는 결정이 모듈 내부에 흩어져 있지 않고, 호출부의 변수 두 개로 압축돼 드러난다는 점입니다. 새 엔지니어가 "왜 beta는 dev보다 인프라가 많지?"라는 질문을 던졌을 때, 모듈 코드를 파고들 필요 없이 `terraform.tfvars` 두 줄만 보면 답이 나옵니다. 클러스터 이름 규칙조차 이 분기를 따라갑니다. Serverless는 `{project}-{env}-msk-sl`, Provisioned는 `{project}-{env}-msk`로 접미사가 갈려서, 리소스 이름만 봐도 어떤 유형인지 구분할 수 있습니다.

다만 이 단순함 뒤에는 감춰진 비용이 하나 있습니다. `enable_msk`나 `use_msk_provisioned`를 바꾸는 일은 값 하나를 고치는 것처럼 보이지만, 실제로는 서로 다른 리소스 유형 사이의 전환이라 클러스터가 통째로 재생성됩니다. Serverless에서 Provisioned로(또는 그 반대로) 넘어가는 것은 설정을 조정하는 일이 아니라 새 클러스터를 만들고 데이터와 컨슈머 그룹을 옮기는 일에 가깝습니다. beta에서 검증한 토픽 설계나 컨슈머 그룹 이름 규칙은 prod에도 그대로 이어 쓸 수 있지만, 클러스터 자체는 처음부터 다시 만들어진다는 사실은 이 변수 두 개를 다룰 때 잊지 않아야 할 부분입니다.

---

## 다이어그램 세 장이 알려 준 것

이 다이어그램들을 보기 전까지는 "환경마다 MSK 사양이 다르다" 정도로만 생각했습니다. 실제로 들여다보니 dev는 사양이 다른 게 아니라 존재 자체가 다른 결정이었고, beta와 prod는 같은 소프트웨어의 다른 배포 형태가 아니라 서로 다른 운영 계약을 맺은 것에 가까웠습니다. Serverless는 "용량 계획을 AWS에 맡기는 대신 세밀한 제어권을 내려놓는다"는 계약이고, Provisioned는 "세밀한 제어권을 갖는 대신 그 운영 부담을 직접 진다"는 계약입니다. 두 계약 중 어느 쪽이 우월한 게 아니라, 환경이 요구하는 것이 다를 뿐입니다.

개인적으로 남는 인상은, 인프라 다이어그램에서 "빠져 있는 것"이 종종 "있는 것"보다 더 많은 정보를 담고 있다는 점이었습니다. dev 다이어그램의 MSK 아이콘이 없는 자리는 단순한 여백이 아니라, "아직 이 기능을 프로덕션 수준으로 완성하지 않았다"는 사실을 있는 그대로 드러내는 자리였습니다. 다이어그램을 코드 리뷰처럼 읽는 습관, 즉 "이 환경엔 왜 이 구성 요소가 있는가"뿐 아니라 "왜 없는가"까지 묻는 습관이 인프라 설계의 정직함을 검증하는 한 가지 방법이라는 생각이 들었습니다.

---

## 참고한 공식 문서

- Amazon MSK 개요(Serverless vs Provisioned): https://docs.aws.amazon.com/msk/latest/developerguide/what-is-msk.html
- MSK Serverless란 무엇인가: https://docs.aws.amazon.com/msk/latest/developerguide/serverless.html
- Amazon MSK 쿼터(Serverless 처리량·파티션 한도 포함): https://docs.aws.amazon.com/msk/latest/developerguide/limits.html
- MSK 지원 Apache Kafka 버전과 KRaft/ZooKeeper 메타데이터 관리: https://docs.aws.amazon.com/msk/latest/developerguide/supported-kafka-versions.html
- MSK Provisioned: https://docs.aws.amazon.com/msk/latest/developerguide/msk-provisioned.html
