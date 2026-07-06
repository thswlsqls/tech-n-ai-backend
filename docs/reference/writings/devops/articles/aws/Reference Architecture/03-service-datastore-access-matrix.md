# SG 목록엔 있는데 코드엔 없고, 코드엔 있는데 SG 목록엔 없다 — 6개 서비스 × 3개 데이터 저장소를 표로 펼쳐본 기록

## SEO 제목 후보

- **AWS Security Group 인바운드 규칙과 실제 코드 사용 여부, 어떻게 교차 검증하는가** — SG를 정리하기 전에 "이 권한을 정말 쓰고 있는지"부터 확인하고 싶은 인프라·보안 엔지니어를 위한 글입니다.
- **마이크로서비스 아키텍처에서 데이터 저장소 접근 권한을 최소 권한 원칙으로 점검하는 방법** — 여러 서비스가 하나의 DB·캐시·메시지 브로커를 나눠 쓰는 구조를 운영하면서 권한 목록이 점점 불어나는 걸 지켜본 백엔드 엔지니어를 위한 글입니다.
- **Terraform Security Group 설정과 애플리케이션 코드가 어긋나는 두 가지 방향 — 미사용 권한과 누락된 권한** — IaC와 애플리케이션 코드가 서로 다른 리포지토리, 다른 리뷰어 손을 거치면서 조금씩 벌어지는 간극을 걱정하는 DevOps·SRE를 위한 글입니다.
- **공유 모듈이 인프라 문서에 만드는 사각지대 — Spring Boot MSA에서 겪은 사례** — 여러 서비스가 공통 라이브러리를 통해 간접적으로 데이터 저장소에 접근하는 구조를 설계 중인 백엔드 엔지니어를 위한 글입니다.

---

인프라 다이어그램을 다시 들여다보다가, 화살표 개수를 세어 보고 싶어진 적이 있습니다. 이 시스템은 ECS Fargate 위에서 여섯 개 서비스(api-gateway, api-auth, api-emerging-tech, api-chatbot, api-bookmark, api-agent)가 돌아가고, Private-data 서브넷에는 Aurora MySQL, ElastiCache Valkey, Amazon MSK 세 개의 저장소가 나란히 서 있습니다. 다이어그램은 이 여섯 개와 세 개 사이를 실선과 점선 화살표로 연결해 두었습니다. Aurora로는 두 개, Valkey로는 세 개, MSK로는 세 개의 화살표가 뻗어 있습니다.

그런데 이 화살표를 실제로 네트워크 접근을 결정하는 Terraform Security Group 규칙과 나란히 놓아 보면, 완전히 같은 그림이 아니었습니다. `envs/prod/services.tf`에는 워크로드 서비스별로 어떤 데이터 저장소 SG에 인바운드를 허락할지 정하는 세 개의 목록이 있습니다. Aurora 접근을 허락받는 `aurora_consumers`는 api-auth, api-emerging-tech, api-bookmark, api-agent 네 개이고, 캐시 접근을 허락받는 `cache_consumers`는 api-auth, api-chatbot, api-bookmark 세 개, MSK 접근을 허락받는 `msk_consumers`는 api-emerging-tech, api-bookmark, api-agent 세 개입니다(`envs/prod/services.tf:282`~`:301`). 다이어그램의 화살표 개수와 이 목록의 개수가 저장소마다 조금씩 다릅니다. 이 간극이 어디서 오는지, 그리고 그 간극이 실제로 문제가 되는지 궁금해서 코드를 따라 들어가 봤습니다.

## 다이어그램, SG 규칙, 실제 코드 — 세 장의 지도

먼저 다이어그램이 뭐라고 말하는지부터 정리하면 이렇습니다. 오른쪽 설명 패널의 3번 항목은 "api-auth와 api-bookmark가 Aurora MySQL을 읽고 쓰며, api-auth·api-chatbot·api-bookmark가 ElastiCache Valkey를 쓴다"고 적어 두었습니다. 4번 항목은 "api-emerging-tech, api-bookmark, api-agent가 MSK에서 이벤트를 만들고 소비한다"고 적었습니다. 즉 다이어그램 자체는 Aurora 화살표 두 개(auth, bookmark), Valkey 화살표 세 개(auth, chatbot, bookmark), MSK 화살표 세 개(emerging-tech, bookmark, agent)를 그려 두고 있고, 이 숫자는 처음에 셌던 것과 같습니다.

