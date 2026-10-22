# Emerging Tech API 설계서

**작성일**: 2026-02-06
**대상 모듈**: api-emerging-tech
**버전**: v1

---

## 1. 개요

| 항목 | 내용 |
|------|------|
| 모듈 | api-emerging-tech |
| Base URL | `/api/v1/emerging-tech` |
| 포트 | 8082 (via Gateway: 8081) |
| 설명 | AI 기술 업데이트 정보 관리 API |

### 인증

| API 유형 | 인증 방식 |
|---------|----------|
| 공개 API | 불필요 |
| 내부 API | `X-Internal-Api-Key` 헤더 |

---

## 2. Enum 정의

### TechProvider (기술 서비스 제공자)

| 값 | 설명 |
|----|------|
| OPENAI | OpenAI (GPT, DALL-E 등) |
| ANTHROPIC | Anthropic (Claude) |
| GOOGLE | Google (Gemini) |
| META | Meta (LLaMA) |
| XAI | xAI (Grok) |

### EmergingTechType (업데이트 유형)

| 값 | 설명 |
|----|------|
| MODEL_RELEASE | AI 모델 출시 |
| API_UPDATE | API 변경사항 |
| SDK_RELEASE | SDK 새 버전 |
| PRODUCT_LAUNCH | 신규 제품/서비스 출시 |
| PLATFORM_UPDATE | 플랫폼 업데이트 |
| BLOG_POST | 기술 블로그 포스트 |

### PostStatus (게시물 상태)

| 값 | 설명 |
|----|------|
| DRAFT | 초안 (자동 수집됨) |
| PENDING | 승인 대기 |
| PUBLISHED | 게시됨 |
| REJECTED | 거부됨 |

### SourceType (데이터 수집 소스 유형)

| 값 | 설명 |
|----|------|
| GITHUB_RELEASE | GitHub Release |
| RSS | RSS 피드 |
| WEB_SCRAPING | 웹 스크래핑 |

---

## 3. 공통 응답 형식

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

---

## 4. 공개 API

### 4.1 목록 조회

**GET** `/api/v1/emerging-tech`

**인증**: 불필요

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 검증 | 설명 |
|----------|------|------|--------|------|------|
| page | Integer | X | 1 | min: 1 | 페이지 번호 |
| size | Integer | X | 20 | min: 1, max: 100 | 페이지 크기 |
| provider | String | X | - | TechProvider enum | 제공자 필터 |
| updateType | String | X | - | EmergingTechType enum | 업데이트 유형 필터 |
| status | String | X | - | PostStatus enum | 상태 필터 |
| sourceType | String | X | - | SourceType enum | 소스 유형 필터 |
| sort | String | X | - | - | 정렬 (예: "publishedAt,desc") |
| startDate | String | X | - | YYYY-MM-DD | 조회 시작일 |
| endDate | String | X | - | YYYY-MM-DD | 조회 종료일 |

**Response** (200 OK) `ApiResponse<EmergingTechPageResponse>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "pageSize": 20,
    "pageNumber": 1,
    "totalCount": 100,
    "items": [
      {
        "id": "507f1f77bcf86cd799439011",
        "provider": "OPENAI",
        "updateType": "MODEL_RELEASE",
        "title": "GPT-5 출시",
        "summary": "GPT-5 모델이 출시되었습니다.",
        "url": "https://openai.com/blog/gpt-5",
        "publishedAt": "2025-01-15T10:00:00",
        "sourceType": "RSS",
        "status": "PUBLISHED",
        "externalId": "gpt5-release-001",
        "metadata": {
          "version": "5.0",
          "tags": ["AI", "LLM", "GPT"],
          "author": "OpenAI",
          "githubRepo": null,
          "additionalInfo": {}
        },
        "createdAt": "2025-01-15T10:30:00",
        "updatedAt": "2025-01-15T10:30:00"
      }
    ]
  }
}
```

**EmergingTechPageResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| pageSize | Integer | O | 페이지 크기 |
| pageNumber | Integer | O | 현재 페이지 번호 |
| totalCount | Integer | O | 전체 항목 수 |
| items | EmergingTechDetailResponse[] | O | 항목 목록 |

---

