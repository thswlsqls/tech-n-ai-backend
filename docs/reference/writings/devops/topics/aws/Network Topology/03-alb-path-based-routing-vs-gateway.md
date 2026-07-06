# 03. "path-based → :8081-8086" — 다이어그램 라벨 한 줄을 파고들다 찾아낸 인증 우회, 그리고 고친 방법

> 1차 소스: [`devops/aws/{dev,beta,prod}/network-topology.png`](../../../../../aws/prod/network-topology.png) · [`architecture-facts.md` §1 서비스 목록·ALB + 라우팅](../../../../../aws/architecture-facts.md) · 저장소 코드(`common/security`, `api/agent`, `api/gateway`)

## 한줄 요약(Hook)

> 다이어그램 속 ALB 옆에는 "path-based → :8081-8086"이라는 짧은 라벨이 붙어 있다. 이 라벨을 실제 리스너 규칙으로 풀어보면, ALB는 `/agent/*`를 api-gateway를 거치지 않고 곧장 api-agent(8086)로 넘긴다. 그런데 api-agent는 게이트웨이가 JWT를 검증한 뒤에만 넣어주는 `x-user-id` 헤더를 클라이언트가 보낸 값 그대로 믿고 있었다 — 즉 이 경로만 놓고 보면 헤더 하나만 조작해도 다른 사용자로 가장할 수 있는 상태였다. 다이어그램 한 줄을 코드까지 따라간 끝에 실제 인증 우회를 찾아냈고, auth·chatbot·bookmark가 이미 쓰고 있던 패턴을 그대로 가져와 막았다.

## 핵심 질문

- ALB 리스너 규칙(우선순위 100~140)이 서비스별 경로를 직접 target group에 매핑할 때, "게이트웨이를 거치지 않는 경로"가 실제로 존재한다는 사실을 다이어그램과 코드에서 어떻게 확인할 수 있는가?
- api-gateway가 JWT 검증 후 주입해 주는 `x-user-id` 같은 헤더를, 게이트웨이 뒤 서비스가 "이미 검증됐다"고 가정하고 그대로 믿는 설계는 왜 위험한가?
- 같은 저장소 안에서 auth·chatbot·bookmark는 왜 이미 안전했고, api-agent와 emerging-tech는 왜 서로 다른 이유로 취약/부분 취약했는가?
- 이 문제를 "각 서비스가 자기 요청을 스스로 검증한다"는 원칙으로 고칠 때, 기존 서비스가 쓰던 패턴을 그대로 재사용하는 것이 왜 새 보안 계층을 설계하는 것보다 나은 선택인가?

## 다루는 관점

- ✅ 설계 근거(Why) — 인프라 라우팅 계층(ALB)과 애플리케이션 인증 계층(게이트웨이/서비스)의 책임을 어떻게 나눠야 하는가
- ✅ 구현(코드 근거) — 서비스별 `build.gradle`·`ServerConfig`·컨트롤러 코드 대조로 취약/안전 여부를 가른 실제 차이
- ✅ 보안 — 헤더 기반 신뢰 전파(trust propagation)의 실패 지점과, "각 계층이 스스로 검증한다"는 방어적 설계 원칙

## 근거