이걸 앞서 정리한 SG 허가 목록과 겹쳐 보면, Aurora 쪽에서 바로 차이가 드러납니다. 다이어그램은 auth와 bookmark 두 개만 그렸는데, SG는 여기에 emerging-tech와 agent까지 더해 네 개에 인바운드를 열어 두고 있습니다. 반대로 Valkey와 MSK는 다이어그램이 그린 화살표 숫자와 SG 허가 목록의 숫자가 정확히 같습니다(Valkey 세 개, MSK 세 개). 숫자만 보면 "Aurora만 SG가 다이어그램보다 후하게 열려 있다"는 결론을 내리고 싶어집니다. 그런데 여기서 멈추지 않고 실제 코드까지 열어 보니, 숫자가 같다고 해서 그 안의 서비스가 같지는 않다는 걸 알게 됐습니다.

각 서비스의 `build.gradle`에서 `datasource-aurora`, `common-kafka` 의존성을 직접 확인하고, `application-*.yml`에서 `module.aurora.schema` 설정을 찾고, 실제 서비스 클래스가 어떤 리포지토리·엔티티·`RedisTemplate`·`KafkaTemplate`을 참조하는지까지 하나씩 열어 본 결과를 저장소별로 표로 정리하면 다음과 같습니다.

**Aurora MySQL 접근**

| 서비스 | 다이어그램 화살표 | SG 인바운드 허가 | 코드로 확인한 실제 사용 |
|---|---|---|---|
| api-auth | 있음 | 있음 | 있음 — `UserEntity`, `RefreshTokenEntity` 등을 직접 조회·저장 |
| api-bookmark | 있음 | 있음 | 있음 — `BookmarkEntity`, `BookmarkHistoryEntity`를 직접 조회·저장 |
| api-emerging-tech | 없음 | 있음 | 없음 — `datasource-aurora` 의존성 자체가 `build.gradle`에 없음 |
| api-agent | 없음 | 있음 | 있음(간접) — `common-conversation` 모듈을 통해 대화 세션·메시지를 저장 |
| api-chatbot | 없음 | 없음 | 있음(간접) — 자체 `module.aurora.schema: chatbot` 설정을 두고 `common-conversation`을 통해 대화 세션·메시지를 저장 |
| api-gateway | 없음 | 없음 | 없음 |

**ElastiCache Valkey 접근**

| 서비스 | 다이어그램 화살표 | SG 인바운드 허가 | 코드로 확인한 실제 사용 |
|---|---|---|---|
| api-auth | 있음 | 있음 | 있음 — OAuth 콜백 검증용 `state` 값을 10분 TTL로 저장 |
| api-chatbot | 있음 | 있음 | 있음 — 응답 캐시를 TTL 기준으로 저장 |
| api-bookmark | 있음 | 있음 | 없음 — `RedisTemplate`을 참조하는 코드가 없음 |

**Amazon MSK 접근**

| 서비스 | 다이어그램 화살표 | SG 인바운드 허가 | 코드로 확인한 실제 사용 |
|---|---|---|---|
| api-emerging-tech | 있음 | 있음 | 없음 — `common-kafka` 의존성 자체가 없음 |
| api-bookmark | 있음 | 있음 | 없음 — `common-kafka` 의존성 자체가 없음 |
| api-agent | 있음 | 있음 | 있음(간접) — `common-conversation`이 세션·메시지 변경을 이벤트로 발행 |

이 세 표를 나란히 놓고 보면, 어긋남이 한 방향으로만 일어나지 않는다는 게 보입니다. 어떤 서비스는 허가는 있는데 코드가 그 저장소를 아예 모르고, 어떤 서비스는 반대로 코드는 그 저장소를 확실히 쓰는데 SG 허가 목록에는 이름이 없습니다.

## 허가는 있는데 코드가 비어 있는 자리

api-emerging-tech는 Aurora와 MSK 양쪽에서 이런 자리를 만듭니다. `build.gradle`을 보면 이 서비스는 `datasource-mongodb`만 의존성으로 갖고 있고 `datasource-aurora`는 아예 선언하지 않았습니다. 서비스 코드도 `EmergingTechQueryServiceImpl`에서 `MongoTemplate`만 다루고 있어서, 이 서비스가 Aurora의 어떤 테이블도 참조할 방법이 코드 구조상 없습니다. 그런데도 Aurora SG의 인바운드 규칙(`aurora_from_workloads`, `envs/prod/services.tf:305`)은 이 서비스의 워크로드 SG를 소스로 명시적으로 허락하고 있습니다. MSK도 마찬가지입니다. 다이어그램은 emerging-tech가 이벤트를 만들고 소비한다고 적어 두었고 SG도 그 인바운드를 열어 뒀지만, `common-kafka` 의존성이 `build.gradle`에 없고 `@KafkaListener`나 `KafkaTemplate`을 참조하는 코드도 찾을 수 없었습니다.