### 4.2 상세 조회

**GET** `/api/v1/emerging-tech/{id}`

**인증**: 불필요

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| id | String | O | MongoDB ObjectId (24자 hex string) |

**Response** (200 OK) `ApiResponse<EmergingTechDetailResponse>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "id": "507f1f77bcf86cd799439011",
    "provider": "OPENAI",
    "updateType": "MODEL_RELEASE",
    "title": "GPT-5 출시",
    "summary": "GPT-5 모델이 출시되었습니다.",
    "url": "https://openai.com/blog/gpt-5",
    "publishedAt": "2025-01-15T10:00:00",
    "sourceType": "RSS",
    "status": "PUBLISHED",
    "externalId": "gpt5-release-001",
    "metadata": {
      "version": "5.0",
      "tags": ["AI", "LLM", "GPT"],
      "author": "OpenAI",
      "githubRepo": "https://github.com/openai/gpt-5",
      "additionalInfo": {
        "customKey": "customValue"
      }
    },
    "createdAt": "2025-01-15T10:30:00",
    "updatedAt": "2025-01-15T10:30:00"
  }
}
```

**EmergingTechDetailResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| id | String | O | MongoDB ObjectId |
| provider | String | O | TechProvider enum 값 |
| updateType | String | O | EmergingTechType enum 값 |
| title | String | O | 제목 |
| summary | String | X | 요약 |
| url | String | O | 원본 URL |
| publishedAt | String (ISO 8601) | X | 발행일시 |
| sourceType | String | O | SourceType enum 값 |
| status | String | O | PostStatus enum 값 |
| externalId | String | X | 외부 시스템 ID |
| metadata | EmergingTechMetadataResponse | X | 메타데이터 |
| createdAt | String (ISO 8601) | O | 생성일시 |
| updatedAt | String (ISO 8601) | O | 수정일시 |

**EmergingTechMetadataResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| version | String | X | 버전 정보 |
| tags | String[] | X | 태그 목록 |
| author | String | X | 작성자 |
| githubRepo | String | X | GitHub 저장소 URL |
| additionalInfo | Object | X | 추가 정보 (key-value) |

**Errors**
- `404` - 리소스 없음

---

### 4.3 검색

**GET** `/api/v1/emerging-tech/search`

**인증**: 불필요

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 검증 | 설명 |
|----------|------|------|--------|------|------|
| q | String | O | - | NotBlank | 검색어 |
| page | Integer | X | 1 | min: 1 | 페이지 번호 |
| size | Integer | X | 20 | min: 1, max: 100 | 페이지 크기 |

**Response** (200 OK) `ApiResponse<EmergingTechPageResponse>`

목록 조회와 동일한 응답 형식

**Errors**
- `400` - 검색어 누락

---

## 5. 내부 API

> 모든 내부 API는 `X-Internal-Api-Key` 헤더가 필수입니다.

### 5.1 단건 생성

**POST** `/api/v1/emerging-tech/internal`

**인증**: X-Internal-Api-Key 헤더

**Request Headers**

| 헤더 | 타입 | 필수 | 설명 |
|------|------|------|------|
| X-Internal-Api-Key | String | O | 내부 API 인증 키 |

**Request Body**

```json
{
  "provider": "OPENAI",
  "updateType": "MODEL_RELEASE",
  "title": "GPT-5 출시",
  "summary": "GPT-5 모델이 출시되었습니다.",
  "url": "https://openai.com/blog/gpt-5",
  "publishedAt": "2025-01-15T10:00:00",
  "sourceType": "RSS",
  "status": "DRAFT",
  "externalId": "gpt5-release-001",
  "metadata": {
    "version": "5.0",
    "tags": ["AI", "LLM", "GPT"],
    "author": "OpenAI",
    "githubRepo": "https://github.com/openai/gpt-5",
    "additionalInfo": {
      "customKey": "customValue"
    }
  }
}
```

