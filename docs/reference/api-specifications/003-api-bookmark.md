# Bookmark API 설계서

**작성일**: 2026-02-06
**대상 모듈**: api-bookmark
**버전**: v1

---

## 1. 개요

| 항목 | 내용 |
|------|------|
| 모듈 | api-bookmark |
| Base URL | `/api/v1/bookmark` |
| 포트 | 8085 (via Gateway: 8081) |
| 설명 | 사용자 북마크 관리 API |

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

### PageData<T>

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| pageSize | Integer | O | 페이지 크기 |
| pageNumber | Integer | O | 현재 페이지 번호 (1부터 시작) |
| totalPageNumber | Integer | O | 전체 페이지 수 |
| totalSize | Long | O | 전체 데이터 수 |
| list | T[] | O | 데이터 리스트 |

---

## 3. 북마크 API

### 3.1 북마크 저장

**POST** `/api/v1/bookmark`

**인증**: 필요

새로운 북마크를 저장합니다.

**Request Body**

```json
{
  "emergingTechId": "et_123456789",
  "tags": ["AI", "Machine Learning"],
  "memo": "관심 있는 기술 아티클"
}
```

**BookmarkCreateRequest 필드**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| emergingTechId | String | O | NotBlank | EmergingTech 콘텐츠 ID |
| tags | String[] | X | - | 사용자 지정 태그 목록 |
| memo | String | X | - | 사용자 메모 |

**Response** (200 OK) `ApiResponse<BookmarkDetailResponse>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "bookmarkTsid": "123456789012345678",
    "userId": "100000001",
    "emergingTechId": "et_123456789",
    "title": "GPT-5 출시 예정",
    "url": "https://example.com/article/123",
    "provider": "HACKERNEWS",
    "summary": "OpenAI가 새로운 GPT-5 모델 출시를 예고했습니다.",
    "publishedAt": "2025-01-15T10:00:00",
    "tags": ["AI", "Machine Learning"],
    "memo": "관심 있는 기술 아티클",
    "createdAt": "2025-01-20T14:30:00",
    "createdBy": "100000001",
    "updatedAt": "2025-01-20T14:30:00",
    "updatedBy": "100000001"
  }
}
```

**BookmarkDetailResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| bookmarkTsid | String | O | 북마크 고유 ID (TSID) |
| userId | String | O | 사용자 ID |
| emergingTechId | String | O | EmergingTech 콘텐츠 ID |
| title | String | X | 콘텐츠 제목 |
| url | String | X | 콘텐츠 URL |
| provider | String | X | 콘텐츠 제공자 |
| summary | String | X | 콘텐츠 요약 |
| publishedAt | String (ISO 8601) | X | 콘텐츠 게시일시 |
| tags | String[] | X | 사용자 지정 태그 목록 |
| memo | String | X | 사용자 메모 |
| createdAt | String (ISO 8601) | O | 북마크 생성일시 |
| createdBy | String | X | 생성자 ID |
| updatedAt | String (ISO 8601) | X | 북마크 수정일시 |
| updatedBy | String | X | 수정자 ID |

**Errors**
- `400` - 유효성 검증 실패
- `401` - 인증 실패
- `404` - EmergingTech 콘텐츠 없음
- `409` - 이미 북마크한 콘텐츠

---

### 3.2 북마크 목록 조회

**GET** `/api/v1/bookmark`

**인증**: 필요

사용자의 북마크 목록을 조회합니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 검증 | 설명 |
|----------|------|------|--------|------|------|
| page | Integer | X | 1 | Min(1) | 페이지 번호 |
| size | Integer | X | 10 | Min(1), Max(100) | 페이지 크기 |
| sort | String | X | "createdAt,desc" | - | 정렬 기준 (필드명,방향) |
| provider | String | X | - | - | 콘텐츠 제공자 필터 |

**Response** (200 OK) `ApiResponse<BookmarkListResponse>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "data": {
      "pageSize": 10,
      "pageNumber": 1,
      "totalPageNumber": 5,
      "totalSize": 45,
      "list": [
        {
          "bookmarkTsid": "123456789012345678",
          "userId": "100000001",
          "emergingTechId": "et_123456789",
          "title": "GPT-5 출시 예정",
          "url": "https://example.com/article/123",
          "provider": "HACKERNEWS",
          "summary": "OpenAI가 새로운 GPT-5 모델 출시를 예고했습니다.",
          "publishedAt": "2025-01-15T10:00:00",
          "tags": ["AI", "Machine Learning"],
          "memo": "관심 있는 기술 아티클",
          "createdAt": "2025-01-20T14:30:00",
          "createdBy": "100000001",
          "updatedAt": "2025-01-20T14:30:00",
          "updatedBy": "100000001"
        }
      ]
    }
  }
}
```

**BookmarkListResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| data | PageData<BookmarkDetailResponse> | O | 페이징된 북마크 목록 |

**Errors**
- `401` - 인증 실패

---

### 3.3 북마크 상세 조회

**GET** `/api/v1/bookmark/{id}`

**인증**: 필요

특정 북마크의 상세 정보를 조회합니다.

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| id | String | O | 북마크 ID (TSID) |

**Response** (200 OK) `ApiResponse<BookmarkDetailResponse>`

BookmarkDetailResponse 형식

**Errors**
- `401` - 인증 실패
- `403` - 권한 없음
- `404` - 북마크 없음

---

### 3.4 북마크 수정

**PUT** `/api/v1/bookmark/{id}`

**인증**: 필요

북마크의 태그와 메모를 수정합니다.

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| id | String | O | 북마크 ID (TSID) |

**Request Body**

```json
{
  "tags": ["AI", "Deep Learning", "NLP"],
  "memo": "수정된 메모 내용"
}
```

**BookmarkUpdateRequest 필드**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| tags | String[] | X | - | 사용자 지정 태그 목록 |
| memo | String | X | - | 사용자 메모 |

**Response** (200 OK) `ApiResponse<BookmarkDetailResponse>`

BookmarkDetailResponse 형식

**Errors**
- `401` - 인증 실패
- `403` - 권한 없음
- `404` - 북마크 없음

---

### 3.5 북마크 삭제

**DELETE** `/api/v1/bookmark/{id}`

**인증**: 필요

북마크를 삭제합니다 (소프트 삭제).

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| id | String | O | 북마크 ID (TSID) |

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
- `403` - 권한 없음
- `404` - 북마크 없음

---

### 3.6 삭제된 북마크 목록 조회

**GET** `/api/v1/bookmark/deleted`

**인증**: 필요

삭제된 북마크 목록을 조회합니다 (휴지통).

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 검증 | 설명 |
|----------|------|------|--------|------|------|
| page | Integer | X | 1 | Min(1) | 페이지 번호 |
| size | Integer | X | 10 | Min(1), Max(100) | 페이지 크기 |
| days | Integer | X | 30 | - | 조회할 기간 (일 단위) |

**Response** (200 OK) `ApiResponse<BookmarkListResponse>`

BookmarkListResponse 형식

**Errors**
- `401` - 인증 실패

---

### 3.7 북마크 복구

**POST** `/api/v1/bookmark/{id}/restore`

**인증**: 필요

삭제된 북마크를 복구합니다.

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| id | String | O | 북마크 ID (TSID) |

**Response** (200 OK) `ApiResponse<BookmarkDetailResponse>`

BookmarkDetailResponse 형식

**Errors**
- `401` - 인증 실패
- `403` - 권한 없음
- `404` - 북마크 없음

---

### 3.8 북마크 검색

**GET** `/api/v1/bookmark/search`

**인증**: 필요

북마크를 검색합니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 검증 | 설명 |
|----------|------|------|--------|------|------|
| q | String | O | - | NotBlank | 검색어 |
| page | Integer | X | 1 | Min(1) | 페이지 번호 |
| size | Integer | X | 10 | Min(1), Max(100) | 페이지 크기 |
| searchField | String | X | "all" | - | 검색 필드 (all, title, memo, tags) |

**Response** (200 OK) `ApiResponse<BookmarkSearchResponse>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "data": {
      "pageSize": 10,
      "pageNumber": 1,
      "totalPageNumber": 2,
      "totalSize": 15,
      "list": [...]
    }
  }
}
```

**BookmarkSearchResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| data | PageData<BookmarkDetailResponse> | O | 페이징된 검색 결과 |

**Errors**
- `400` - 검색어 누락
- `401` - 인증 실패

---

## 4. 북마크 히스토리 API

### 4.1 변경 이력 조회

**GET** `/api/v1/bookmark/history/{entityId}`

**인증**: 필요

특정 북마크의 변경 이력을 조회합니다.

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| entityId | String | O | 북마크 ID (TSID) |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 검증 | 설명 |
|----------|------|------|--------|------|------|
| page | Integer | X | 1 | Min(1) | 페이지 번호 |
| size | Integer | X | 10 | Min(1), Max(100) | 페이지 크기 |
| operationType | String | X | - | - | 작업 유형 필터 (CREATE, UPDATE, DELETE) |
| startDate | String | X | - | ISO 8601 | 조회 시작일 |
| endDate | String | X | - | ISO 8601 | 조회 종료일 |

**Response** (200 OK) `ApiResponse<BookmarkHistoryListResponse>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "data": {
      "pageSize": 10,
      "pageNumber": 1,
      "totalPageNumber": 1,
      "totalSize": 3,
      "list": [
        {
          "historyId": "987654321098765432",
          "entityId": "123456789012345678",
          "operationType": "UPDATE",
          "beforeData": {
            "tags": ["AI"],
            "memo": "이전 메모"
          },
          "afterData": {
            "tags": ["AI", "Machine Learning"],
            "memo": "수정된 메모"
          },
          "changedBy": "100000001",
          "changedAt": "2025-01-20T15:00:00",
          "changeReason": null
        }
      ]
    }
  }
}
```

**BookmarkHistoryListResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| data | PageData<BookmarkHistoryDetailResponse> | O | 페이징된 히스토리 목록 |

**BookmarkHistoryDetailResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| historyId | String | O | 히스토리 ID |
| entityId | String | O | 북마크 ID |
| operationType | String | O | 작업 유형 (CREATE, UPDATE, DELETE) |
| beforeData | Object | X | 변경 전 데이터 (JSON) |
| afterData | Object | X | 변경 후 데이터 (JSON) |
| changedBy | String | O | 변경자 ID |
| changedAt | String (ISO 8601) | O | 변경일시 |
| changeReason | String | X | 변경 사유 |

**Errors**
- `401` - 인증 실패
- `403` - 권한 없음
- `404` - 북마크 없음

---

### 4.2 특정 시점 데이터 조회

**GET** `/api/v1/bookmark/history/{entityId}/at`

**인증**: 필요

특정 시점의 북마크 상태를 조회합니다.

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| entityId | String | O | 북마크 ID (TSID) |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| timestamp | String | O | 조회 시점 (ISO 8601 형식) |

**Response** (200 OK) `ApiResponse<BookmarkHistoryDetailResponse>`

BookmarkHistoryDetailResponse 형식

**Errors**
- `401` - 인증 실패
- `403` - 권한 없음
- `404` - 히스토리 없음

---

### 4.3 특정 버전으로 복구

**POST** `/api/v1/bookmark/history/{entityId}/restore`

**인증**: 필요

특정 히스토리 버전으로 북마크를 복구합니다.

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| entityId | String | O | 북마크 ID (TSID) |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| historyId | String | O | 복구할 히스토리 ID |

**Response** (200 OK) `ApiResponse<BookmarkDetailResponse>`

BookmarkDetailResponse 형식

**Errors**
- `401` - 인증 실패
- `403` - 권한 없음
- `404` - 히스토리 없음

---

## 5. 에러 코드

| HTTP 상태 | 에러 코드 | 설명 |
|----------|---------|------|
| 400 | 4000 | 잘못된 요청 (Validation Error) |
| 401 | 4010 | 인증 실패 (Unauthorized) |
| 403 | 4030 | 권한 없음 (Forbidden) |
| 404 | 4040 | 리소스 없음 (Not Found) |
| 409 | 4090 | 충돌 (Conflict - 북마크 중복 등) |
| 500 | 5000 | 서버 에러 (Internal Server Error) |

### Bookmark 관련 에러 메시지

| 상황 | 에러 코드 | 메시지 |
|------|---------|--------|
| 북마크 없음 | 4040 | 북마크를 찾을 수 없습니다. |
| 북마크 중복 | 4090 | 이미 북마크한 콘텐츠입니다. |
| 유효하지 않은 ID | 4000 | 유효하지 않은 북마크 ID 형식입니다. |
| EmergingTech 없음 | 4040 | EmergingTech 콘텐츠를 찾을 수 없습니다. |
| 히스토리 없음 | 4040 | 히스토리를 찾을 수 없습니다. |

---

## 6. 엔드포인트 요약

| Method | Endpoint | 인증 | 설명 |
|--------|----------|------|------|
| POST | `/api/v1/bookmark` | O | 북마크 저장 |
| GET | `/api/v1/bookmark` | O | 북마크 목록 조회 |
| GET | `/api/v1/bookmark/{id}` | O | 북마크 상세 조회 |
| PUT | `/api/v1/bookmark/{id}` | O | 북마크 수정 |
| DELETE | `/api/v1/bookmark/{id}` | O | 북마크 삭제 |
| GET | `/api/v1/bookmark/deleted` | O | 삭제된 북마크 목록 조회 |
| POST | `/api/v1/bookmark/{id}/restore` | O | 북마크 복구 |
| GET | `/api/v1/bookmark/search` | O | 북마크 검색 |
| GET | `/api/v1/bookmark/history/{entityId}` | O | 변경 이력 조회 |
| GET | `/api/v1/bookmark/history/{entityId}/at` | O | 특정 시점 데이터 조회 |
| POST | `/api/v1/bookmark/history/{entityId}/restore` | O | 특정 버전으로 복구 |
| POST | `/api/v1/bookmark/{id}/views` | O | 조회 이벤트 기록 |
| GET | `/api/v1/bookmark/reports/daily` | O | 일별 조회 리포트 |

---

**문서 버전**: 1.0
**최종 업데이트**: 2026-02-06