api-bookmark는 MSK와 Valkey 두 곳에서 비슷한 자리를 만듭니다. Aurora는 실제로 쓰고 있으니 문제가 없지만, MSK 접근은 emerging-tech와 같은 이유로 코드에 흔적이 없고, Valkey 역시 다이어그램과 SG 모두 사용을 명시하는데 `RedisTemplate`을 참조하는 클래스를 찾지 못했습니다. 공용 모듈인 `common-core`가 범용 `RedisTemplate` 빈을 등록해 두고 있어서 어떤 서비스든 의존성 그래프상으로는 Redis에 닿을 수 있는 상태이긴 하지만, bookmark 쪽 서비스 코드가 실제로 그 빈을 주입받아 쓰는 자리는 없었습니다.

이런 자리를 "버그"라고 부르고 싶지는 않습니다. 이 시스템은 여전히 발전 중인 아키텍처이고, MSK 이벤트 발행이나 캐시 활용은 이미 방향이 정해진 채 SG와 다이어그램에 먼저 반영되고, 실제 구현은 아직 그 뒤를 따라가는 중일 수 있습니다. 다만 이 상태 그대로 오래 남아 있다면, 그 사이 누군가는 "이 서비스가 왜 Aurora 인바운드를 허락받았는지" 다시 물어야 하는 순간이 옵니다. AWS IAM 모범 사례 문서는 권한을 "사용 중인지" 주기적으로 점검하고 쓰지 않는 사용자·역할·권한·자격 증명을 제거하라고 권합니다. 이 원칙은 IAM 정책뿐 아니라 네트워크 계층의 Security Group 인바운드 규칙에도 똑같이 적용할 수 있는 태도라고 생각합니다. 열려 있는 포트 하나하나가 공격 표면이 될 수 있다는 점에서는 IAM 권한과 SG 규칙이 다르지 않기 때문입니다.

## 반대 방향의 빈자리 — 코드는 쓰는데 허가가 없다

여기까지는 "허가된 것보다 적게 쓴다"는, 상대적으로 안전한 방향의 어긋남입니다. 그런데 api-chatbot을 들여다보면서 반대 방향의 빈자리를 하나 발견했습니다. api-chatbot은 Aurora SG의 `aurora_consumers` 목록에도, MSK SG의 `msk_consumers` 목록에도 이름이 없습니다. 다이어그램에도 이 서비스에서 Aurora나 MSK로 뻗는 화살표는 그려져 있지 않습니다. 둘 다 "api-chatbot은 이 두 저장소를 쓰지 않는다"고 말하고 있는 셈입니다.

하지만 코드는 다른 이야기를 하고 있었습니다. api-chatbot의 `application-chatbot-api.yml`에는 `module.aurora.schema: chatbot`이라는 설정이 명시적으로 들어 있고, `ChatbotServiceImpl`은 사용자가 메시지를 보낼 때마다 `sessionService.createSession`, `messageService.saveMessage`, `sessionService.updateLastMessageAt`을 호출합니다. 이 세 메서드는 `common-conversation` 모듈의 `ConversationSessionService`, `ConversationMessageService` 구현체가 제공하는데, 이 구현체는 `datasource-aurora`의 `ConversationSessionEntity`, `ConversationMessageEntity`를 JPA로 저장합니다. 다시 말해 채팅 요청 하나가 들어올 때마다 api-chatbot은 Aurora MySQL의 3306 포트에 실제로 연결을 시도합니다. 게다가 `common-conversation`은 `common-kafka`에도 의존하고 있어서, 세션·메시지가 생성되거나 바뀔 때마다 `EventPublisher`를 통해 `tech-n-ai.conversation.session.created` 같은 토픽으로 이벤트를 발행합니다. 이 발행 경로 역시 MSK에 실제로 연결해야만 동작합니다.

