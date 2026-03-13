# Chatbot API 설계서

**작성일**: 2026-02-06
**대상 모듈**: api-chatbot
**버전**: v2

---

## 1. 개요

| 항목 | 내용 |
|------|------|
| 모듈 | api-chatbot |
| Base URL | `/api/v1/chatbot` |
| 포트 | 8084 (via Gateway: 8081) |
| 설명 | RAG 기반 AI 챗봇 API (대화, 세션 관리) |

### 인증

모든 API는 JWT 인증이 필요합니다.

| 헤더 | 타입 | 필수 | 설명 |
|------|------|------|------|
| Authorization | String | O | `Bearer {accessToken}` |

---

## 2. 공통 응답 형식

### ApiResponse<T>

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

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| code | String | O | 응답 코드 |
| messageCode | MessageCode | O | 메시지 코드 객체 |
| message | String | X | 응답 메시지 |
| data | T | X | 응답 데이터 |

### PageData\<T\>

페이징 응답에 사용되는 공통 래퍼 객체입니다.

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| pageSize | Integer | O | 페이지 크기 |
| pageNumber | Integer | O | 현재 페이지 번호 (1부터 시작) |
| totalPageNumber | Integer | O | 전체 페이지 수 |
| totalSize | Integer | O | 전체 데이터 수 |
| list | T[] | O | 데이터 목록 |

---

## 3. 채팅 API

### 3.1 채팅 메시지 전송

**POST** `/api/v1/chatbot`

**인증**: 필요

AI 챗봇에게 메시지를 보내고 응답을 받습니다. 세션 ID가 없으면 새 세션이 생성됩니다.

**Request Body**

```json
{
  "message": "최신 AI 기술 트렌드에 대해 알려줘",
  "conversationId": "sess_abc123def456"
}
```

**ChatRequest 필드**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| message | String | O | NotBlank, Max(500) | 사용자 메시지 (최대 500자) |
| conversationId | String | X | - | 대화 세션 ID (없으면 새 세션 생성) |

**Response** (200 OK) `ApiResponse<ChatResponse>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "response": "최신 AI 기술 트렌드를 알려드리겠습니다...",
    "conversationId": "sess_abc123def456",
    "title": null,
    "sources": [
      {
        "documentId": "doc_789xyz",
        "collectionType": "EMERGING_TECH",
        "score": 0.95,
        "title": "2025 AI 트렌드 리포트",
        "url": "https://example.com/ai-trends-2025"
      }
    ]
  }
}
```

**ChatResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| response | String | O | AI 응답 메시지 |
| conversationId | String | O | 대화 세션 ID |
| title | String | X | 세션 타이틀 (비동기 생성으로 null일 수 있음) |
| sources | SourceResponse[] | X | 참조한 소스 목록 (RAG) |

**SourceResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| documentId | String | X | 문서 ID |
| collectionType | String | X | 컬렉션 타입 (EMERGING_TECH, NEWS 등) |
| score | Double | X | 관련도 점수 (0~1) |
| title | String | X | 소스 제목 (RAG 벡터 검색, 웹 검색) |
| url | String | X | 소스 URL (RAG 벡터 검색, 웹 검색) |

**Errors**
- `400` - 빈 메시지, 메시지 길이 초과 (500자), 토큰 한도 초과
- `401` - 인증 실패

---

## 4. 대화 세션 API

> **참고**: 세션/메시지 서비스 계층은 `common-conversation` 공유 모듈로 추출되어 api-agent와 동일한 인프라를 공유합니다. 세션 ID는 TSID(Time-Sorted Unique Identifier) 기반입니다.

### 4.1 세션 목록 조회

**GET** `/api/v1/chatbot/sessions`

**인증**: 필요

사용자의 대화 세션 목록을 조회합니다. 최근 메시지 시간 기준 내림차순 정렬됩니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 검증 | 설명 |
|----------|------|------|--------|------|------|
| page | Integer | X | 1 | Min(1) | 페이지 번호 (1부터 시작) |
| size | Integer | X | 20 | Min(1), Max(100) | 페이지 크기 |

**Response** (200 OK) `ApiResponse<SessionListResponse>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "data": {
      "pageSize": 20,
      "pageNumber": 1,
      "totalPageNumber": 1,
      "totalSize": 15,
      "list": [
        {
          "sessionId": "sess_abc123def456",
          "title": "AI 트렌드에 대한 대화",
          "createdAt": "2025-01-20T10:00:00",
          "lastMessageAt": "2025-01-20T14:30:00",
          "isActive": true
        }
      ]
    }
  }
}
```

**SessionResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| sessionId | String | O | 세션 ID |
| title | String | X | 세션 제목 (첫 메시지 기반 자동 생성) |
| createdAt | String (ISO 8601) | O | 세션 생성일시 |
| lastMessageAt | String (ISO 8601) | X | 마지막 메시지 일시 |
| isActive | Boolean | O | 세션 활성화 여부 |

**Errors**
- `401` - 인증 실패

---

### 4.2 세션 상세 조회

**GET** `/api/v1/chatbot/sessions/{sessionId}`

**인증**: 필요

특정 대화 세션의 상세 정보를 조회합니다.

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| sessionId | String | O | 세션 ID |

**Response** (200 OK) `ApiResponse<SessionResponse>`

SessionResponse 형식

**Errors**
- `401` - 인증 실패
- `403` - 해당 세션에 접근할 권한 없음
- `404` - 세션 없음