- 다이어그램 사실: network-topology.png(dev/beta/prod 공통) ALB 노드 라벨 "path-based → :8081-8086"
- `architecture-facts.md` §1 서비스 목록 표(25~32행) — 서비스별 container port·listener priority·path 전체 목록(`api-gateway` 1000/`/*` 폴백, `api-agent` 140/`/agent/*` 등 5개 경로는 게이트웨이를 거치지 않고 직접 매핑)
- `architecture-facts.md` §1 ALB + 라우팅(45행) — "라우팅은 path-based, 우선순위로 매칭. host header 조건은 사용 안 함"
- 코드 근거(수정 전 상태, git 이력으로 확인 가능):
  - `common/security/.../config/SecurityConfig.java` — `JwtAuthenticationFilter`를 필터체인에 등록하고 `.anyRequest().authenticated()`를 기본으로 거는 공유 설정
  - `api/auth`, `api/chatbot`, `api/bookmark`의 `build.gradle`은 `common-security`를 의존하고, 각 `ServerConfig.java`가 `@ComponentScan`으로 `com.tech.n.ai.common.security`를 스캔하며 `@Import(SecurityConfig.class)`로 위 설정을 로드함 — 컨트롤러는 `@AuthenticationPrincipal UserPrincipal`로 검증된 사용자 정보를 받음(`ChatbotController` 등)
  - `api/agent`의 `build.gradle`에는 `common-security` 의존성이 없었고, `ServerConfig.java`에도 `common.security` 스캔·`SecurityConfig` import가 없었음 — Spring Security 자체가 아예 동작하지 않는 상태
  - `AgentController.java`의 6개 엔드포인트 모두 `@RequestHeader("x-user-id") String userId`로 클라이언트가 보낸 헤더값을 그대로 서비스 계층에 전달
  - `api/gateway/.../filter/JwtAuthenticationGatewayFilter.java` — JWT를 검증한 뒤에만 `x-user-id`/`x-user-email`/`x-user-role` 헤더를 주입하고, 원본 `Authorization` 헤더도 그대로 하위로 전달함(103~106행)
  - `api/gateway/.../application.yml`의 `gateway.security.admin-only-paths`에 `/api/v1/agent/**`가 등록돼 있어, 설계 의도상 이 경로는 ADMIN 역할 전용이었음
  - `api/emerging-tech`는 `common-security`가 없지만, 조회 API는 원래 공개이고 쓰기 API만 별도의 `InternalApiKeyValidator`(`X-Internal-Api-Key` 헤더 대조)로 보호되는 다른 패턴이라 같은 방식의 사용자 가장 문제는 없었음
- 수정 후 상태(이번에 적용한 변경):
  - `common/security/.../config/SecurityConfig.java`에 `.requestMatchers("/api/v1/agent/**").hasRole("ADMIN")` 한 줄 추가 — 게이트웨이의 `admin-only-paths`와 동일한 제약을 서비스 자체에도 강제
  - `api/agent/build.gradle`에 `common-security` 의존성 추가
  - `api/agent/.../config/ServerConfig.java`에 `com.tech.n.ai.common.security` 컴포넌트 스캔과 `SecurityConfig` import 추가
  - `AgentController.java`의 `@RequestHeader("x-user-id")` 6곳을 전부 `@AuthenticationPrincipal UserPrincipal`로 교체, `userPrincipal.userId().toString()`으로 다운스트림 호출
  - `AgentControllerTest.java`를 `ChatbotControllerTest`와 동일한 커스텀 `HandlerMethodArgumentResolver` 패턴으로 갱신
  - `:api-agent:test`, `:common-security:test`, `:api-auth:test`, `:api-chatbot:test`, `:api-bookmark:test` 전체 통과 확인(공유 `SecurityConfig` 변경이 기존 서비스에 회귀를 만들지 않음을 검증)

## 타깃 독자 & 난이도

- ALB와 애플리케이션 레벨 API 게이트웨이를 함께 쓰는 MSA를 설계·운영하며, "게이트웨이만 지키면 안전하다"는 가정을 점검하고 싶은 백엔드/보안 엔지니어
- ★★★☆☆ (사전지식: ALB 리스너 규칙, Spring Security 필터 체인, JWT 인증 기본 개념)

## 예상 분량

- 김 (~5,000자)

## 글 아웃라인

1. **들어가며 — 다이어그램의 짧은 라벨 하나가 던진 질문**
   - "path-based → :8081-8086"을 그대로 읽으면 이미 라우팅이 끝난 것처럼 보인다는 관찰에서, "그럼 게이트웨이 없이 이 경로로 요청이 가면 무슨 일이 일어나는가"라는 질문으로 이어감
2. **리스너 규칙을 표로 펼쳐보기 — 우선순위 100부터 1000까지**
   - `architecture-facts.md`의 서비스 목록 표를 근거로, 어떤 경로가 어떤 포트로 직접 가는지 정리
   - `api-gateway`가 `/*` 폴백(우선순위 1000, 가장 낮음)이라는 사실이 의미하는 것 — 명시적으로 매칭되는 5개 경로는 게이트웨이를 거치지 않는다
3. **"모든 외부 트래픽은 게이트웨이를 통과한다"는 설계 문장을, 서비스별 코드 대조로 검증하기**
   - auth·chatbot·bookmark는 이미 `common-security`를 물고 있어 게이트웨이 없이도 자체적으로 JWT를 재검증한다는 사실을 코드로 확인
   - api-agent만 이 방어선이 완전히 비어 있었다는 사실을, `@RequestHeader("x-user-id")`가 원래 누가 채워주는 값인지 추적해서 드러냄
   - emerging-tech는 또 다른 패턴(공개 API + 별도 API 키)이라 같은 문제가 아니라는 점도 짧게 대조