정리하면 api-chatbot은 Aurora와 MSK 양쪽 모두, 그것도 매 채팅 요청마다 실행되는 핵심 경로에서 실제로 접속을 시도하는 코드를 갖고 있는데, 그 접속을 허락하는 Security Group 인바운드 규칙은 `services.tf`의 세 소비자 목록 어디에도 없습니다. Security Group은 기본적으로 화이트리스트 방식이라, 명시적으로 허락된 소스가 아니면 해당 포트로의 접근은 거부됩니다. 이 사실만 놓고 보면 api-chatbot이 대화 이력을 저장하려는 순간 Aurora 3306 포트로의 연결이 막혀야 자연스럽습니다.

다만 이 지점에서 정직하게 밝혀야 할 한계가 있습니다. 이 저장소의 코드와 Terraform 설정만으로 확인할 수 있는 것은 여기까지입니다. 실제 운영 환경에서 이 연결이 정말로 거부되고 있는지, 아니면 제가 찾지 못한 다른 경로(예를 들어 다른 모듈이나 별도 리소스에서 부여하는 추가 규칙)로 이미 허용되고 있는지는 CloudWatch나 VPC Flow Logs 같은 실제 운영 신호를 봐야 확정할 수 있습니다. `envs/prod/services.tf` 안에서 Aurora·Valkey·MSK로 향하는 인바운드 규칙은 `aurora_from_workloads`, `cache_from_workloads`, `msk_from_workloads` 세 개뿐이고 이 세 목록 어디에도 api-chatbot의 워크로드 SG는 들어 있지 않다는 것까지는 코드로 확인했지만, 그 이상은 "코드 근거로 확인한 사실"과 "실제 운영에서 벌어지는 일" 사이의 경계로 남겨 둡니다.

## 공유 모듈이 만드는 사각지대

api-agent와 api-chatbot이 나란히 보여 주는 패턴이 하나 있습니다. 둘 다 Aurora·MSK와의 관계가 자기 자신의 비즈니스 로직이 아니라 `common-conversation`이라는 공유 모듈을 거쳐서 생깁니다. api-agent의 `build.gradle`은 `datasource-aurora`와 `common-kafka`를 직접 선언하고 있지만, 정작 api-agent 자신의 코드에는 `@Entity`나 `JpaRepository`, `@KafkaListener`, `KafkaTemplate`을 참조하는 클래스가 하나도 없습니다. `AgentFacade`가 `ConversationSessionService`와 `ConversationMessageService`를 주입받아 쓰는 게 전부이고, 흥미롭게도 이 서비스의 `application.yml`은 `module.aurora.schema: ${AURORA_SCHEMA:chatbot}`으로 돼 있어서, 환경 변수를 따로 지정하지 않으면 api-chatbot과 같은 `chatbot` 스키마를 기본값으로 씁니다. 즉 api-agent와 api-chatbot은 대화 이력이라는 하나의 데이터를 같은 스키마로 공유하도록 설계돼 있습니다.

이 구조 자체는 CLAUDE.md에도 "common-conversation — agent와 chatbot이 공유하는 세션·메시지 저장소"라고 분명히 적혀 있으니 의도된 설계입니다. 문제는 이 공유 관계가 다이어그램에는 전혀 드러나지 않는다는 점입니다. 다이어그램은 서비스와 저장소를 직접 잇는 화살표로 그리는데, "서비스 A가 공유 라이브러리 B를 통해 저장소 C에 접근한다"는 간접 경로는 이 화살표 문법으로 표현하기 어렵습니다. 그래서 api-agent의 Aurora·MSK 접근은 SG에는 반영됐지만 다이어그램에는 빠졌고, api-chatbot의 Aurora·MSK 접근은 다이어그램에도 SG에도 반영되지 못한 채 코드에만 남아 있게 된 것으로 보입니다. 공유 모듈을 새로 도입할 때 그 모듈이 내부적으로 어떤 인프라 자원을 필요로 하는지는, 그 모듈을 가져다 쓰는 서비스의 `build.gradle`만 봐서는 한눈에 드러나지 않습니다. 실제로 무엇을 호출하는지까지 따라 들어가야 알 수 있습니다.

## 최소 권한과 가용성, 서로 다른 두 축