**EmergingTechCreateRequest 필드**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| provider | String | O | TechProvider enum | 제공자 |
| updateType | String | O | EmergingTechType enum | 업데이트 유형 |
| title | String | O | NotBlank | 제목 |
| summary | String | X | - | 요약 |
| url | String | O | NotBlank | 원본 URL |
| publishedAt | String | X | ISO 8601 | 발행일시 |
| sourceType | String | O | SourceType enum | 소스 유형 |
| status | String | O | PostStatus enum | 상태 |
| externalId | String | X | - | 외부 시스템 ID |
| metadata | EmergingTechMetadataRequest | X | - | 메타데이터 |

**Response** (200 OK) `ApiResponse<EmergingTechDetailResponse>`

**Errors**
- `400` - 유효성 검증 실패
- `401` - 인증 실패

---

### 5.2 다건 생성

**POST** `/api/v1/emerging-tech/internal/batch`

**인증**: X-Internal-Api-Key 헤더

**Request Body**

```json
{
  "items": [
    {
      "provider": "OPENAI",
      "updateType": "MODEL_RELEASE",
      "title": "GPT-5 출시",
      "summary": "GPT-5 모델이 출시되었습니다.",
      "url": "https://openai.com/blog/gpt-5",
      "publishedAt": "2025-01-15T10:00:00",
      "sourceType": "RSS",
      "status": "DRAFT",
      "externalId": "gpt5-release-001",
      "metadata": null
    }
  ]
}
```

**EmergingTechBatchRequest 필드**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| items | EmergingTechCreateRequest[] | O | NotEmpty | 생성 요청 목록 |

**Response** (200 OK) `ApiResponse<EmergingTechBatchResponse>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "totalCount": 10,
    "successCount": 8,
    "newCount": 6,
    "duplicateCount": 2,
    "failureCount": 0,
    "failureMessages": []
  }
}
```

**EmergingTechBatchResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| totalCount | Integer | O | 전체 요청 수 |
| successCount | Integer | O | 성공 수 (신규 + 중복) |
| newCount | Integer | O | 신규 등록 수 |
| duplicateCount | Integer | O | 중복 스킵 수 |
| failureCount | Integer | O | 실패 수 |
| failureMessages | String[] | O | 실패 메시지 목록 |

**Errors**
- `400` - 유효성 검증 실패
- `401` - 인증 실패

---

### 5.3 승인

**POST** `/api/v1/emerging-tech/{id}/approve`

**인증**: X-Internal-Api-Key 헤더

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| id | String | O | MongoDB ObjectId |

**Response** (200 OK) `ApiResponse<EmergingTechDetailResponse>`

상태가 `PUBLISHED`로 변경된 응답 반환

**Errors**
- `401` - 인증 실패
- `404` - 리소스 없음

---

### 5.4 거부

**POST** `/api/v1/emerging-tech/{id}/reject`

**인증**: X-Internal-Api-Key 헤더

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| id | String | O | MongoDB ObjectId |

**Response** (200 OK) `ApiResponse<EmergingTechDetailResponse>`

상태가 `REJECTED`로 변경된 응답 반환

**Errors**
- `401` - 인증 실패
- `404` - 리소스 없음

---

## 6. 에러 코드

| HTTP 상태 | 에러 코드 | 설명 |
|----------|---------|------|
| 400 | 4000 | 잘못된 요청 (Validation Error) |
| 401 | 4010 | 인증 실패 (Unauthorized) |
| 403 | 4030 | 권한 없음 (Forbidden) |
| 404 | 4040 | 리소스 없음 (Not Found) |
| 500 | 5000 | 서버 에러 (Internal Server Error) |

---

## 7. 엔드포인트 요약

| Method | Endpoint | 인증 | 설명 |
|--------|----------|------|------|
| GET | `/api/v1/emerging-tech` | X | 목록 조회 |
| GET | `/api/v1/emerging-tech/{id}` | X | 상세 조회 |
| GET | `/api/v1/emerging-tech/search` | X | 검색 |
| POST | `/api/v1/emerging-tech/internal` | O (Internal) | 단건 생성 |
| POST | `/api/v1/emerging-tech/internal/batch` | O (Internal) | 다건 생성 |
| POST | `/api/v1/emerging-tech/{id}/approve` | O (Internal) | 승인 |
| POST | `/api/v1/emerging-tech/{id}/reject` | O (Internal) | 거부 |

---

**문서 버전**: 1.0
**최종 업데이트**: 2026-02-06