4. **헤더 기반 신뢰 전파가 실패하는 지점 — "이미 검증됐다"는 가정이 깨지는 순간**
   - 게이트웨이가 `x-user-id`를 주입하는 것은 "게이트웨이를 거쳤다면"이라는 전제 위에서만 유효한데, ALB의 path-based 라우팅이 그 전제를 깨뜨릴 수 있다는 구조적 설명
   - AWS 공식 문서 기준 ALB 리스너 규칙의 동작 방식(우선순위 평가, path-pattern 조건)이 "게이트웨이를 우회하는 경로"가 인프라 설정만으로 자연스럽게 만들어질 수 있음을 보여줌
5. **고친 방법 — 새 보안 계층을 만들지 않고, 이미 검증된 패턴을 재사용하기**
   - `common-security`의 `SecurityConfig`/`JwtAuthenticationFilter`/`UserPrincipal`을 api-agent에도 그대로 연결한 과정
   - 게이트웨이의 `admin-only-paths` 목록과 서비스 자체의 권한 규칙을 같은 기준으로 맞춘 것 — "게이트웨이가 지키는 규칙"과 "서비스가 지키는 규칙"이 서로 다른 목록으로 따로 관리되면 이런 간극이 다시 생길 수 있다는 점을 남김
   - 컨트롤러가 클라이언트/상위 계층이 보낸 헤더값 대신 자기 프로세스에서 검증한 인증 정보(`@AuthenticationPrincipal`)를 쓰도록 바꾼 것의 의미
6. **결론 — 다이어그램 한 줄이 숨긴 아키텍처 결정을 코드로 검증하는 습관**
   - 다이어그램·설계 문서·실제 리스너 규칙·실제 컨트롤러 코드, 네 가지가 어긋날 수 있다는 사실과, 그 어긋남을 찾아내는 절차 자체가 이 글의 결론

## 참고할 1차 출처

- ALB 리스너 규칙 조건 타입(path-pattern 포함): https://docs.aws.amazon.com/elasticloadbalancing/latest/application/rule-condition-types.html
- ALB CreateRule API(우선순위 평가 방식): https://docs.aws.amazon.com/elasticloadbalancing/latest/APIReference/API_CreateRule.html
- Application Load Balancer 리스너 개요: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html
- 코드 근거는 이 저장소 자체(`common/security`, `api/agent`, `api/gateway`)이며, 별도의 외부 공식 문서 인용이 아니라 프로젝트 1차 소스로 표기한다.

## 시리즈 인용 관계

시리즈 외 독립 자산이다. 이 단편은 다이어그램 세 환경에 공통되는 사실(라우팅 구조는 env와 무관)을 다루므로, "환경별 성숙도"를 다루는 [series-01](./series-01-three-diagrams-maturity-signal.md)에는 인용되지 않는다.

## 작성 메모

- 이 글은 더 이상 "확인이 필요한 지점"을 남겨 두는 글이 아니다. 발견과 수정이 모두 끝난 상태이므로, 결론을 흐릿하게 두지 말고 무엇을 찾았고 어떻게 고쳤는지를 분명하게 적는다.
- 다만 공개 글이라는 점을 고려해, 구체적인 공격 재현 절차(예: 실제 요청 예시, 특정 사용자 ID를 흉내 내는 curl 명령 등)는 넣지 않는다. "무엇이 문제였는가"와 "왜 그 구조가 위험한가", "어떻게 고쳤는가"에 집중하고, 그대로 따라 하면 다른 시스템의 유사 취약점을 찾는 안내서가 되지 않도록 일반화된 설명 수준을 유지한다.
- "버그였다"를 비난조로 쓰지 않는다. 게이트웨이 계층에서 인증을 중앙화하는 설계 자체는 합리적인 출발점이었고, 이번 발견은 "그 중앙화가 인프라 라우팅 설정과 어떻게 어긋날 수 있는가"를 보여주는 사례라는 톤을 유지한다.
- 이미 고쳐진 코드(auth/chatbot/bookmark의 기존 패턴)를 재사용한 것이 핵심이므로, "새로운 방어 장치를 발명했다"는 과장된 서술을 피한다.