---

### 4.3 세션 메시지 목록 조회

**GET** `/api/v1/chatbot/sessions/{sessionId}/messages`

**인증**: 필요

특정 세션의 메시지 목록을 조회합니다. 시퀀스 번호 기준 오름차순 정렬됩니다.

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| sessionId | String | O | 세션 ID |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 검증 | 설명 |
|----------|------|------|--------|------|------|
| page | Integer | X | 1 | Min(1) | 페이지 번호 (1부터 시작) |
| size | Integer | X | 50 | Min(1), Max(100) | 페이지 크기 |

**Response** (200 OK) `ApiResponse<MessageListResponse>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "data": {
      "pageSize": 50,
      "pageNumber": 1,
      "totalPageNumber": 1,
      "totalSize": 2,
      "list": [
        {
          "messageId": "msg_xyz789",
          "sessionId": "sess_abc123def456",
          "role": "USER",
          "content": "최신 AI 기술 트렌드에 대해 알려줘",
          "tokenCount": 25,
          "sequenceNumber": 1,
          "createdAt": "2025-01-20T14:25:00"
        },
        {
          "messageId": "msg_xyz790",
          "sessionId": "sess_abc123def456",
          "role": "ASSISTANT",
          "content": "최신 AI 기술 트렌드를 알려드리겠습니다...",
          "tokenCount": 150,
          "sequenceNumber": 2,
          "createdAt": "2025-01-20T14:25:05"
        }
      ]
    }
  }
}
```

**MessageResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| messageId | String | O | 메시지 ID |
| sessionId | String | O | 세션 ID |
| role | String | O | 메시지 역할 (`USER`, `ASSISTANT`) |
| content | String | O | 메시지 내용 |
| tokenCount | Integer | X | 토큰 수 |
| sequenceNumber | Integer | O | 메시지 순서 번호 |
| createdAt | String (ISO 8601) | O | 메시지 생성일시 |

**Errors**
- `401` - 인증 실패
- `403` - 해당 세션에 접근할 권한 없음
- `404` - 세션 없음

---

### 4.4 세션 타이틀 수정

**PATCH** `/api/v1/chatbot/sessions/{sessionId}/title`

**인증**: 필요

세션의 타이틀을 수정합니다.

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| sessionId | String | O | 세션 ID |

**Request Body**

```json
{
  "title": "AI 트렌드 대화"
}
```

**UpdateSessionTitleRequest 필드**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| title | String | O | NotBlank, Max(200) | 새 세션 타이틀 (최대 200자) |

**Response** (200 OK) `ApiResponse<SessionResponse>`

SessionResponse 형식

**Errors**
- `400` - 빈 타이틀, 타이틀 길이 초과 (200자)
- `401` - 인증 실패
- `403` - 해당 세션에 접근할 권한 없음
- `404` - 세션 없음

---

### 4.5 세션 삭제

**DELETE** `/api/v1/chatbot/sessions/{sessionId}`

**인증**: 필요

대화 세션을 삭제합니다.

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| sessionId | String | O | 세션 ID |

**Response** (200 OK) `ApiResponse<Void>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success"
}
```

**Errors**
- `401` - 인증 실패
- `403` - 해당 세션에 접근할 권한 없음
- `404` - 세션 없음

---

## 5. 에러 코드

| HTTP 상태 | 에러 코드 | 설명 |
|----------|---------|------|
| 400 | 4000 | 잘못된 요청 (Validation Error) |
| 401 | 4010 | 인증 실패 (Unauthorized) |
| 403 | 4030 | 권한 없음 (Forbidden) |
| 404 | 4040 | 리소스 없음 (Not Found) |
| 500 | 5000 | 서버 에러 (Internal Server Error) |

### Chatbot 관련 에러 메시지

| 상황 | 에러 코드 | 메시지 |
|------|---------|--------|
| 빈 메시지 | 4000 | 메시지는 필수입니다. |
| 메시지 길이 초과 | 4000 | 메시지는 500자를 초과할 수 없습니다. |
| 세션 없음 | 4040 | 세션을 찾을 수 없습니다. |
| 세션 ID 형식 오류 | 4000 | 유효하지 않은 세션 ID 형식입니다. |
| 토큰 한도 초과 | 4000 | 토큰 한도를 초과했습니다. |
| 권한 없음 | 4030 | 해당 세션에 접근할 권한이 없습니다. |
| 빈 타이틀 | 4000 | 타이틀은 필수입니다. |
| 타이틀 길이 초과 | 4000 | 타이틀은 200자를 초과할 수 없습니다. |

---

## 6. 엔드포인트 요약

| Method | Endpoint | 인증 | 설명 |
|--------|----------|------|------|
| POST | `/api/v1/chatbot` | O | 채팅 메시지 전송 |
| GET | `/api/v1/chatbot/sessions` | O | 세션 목록 조회 |
| GET | `/api/v1/chatbot/sessions/{sessionId}` | O | 세션 상세 조회 |
| GET | `/api/v1/chatbot/sessions/{sessionId}/messages` | O | 세션 메시지 목록 조회 |
| PATCH | `/api/v1/chatbot/sessions/{sessionId}/title` | O | 세션 타이틀 수정 |
| DELETE | `/api/v1/chatbot/sessions/{sessionId}` | O | 세션 삭제 |

---

**문서 버전**: 2.0
**최종 업데이트**: 2026-03-13
