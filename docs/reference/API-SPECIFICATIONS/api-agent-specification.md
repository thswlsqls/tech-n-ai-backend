# Agent API 설계서

**작성일**: 2026-02-06
**대상 모듈**: api-agent
**버전**: v4

---

## 1. 개요


| 항목       | 내용                                        |
| -------- | ----------------------------------------- |
| 모듈       | api-agent                                 |
| Base URL | `/api/v1/agent`                           |
| 포트       | 8086 (via Gateway: 8081)                  |
| 설명       | LangChain4j 기반 AI Agent API (ADMIN 역할 전용) |


### 인증

모든 API는 **ADMIN 역할** JWT 인증이 필요합니다.
Gateway에서 JWT 역할 기반 인증을 수행합니다.


| 헤더            | 타입     | 필수  | 설명                       |
| ------------- | ------ | --- | ------------------------ |
| Authorization | String | O   | `Bearer {accessToken}`   |
| x-user-id     | String | O   | Gateway가 주입한 사용자 ID (자동) |


---

## 2. 공통 응답 형식

### ApiResponseT

```json
{
  "code": "2000",
  "messageCode": {
    "code": "SUCCESS",
    "text": "성공"
  },
  "message": "success",
  "data": {...}
}
```


| 필드          | 타입          | 필수  | 설명        |
| ----------- | ----------- | --- | --------- |
| code        | String      | O   | 응답 코드     |
| messageCode | MessageCode | O   | 메시지 코드 객체 |
| message     | String      | X   | 응답 메시지    |
| data        | T           | X   | 응답 데이터    |


---

## 3. Agent API

### 3.1 Agent 실행

**POST** `/api/v1/agent/run`

**인증**: 필요 (ADMIN)

AI Agent를 실행합니다. `goal`에 따라 Emerging Tech 데이터 수집, 검색, 분석 등을 자율적으로 수행합니다.

**Request Body**

```json
{
  "goal": "최신 AI 기술 동향을 수집하고 분석해줘",
  "sessionId": "admin-123-abc12345"
}
```

**AgentRunRequest 필드**


| 필드        | 타입     | 필수  | 검증       | 설명                                             |
| --------- | ------ | --- | -------- | ---------------------------------------------- |
| goal      | String | O   | NotBlank | 실행 목표 (자연어)                                    |
| sessionId | String | X   | -        | 세션 식별자 (미지정 시 TSID 기반 자동 생성, 응답으로 반환) |


#### 세션 관리

- 동일한 `sessionId`로 요청하면 이전 대화 맥락을 유지하는 **멀티 턴 대화**가 가능합니다.
- 세션당 최대 **30개 메시지**를 인메모리 윈도우로 보관하며, `MongoDbChatMemoryStore`를 통해 영속 저장소에서 이력을 로드합니다 (초과 시 오래된 메시지부터 제외).
- 전체 대화 이력은 **Aurora MySQL + MongoDB**에 영속화됩니다 (CQRS 패턴, `common-conversation` 모듈 기반).
- `sessionId`를 생략하면 매 요청마다 새 세션이 생성되고, 응답의 `sessionId` 필드로 TSID가 반환됩니다.

**Response** (200 OK) `ApiResponse<AgentExecutionResult>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "success": true,
    "summary": "GitHub에서 10개, RSS에서 5개의 신기술 정보를 수집했습니다.",
    "sessionId": "admin-123-abc12345",
    "toolCallCount": 15,
    "analyticsCallCount": 3,
    "executionTimeMs": 12500,
    "errors": []
  }
}
```

**AgentExecutionResult 필드**


| 필드                 | 타입       | 필수  | 설명                                                     |
| ------------------ | -------- | --- | ------------------------------------------------------ |
| success            | boolean  | O   | 실행 성공 여부                                               |
| summary            | String   | O   | LLM이 생성한 실행 결과 요약 (Markdown 포함 가능)                     |
| sessionId          | String   | O   | 세션 식별자 (멀티 턴 대화 시 다음 요청에 재사용)                         |
| toolCallCount      | int      | O   | Tool 호출 횟수                                             |
| analyticsCallCount | int      | O   | 분석 Tool 호출 횟수 (통계/빈도 분석)                               |
| executionTimeMs    | long     | O   | 실행 시간 (밀리초)                                            |
| errors             | String[] | O   | 에러 메시지 목록 (성공 시 빈 배열)                                  |