Security Group 인바운드 규칙은 소스를 CIDR 대역이나 다른 보안 그룹의 ID로 지정할 수 있고, 규칙에 명시된 소스가 아니면 해당 포트로의 트래픽은 거부됩니다. AWS 공식 문서는 보안 그룹을 인스턴스나 네트워크 인터페이스에 도달하는 트래픽을 통제하는 가상 방화벽으로 설명하면서, 허용 규칙이 없으면 어떤 인바운드 트래픽도 들어올 수 없다고 명시합니다. 이 시스템의 `aurora_consumers`, `cache_consumers`, `msk_consumers`도 정확히 이 문법 위에서 동작합니다. 워크로드 서비스의 보안 그룹 ID를 소스로 등록해야만 그 서비스가 해당 포트로 연결할 수 있고, 등록되지 않으면 코드가 아무리 그 저장소를 호출하려 해도 네트워크 계층에서 막힙니다.

이번에 찾은 두 방향의 어긋남은 사실 서로 다른 축의 문제입니다. emerging-tech와 bookmark처럼 "허가는 있는데 코드가 없는" 경우는 최소 권한 원칙의 문제입니다. 당장 장애로 이어지지는 않지만, 쓰지 않는 접근 경로가 계속 열려 있다는 것 자체가 불필요한 공격 표면입니다. AWS IAM 모범 사례 문서가 강조하는 "정기적으로 사용하지 않는 권한을 검토하고 제거하라"는 원칙이 정확히 이 상황을 겨냥합니다. 반대로 chatbot처럼 "코드는 있는데 허가가 없는" 경우는 가용성의 문제입니다. 이 경우 문제가 조용히 숨어 있지 않고, 실제로 그 코드 경로가 실행되는 순간 연결이 거부되는 형태로 즉시 드러날 가능성이 높습니다. 두 축은 점검하는 방법도 다릅니다. 전자는 "이 권한을 실제로 쓰는 코드가 있는가"를 코드베이스 안에서 찾으면 되지만, 후자는 "이 코드가 필요로 하는 접근이 인프라 설정에 실제로 존재하는가"를 반대 방향으로 되짚어야 발견됩니다. 이번에 표를 만들면서 두 방향을 모두 훑어보지 않았다면, chatbot 쪽 빈자리는 그냥 지나쳤을 것 같습니다.

## 마치며

이 매트릭스를 만들기 전까지는 "다이어그램이 실제보다 단순하게 그려졌겠거니" 정도로만 생각했습니다. 그런데 막상 SG 규칙과 코드를 나란히 놓고 보니, 단순화의 방향이 하나가 아니었습니다. 어떤 자리는 다이어그램과 SG가 코드보다 앞서 있었고, 어떤 자리는 반대로 코드가 다이어그램과 SG보다 앞서 있었습니다. 셋 중 어느 것도 항상 최신 상태의 정답은 아니라는 걸 이번에 다시 확인한 셈입니다.

개인적으로 남은 습관은, 인프라 문서나 SG 규칙에서 "이 서비스가 이 저장소를 쓴다"는 문장을 볼 때, 그 반대 방향도 함께 물어보는 것입니다. "이 서비스는 정말 이것만 쓰는가"와 "이 서비스가 쓰는 것 중에 여기 빠진 게 있지는 않은가"는 서로 다른 질문이고, 둘 다 물어야 전체 그림이 맞춰집니다. 특히 여러 서비스가 공유 모듈을 통해 같은 자원에 접근하는 구조에서는, 그 모듈의 `build.gradle`과 실제 호출 코드까지 한 겹 더 들어가 봐야 어떤 자원이 실제로 필요한지 알 수 있다는 것도 이번에 얻은 확인이었습니다.

## 참고한 공식 문서

- Amazon VPC 보안 그룹 규칙(소스로 다른 보안 그룹을 지정하는 방식 포함): https://docs.aws.amazon.com/vpc/latest/userguide/security-group-rules.html
- Amazon VPC 보안 그룹 기본 개념(가상 방화벽으로서의 동작, 허용 규칙이 없으면 트래픽이 거부되는 원리): https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html
- AWS IAM 모범 사례 — 최소 권한 부여 및 미사용 권한 정기 점검: https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html

이 글에서 다룬 서비스별 `build.gradle` 의존성, `application-*.yml` 설정, 서비스·리포지토리·엔티티 클래스의 실제 참조 관계는 외부 문서가 아니라 이 저장소 자체의 코드에서 직접 확인한 내용입니다. `envs/prod/services.tf`의 SG 인바운드 규칙과 `devops/aws/prod/reference-architecture.png` 다이어그램 역시 이 저장소 안의 1차 소스입니다.
