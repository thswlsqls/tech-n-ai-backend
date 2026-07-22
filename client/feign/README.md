# Feign Client 모듈

## 개요

`client-feign`은 Spring Cloud OpenFeign으로 외부·내부 API를 호출하는 라이브러리입니다. 외부로는 OAuth 제공자(Google·Naver·Kakao)와 GitHub REST API를, 내부로는 같은 백엔드의 다른 서비스(`api-emerging-tech`, `api-agent`)를 호출합니다. `bootJar.enabled = false`인 라이브러리라 `api-auth`·`batch-source` 등이 의존성으로 가져다 씁니다.

## 주요 기능

- **OAuth 연동 (`domain/oauth`)**: Google·Naver·Kakao. Authorization Code로 토큰을 교환하고(`exchangeAccessToken`) 사용자 정보를 조회합니다(`getUserInfo`). Naver·Kakao는 토큰 교환과 정보 조회 호스트가 달라 Feign Client를 2개씩 둡니다.
- **GitHub 연동 (`domain/github`)**: 이벤트·릴리스 조회. 토큰이 있으면 `Authorization: Bearer` 헤더를 붙입니다.
- **내부 호출 (`domain/internal`, `domain/agent`)**: EmergingTech 내부 API(단건·배치 생성, 검색, 목록, 상세, 승인 6종, `X-Internal-Api-Key` 헤더 필요)와 Agent 실행(`/api/v1/agent/run`, `x-user-id`·`x-user-role` 헤더).
- **Mock / REST 분기**: OAuth·GitHub는 `mode` 값으로 실제 호출(`rest`) 또는 Mock을 빈으로 등록합니다. 내부 API는 항상 실제 호출입니다.

## 패키지 구조

```
com.tech.n.ai.client.feign
├── config/   OpenFeignConfig(공통 확장점), GitHubFeignConfig,
│             AgentFeignConfig, EmergingTechInternalFeignConfig
└── domain/
    ├── oauth/      config(OAuthFeignConfig)·contract·api·client·mock
    ├── github/     contract·api·client·mock
    ├── internal/   contract·api·client   (EmergingTech 내부 API)
    └── agent/      contract·api·client    (Agent 내부 API)

각 도메인: *Contract(비즈니스 인터페이스) · *Api(구현) · *FeignClient(HTTP 매핑) · *Mock
```

## 설계 패턴

- **Contract 패턴**: 도메인마다 비즈니스 인터페이스(`*Contract`), 구현(`*Api`), Feign 매핑(`*FeignClient`)을 분리합니다. 호출자는 `*Contract`만 참조해 Feign 세부와 떨어집니다.
- **조건부 빈 (`@ConditionalOnProperty`)**: `mode=mock|rest`로 Mock과 실제 구현을 갈아 끼웁니다.
- **응답 검증**: OAuth `*Api`는 응답이 비거나 필수 필드가 없으면 `UnauthorizedException`을 던집니다. 사용자 이름 우선순위는 제공자마다 다릅니다(Google `name`→`email`, Kakao 닉네임→이메일→`KakaoUser_{id}`).
- **DTO 독립**: `InternalApiDto`에 "모듈 간 DTO 공유 금지"가 명시돼 있어, 대상 모듈과 필드가 같아도 별도 정의합니다.

## 기술 스택

- **spring-cloud-starter-openfeign**: 선언형 HTTP 클라이언트
- **공통 모듈**: `common-core`, `common-kafka`, `common-exception`(`UnauthorizedException`)
- `datasource-aurora`·`datasource-mongodb`는 전이 의존성으로 포함(이 모듈 코드에서 직접 사용 안 함)

타임아웃: GitHub·OAuth는 프로필별 선언으로 connect 3초·read 30초, 내부 API는 connect 5초·read 30초(agent는 read 60초). prod의 GitHub·OAuth 호출은 OkHttp + 커넥션 풀 확대(35,000), 커넥션 timeout 120초.

## 설정

| 키 | 기본값 |
|---|---|
| `feign-clients.github.uri` / `.token` / `.mode` | `https://api.github.com` / (빈 값) / `rest`(test `mock`) |
| `feign-clients.oauth.mode` | `rest` (test `mock`) |
| `feign-clients.oauth.google.uri` | `https://oauth2.googleapis.com` |
| `feign-clients.oauth.naver.uri` / `.userinfo.uri` | `https://nid.naver.com` / `https://openapi.naver.com` |
| `feign-clients.oauth.kakao.uri` / `.userinfo.uri` | `https://kauth.kakao.com` / `https://kapi.kakao.com` |
| `feign.client.config.emerging-tech-internal-api.url` | `http://localhost:8082` |
| `feign.client.config.agent-api.url` | `http://localhost:8086` |

설정 파일은 도메인별로 나뉩니다: `application-feign-oauth.yml`, `-github.yml`, `-internal.yml`.

## 사용 예시

```java
// OAuth 로그인
String accessToken = googleOAuthContract.exchangeAccessToken(code, clientId, clientSecret, redirectUri);
OAuthDto.OAuthUserInfo userInfo = googleOAuthContract.getUserInfo(accessToken);

// 내부 API — X-Internal-Api-Key는 호출자가 직접 전달
internalContract.approve(apiKey, id);
```

## 현재 구현 범위

- **Mock 지원은 GitHub·OAuth에만**. internal·agent는 항상 실제 호출입니다.
- **`X-Internal-Api-Key`는 자동으로 안 채워집니다.** 호출자가 설정값(`internal-api.*.api-key`)을 읽어 Contract 인자로 직접 넘겨야 합니다.
- **`AgentFeignClient`**는 다른 도메인과 달리 `AgentContract`를 직접 `extends`합니다.

## 참고 문서

- [Spring Cloud OpenFeign](https://spring.io/projects/spring-cloud-openfeign)
- [Google OAuth 2.0](https://developers.google.com/identity/protocols/oauth2) · [네이버 로그인](https://developers.naver.com/docs/login/api/api.md) · [Kakao 로그인](https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api)
- [GitHub REST API](https://docs.github.com/en/rest)