> **참고**: `summary`는 LLM이 생성한 자연어 텍스트입니다. Markdown 표, Mermaid 차트 등이 포함될 수 있으므로 프론트엔드에서 Markdown 렌더링을 권장합니다. 응답 시간은 LLM 처리와 외부 API 호출에 따라 **10~60초** 소요될 수 있습니다.

**Errors**


| HTTP 상태 | 상황             | 에러 코드 | 메시지                    |
| ------- | -------------- | ----- | ---------------------- |
| 400     | goal 필드 누락/빈 값 | 4000  | goal은 필수입니다.           |
| 401     | 인증 실패          | 4010  | Unauthorized           |
| 403     | ADMIN 역할 없음    | 4030  | 관리자 권한이 필요합니다.         |
| 500     | Agent 실행 중 오류  | 5000  | Agent 실행 중 오류가 발생했습니다. |


---

## 4. 세션 관리 API

common-conversation 모듈 기반의 대화 세션 영속화 API입니다.
모든 대화는 Aurora MySQL(Command) + MongoDB(Query)에 CQRS 패턴으로 저장됩니다.

### 4.1 세션 목록 조회

**GET** `/api/v1/agent/sessions`

**인증**: 필요 (ADMIN)

**Query Parameters**

| 필드   | 타입      | 필수 | 기본값 | 검증         | 설명           |
|--------|---------|------|------|------------|--------------|
| page   | Integer | X    | 1    | Min(1)     | 페이지 번호       |
| size   | Integer | X    | 20   | Min(1), Max(100) | 페이지 크기 |

**Response** (200 OK) `ApiResponse<AgentSessionListResponse>`

```json
{
  "code": "2000",
  "data": {
    "data": {
      "pageSize": 20,
      "pageNumber": 1,
      "totalPageNumber": 1,
      "totalSize": 2,
      "list": [
        {
          "sessionId": "admin-123-abc12345",
          "title": null,
          "createdAt": "2026-03-13T10:00:00",
          "lastMessageAt": "2026-03-13T10:05:00",
          "isActive": true
        }
      ]
    }
  }
}
```

### 4.2 세션 상세 조회

**GET** `/api/v1/agent/sessions/{sessionId}`

**인증**: 필요 (ADMIN)

**Response** (200 OK) `ApiResponse<SessionResponse>`

**Errors**

| HTTP 상태 | 상황 | 에러 코드 | 메시지 |
|---------|------|---------|------|
| 400 | 세션 ID 형식 오류 | 4000 | 유효하지 않은 세션 ID 형식입니다. |
| 403 | 다른 사용자 세션 접근 | 4030 | 세션에 대한 접근 권한이 없습니다. |
| 404 | 세션 없음/삭제됨 | 4040 | 세션을 찾을 수 없습니다. |

### 4.3 대화 이력 조회

**GET** `/api/v1/agent/sessions/{sessionId}/messages`

**인증**: 필요 (ADMIN)

**Query Parameters**

| 필드   | 타입      | 필수 | 기본값 | 검증         | 설명           |
|--------|---------|------|------|------------|--------------|
| page   | Integer | X    | 1    | Min(1)     | 페이지 번호       |
| size   | Integer | X    | 50   | Min(1), Max(100) | 페이지 크기 |

**Response** (200 OK) `ApiResponse<AgentMessageListResponse>`

```json
{
  "code": "2000",
  "data": {
    "data": {
      "pageSize": 50,
      "pageNumber": 1,
      "totalPageNumber": 1,
      "totalSize": 4,
      "list": [
        {
          "messageId": "msg-1",
          "sessionId": "admin-123-abc12345",
          "role": "USER",
          "content": "OpenAI 최신 업데이트 확인해줘",
          "tokenCount": 15,
          "sequenceNumber": 1,
          "createdAt": "2026-03-13T10:00:00"
        }
      ]
    }
  }
}
```

### 4.4 세션 삭제

**DELETE** `/api/v1/agent/sessions/{sessionId}`

**인증**: 필요 (ADMIN)

**Response** (200 OK) `ApiResponse<Void>`

**Errors**

| HTTP 상태 | 상황 | 에러 코드 | 메시지 |
|---------|------|---------|------|
| 400 | 세션 ID 형식 오류 | 4000 | 유효하지 않은 세션 ID 형식입니다. |
| 403 | 다른 사용자 세션 접근 | 4030 | 세션에 대한 접근 권한이 없습니다. |
| 404 | 세션 없음/삭제됨 | 4040 | 세션을 찾을 수 없습니다. |

---

## 5. Agent Tool 목록 (변경 없음)

Agent는 `goal`에 따라 아래 Tool을 자율적으로 선택·조합하여 실행합니다.
프론트엔드가 Tool을 직접 호출하지 않으며, Agent 응답(`summary`)에 Tool 실행 결과가 자연어로 포함됩니다.

### 데이터 조회


| Tool 이름                    | 설명                               |
| -------------------------- | -------------------------------- |
| `fetch_github_releases`    | GitHub 저장소의 최신 릴리스 목록 조회         |
| `scrape_web_page`          | 웹 페이지를 크롤링하여 텍스트 내용 추출           |
| `search_emerging_techs`    | 저장된 Emerging Tech 업데이트를 키워드로 검색  |
| `list_emerging_techs`      | 기간/필터별 Emerging Tech 목록 조회 (페이징) |
| `get_emerging_tech_detail` | Emerging Tech 상세 정보를 ID로 조회      |


### 데이터 분석


| Tool 이름                        | 설명                                    |
| ------------------------------ | ------------------------------------- |
| `get_emerging_tech_statistics` | Provider/SourceType/UpdateType별 통계 집계 |
| `analyze_text_frequency`       | title/summary 텍스트의 키워드 빈도 분석          |


### 데이터 수집


| Tool 이름                    | 설명                                       |
| -------------------------- | ---------------------------------------- |
| `collect_github_releases`  | GitHub 저장소 릴리스를 수집하여 DB에 저장              |
| `collect_rss_feeds`        | OpenAI/Google AI 블로그 RSS 피드를 수집하여 DB에 저장 |
| `collect_scraped_articles` | Anthropic/Meta AI 블로그를 크롤링하여 DB에 저장      |


### 알림


| Tool 이름                   | 설명                      |
| ------------------------- | ----------------------- |
| `send_slack_notification` | Slack 채널에 관리자 알림 메시지 전송 |


---

## 6. Enum 허용 값

### Provider


| 값           | 설명        |
| ----------- | --------- |
| `OPENAI`    | OpenAI    |
| `ANTHROPIC` | Anthropic |
| `GOOGLE`    | Google    |
| `META`      | Meta      |
| `XAI`       | xAI       |


### UpdateType


| 값                 | 설명       |
| ----------------- | -------- |
| `MODEL_RELEASE`   | 모델 출시    |
| `API_UPDATE`      | API 업데이트 |
| `SDK_RELEASE`     | SDK 릴리스  |
| `PRODUCT_LAUNCH`  | 제품 출시    |
| `PLATFORM_UPDATE` | 플랫폼 업데이트 |
| `BLOG_POST`       | 블로그 포스트  |


### SourceType


| 값                | 설명         |
| ---------------- | ---------- |
| `GITHUB_RELEASE` | GitHub 릴리스 |
| `RSS`            | RSS 피드     |
| `WEB_SCRAPING`   | 웹 스크래핑     |


### Status


| 값           | 설명    |
| ----------- | ----- |
| `DRAFT`     | 초안    |
| `PENDING`   | 검토 대기 |
| `PUBLISHED` | 게시됨   |
| `REJECTED`  | 반려됨   |


> **참고**: 수집 Tool의 Provider는 소스별로 제한됩니다.
>
> - RSS 수집: `OPENAI`, `GOOGLE`만 허용
> - 웹 스크래핑 수집: `ANTHROPIC`, `META`만 허용

---

## 7. 엔드포인트 요약


| Method | Endpoint                                      | 인증        | 설명        |
| ------ | --------------------------------------------- | --------- | --------- |
| POST   | `/api/v1/agent/run`                           | O (ADMIN) | Agent 실행  |
| GET    | `/api/v1/agent/sessions`                      | O (ADMIN) | 세션 목록 조회  |
| GET    | `/api/v1/agent/sessions/{sessionId}`          | O (ADMIN) | 세션 상세 조회  |
| GET    | `/api/v1/agent/sessions/{sessionId}/messages` | O (ADMIN) | 대화 이력 조회  |
| DELETE | `/api/v1/agent/sessions/{sessionId}`          | O (ADMIN) | 세션 삭제     |


---

**문서 버전**: 4.0
**최종 업데이트**: 2026-03-13