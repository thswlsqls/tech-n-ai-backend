# Bookmark API 모듈 (`api-bookmark`)

## 개요

`api-bookmark`는 로그인한 사용자가 관심 있는 EmergingTech(AI·기술 업데이트) 항목을 개인 북마크로 저장하고 관리하는 REST API 서버입니다. 북마크에 태그와 메모를 붙여 수정할 수 있고, 삭제는 데이터를 실제로 지우지 않는 soft delete 방식이라 일정 기간 안에는 다시 복구할 수 있습니다. 모든 변경은 이력으로 남아서 과거 시점의 상태를 조회하거나 특정 버전으로 되돌릴 수 있습니다.

로컬 실행 포트는 **8085**이고, 외부 요청은 `api-gateway`를 거쳐 들어옵니다.

## 주요 기능

### 북마크 관리
- **저장**: EmergingTech 문서 ID를 받아 북마크로 저장합니다. 저장 시점에 원본 문서의 제목·URL·제공자·요약·게시일을 북마크 테이블에 함께 복사해 둡니다(비정규화).
- **조회**: 사용자별 북마크 목록과 단건 상세를 조회합니다. 목록은 제공자(`provider`)로 거를 수 있습니다.
- **수정**: 태그(`tags`)와 메모(`memo`)를 수정합니다. 원본에서 복사해 온 제목·URL 등은 사용자가 바꾸지 않습니다.
- **삭제**: soft delete로 처리합니다. 데이터는 남고 `is_deleted` 플래그만 켭니다.
- **복구**: 삭제한 북마크를 되살립니다. 삭제 후 **30일 이내**만 복구할 수 있습니다.

### 검색
- 검색어 `q`로 북마크를 찾습니다. 검색 대상 필드는 `searchField`로 고릅니다: `title`, `tag`, `memo`, 또는 전체(`all`, 기본값).
- 검색·정렬·필터는 모두 Aurora MySQL에서 JPA Specification으로 처리합니다.

### 변경 이력
- **이력 목록 조회**: 특정 북마크의 변경 이력을 조회합니다. 작업 종류(`operationType`)와 날짜 범위로 거를 수 있습니다.
- **특정 시점 조회**: 주어진 시점 직전의 가장 최근 이력을 조회합니다.
- **버전 복구**: 특정 이력 버전(historyId)에 저장된 데이터로 북마크를 되돌립니다. 단, 중복 방지용 UNIQUE 제약에 묶인 `emerging_tech_id`는 복원 대상에서 제외합니다.

### 조회 리포트
- **조회 이벤트 기록**: 북마크 상세를 열 때마다 이벤트를 한 건 남기고, 그 날짜의 집계를 함께 올립니다.
- **일별 리포트 조회**: 날짜 구간(`from`~`to`)을 받아 날짜·제공자별 조회 수를 돌려줍니다. 구간은 최대 90일입니다.

## 아키텍처

### 데이터 저장 방식

이 모듈은 **읽기와 쓰기를 모두 Aurora MySQL(`bookmark` 스키마)에서 처리**합니다. 명령(Command)과 조회(Query) 서비스를 코드 레벨에서 나누어 책임을 분리했지만, 물리적인 저장소는 Aurora 하나입니다.

MongoDB는 북마크 자체를 저장하는 데 쓰지 않습니다. **북마크를 새로 만들 때 원본 EmergingTech 문서를 한 번 조회**해서 제목·URL·제공자·요약·게시일을 가져와 Aurora의 북마크 행에 복사하는 용도로만 사용합니다. 이렇게 복사해 두면 목록·검색·상세 조회를 MongoDB까지 가지 않고 Aurora 한 곳에서 끝낼 수 있습니다.

> 참고: 프로젝트 전반은 Aurora(쓰기)와 MongoDB(읽기)를 Kafka로 잇는 CQRS 구조이지만, **이 북마크 모듈에는 Kafka 이벤트 발행·수신이 없습니다.** `build.gradle`에도 `common-kafka` 의존성이 없습니다.

### 이력 추적

변경 이력은 Kafka가 아니라 쓰기 리포지토리 계층에서 직접 남깁니다. `BookmarkWriterRepository`가 상속하는 `BaseWriterRepository`가 저장·수정·삭제 시점마다 `HistoryService`를 호출해, 변경 전(before)·후(after) 상태를 JSON으로 직렬화한 행을 `bookmark_history` 테이블에 기록합니다. 작업 종류는 `INSERT`, `UPDATE`, `DELETE`로 구분됩니다.

### 계층 구조

```
Controller → Facade → Service → Repository → Aurora MySQL
                                    └ (생성 시) EmergingTech 조회 → MongoDB
```

- **Controller**: HTTP 요청/응답 처리, JWT에서 사용자 ID 추출
  - `BookmarkController`: 북마크 CRUD·검색·이력
  - `BookmarkReportController`: 조회 이벤트 기록, 일별 리포트
- **Facade**: 서비스 호출을 조합하고 엔티티를 응답 DTO로 변환. ID 문자열 파싱, 페이지 변환을 담당
  - `BookmarkFacade`: 북마크 CRUD·검색·이력
  - `BookmarkReportFacade`: 경로 ID 파싱과 리포트 요청 구간 검증
- **Service**:
  - `BookmarkCommandService`: 저장·수정·삭제·복구 (쓰기)
  - `BookmarkQueryService`: 목록·상세·검색 (읽기)
  - `BookmarkHistoryService`: 이력 조회 및 버전 복구
  - `BookmarkViewEventService`: 조회 이벤트 적재와 일별 집계 갱신
  - `BookmarkReportService`: 구간 집계 조회
- **Repository** (`datasource-aurora` 모듈):
  - `BookmarkReaderRepository`: JPA `JpaSpecificationExecutor` 기반 조회
  - `BookmarkHistoryReaderRepository`: 이력 조회
  - `BookmarkWriterRepository`: 저장·삭제 + 이력 기록
  - `BookmarkDailyStatReaderRepository`: 일별 집계 조회
  - `BookmarkViewEventWriterJpaRepository` · `BookmarkDailyStatWriterJpaRepository`: 조회 이벤트·집계 저장

## 데이터 모델

### `bookmarks` 테이블 (`BookmarkEntity`)

| 컬럼 | 설명 |
| --- | --- |
| `id` | 기본키 (TSID, 64비트 Long) |
| `user_id` | 소유 사용자 ID |
| `emerging_tech_id` | 원본 EmergingTech 문서 ID (MongoDB ObjectId 문자열) |
| `title`, `url`, `provider`, `summary`, `published_at` | 원본 문서에서 복사한 비정규화 필드 |
| `tag` | 태그를 `\|`(파이프)로 이어 붙인 한 컬럼. 코드에서 `List<String>`과 상호 변환 |
| `memo` | 사용자 메모 |
| `is_deleted`, `deleted_at`, `deleted_by` 등 | `BaseEntity`가 제공하는 soft delete·감사 필드 |

- 중복 방지: `(user_id, emerging_tech_id)` 조합으로 같은 항목을 두 번 북마크할 수 없습니다(삭제되지 않은 건 기준).
- 태그는 별도 테이블 없이 단일 문자열 컬럼에 구분자로 저장합니다.

### `bookmark_history` 테이블 (`BookmarkHistoryEntity`)

| 컬럼 | 설명 |
| --- | --- |
| `history_id` | 기본키 (TSID) |
| `bookmark_id` | 대상 북마크 ID |
| `operation_type` | `INSERT` / `UPDATE` / `DELETE` |
| `before_data`, `after_data` | 변경 전·후 상태 (JSON) |
| `changed_by`, `changed_at`, `change_reason` | 변경자·변경 시각·사유 |

### `bookmark_view_events` 테이블 (`BookmarkViewEventEntity`)

| 컬럼 | 설명 |
| --- | --- |
| `id` | 기본키 (TSID) |
| `bookmark_id` | 조회한 북마크 ID |
| `user_id` | 조회한 사용자 ID |
| `provider` | 조회 시점의 제공자 (북마크에서 복사) |
| `viewed_at` | 조회 시각 |
| `source` | 조회 경로 (`web`, `app`) |

집계가 틀어졌을 때 다시 계산하기 위한 원본입니다. 리포트 API는 이 테이블을 직접 읽지 않습니다.

### `bookmark_daily_stats` 테이블 (`BookmarkDailyStatEntity`)

| 컬럼 | 설명 |
| --- | --- |
| `id` | 기본키 (TSID) |
| `user_id` | 사용자 ID |
| `stat_date` | 집계 날짜 (KST 기준) |
| `provider` | 제공자 |
| `view_count` | 그 날짜·제공자의 조회 수 |

- `(user_id, stat_date, provider)`에 UNIQUE 제약을 둡니다. 같은 조합의 행이 둘 생기면 합계가 두 번 세어집니다.

## API 엔드포인트

- **Base URL**: `/api/v1/bookmark`
- **인증**: 모든 엔드포인트에 JWT Access Token 필요 (`@AuthenticationPrincipal UserPrincipal`)
- **응답 형식**: 공통 `ApiResponse<T>` 래퍼 사용

| 메서드 | 경로 | 설명 | 주요 파라미터 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/bookmark` | 북마크 저장 | body: `emergingTechId`(필수), `tags`, `memo` |
| `GET` | `/api/v1/bookmark` | 북마크 목록 조회 | `page`(기본 1), `size`(기본 10, 최대 100), `sort`(기본 `createdAt,desc`), `provider` |
| `GET` | `/api/v1/bookmark/{id}` | 북마크 상세 조회 | path: `id` |
| `PUT` | `/api/v1/bookmark/{id}` | 북마크 수정 | body: `tags`, `memo` |
| `DELETE` | `/api/v1/bookmark/{id}` | 북마크 삭제 (soft delete) | path: `id` |
| `GET` | `/api/v1/bookmark/deleted` | 삭제된 북마크 목록 조회 | `page`, `size`, `days`(기본 30) |
| `POST` | `/api/v1/bookmark/{id}/restore` | 북마크 복구 (30일 이내) | path: `id` |
| `GET` | `/api/v1/bookmark/search` | 북마크 검색 | `q`(필수), `page`, `size`, `searchField`(`title`/`tag`/`memo`/`all`, 기본 `all`) |
| `GET` | `/api/v1/bookmark/history/{entityId}` | 변경 이력 목록 조회 | `page`, `size`, `operationType`, `startDate`, `endDate` |
| `GET` | `/api/v1/bookmark/history/{entityId}/at` | 특정 시점 데이터 조회 | `timestamp`(필수, `yyyy-MM-dd` 또는 `yyyy-MM-ddTHH:mm:ss`) |
| `POST` | `/api/v1/bookmark/history/{entityId}/restore` | 특정 버전으로 복구 | `historyId`(필수) |
| `POST` | `/api/v1/bookmark/{id}/views` | 조회 이벤트 기록 | body: `source`(선택) |
| `GET` | `/api/v1/bookmark/reports/daily` | 일별 조회 리포트 | `from`(필수), `to`(필수), `provider` |

ID는 API 경계에서 모두 문자열로 주고받습니다. TSID(64비트 Long)가 자바스크립트의 안전 정수 범위를 넘기 때문입니다.

## 인증과 권한

- 모든 API는 JWT 인증이 필요합니다.
- 사용자는 **본인 소유 북마크만** 조회·수정·삭제·복구할 수 있습니다. 서비스 계층에서 `isOwnedBy(userId)`로 소유자를 확인하고, 아니면 `UnauthorizedException`을 던집니다.
- 이력 조회·복구도 같은 소유자 검증을 적용합니다. **관리자/일반 사용자 구분은 이 모듈에 없습니다.**

## 예외 처리

`BookmarkExceptionHandler`(`@RestControllerAdvice`)가 모듈 전용 예외를 HTTP 상태로 변환합니다.

| 예외 | HTTP 상태 | 발생 상황 |
| --- | --- | --- |
| `BookmarkNotFoundException` | 404 | 북마크를 찾을 수 없거나 이미 삭제된 경우 |
| `BookmarkItemNotFoundException` | 404 | 원본 EmergingTech 문서를 찾을 수 없는 경우 |
| `BookmarkDuplicateException` | 409 | 같은 항목을 중복 북마크하려는 경우 |
| `BookmarkValidationException` | 400 | ID 형식 오류, 날짜 형식 오류, 복구 기간 초과 등 |

## 기술 스택과 의존성

### 의존 모듈 (`build.gradle`)
- `common-core` — 공통 DTO(`ApiResponse`, `PageData`), 에러 코드
- `common-exception` — 공통 예외(`UnauthorizedException` 등)
- `common-security` — JWT 인증, `UserPrincipal`
- `datasource-aurora` — 북마크/이력 엔티티와 리포지토리 (Aurora MySQL)
- `datasource-mongodb` — EmergingTech 문서 조회 (`EmergingTechRepository`)

### 주요 기술
- Spring Boot 4, Java 21
- Spring Data JPA + QueryDSL (Aurora 읽기/쓰기), JPA Specification (동적 검색)
- Spring Data MongoDB (EmergingTech 문서 조회 전용)
- Spring Security (JWT)
- Jackson 3 (`tools.jackson.*`) — 이력 JSON 직렬화/역직렬화

## 설정

### `application.yml`
```yaml
server:
  port: 8085

spring:
  application:
    name: bookmark-api
  profiles:
    include:
      - common-core
      - kafka
      - api-domain
      - mongodb-domain
      - bookmark-api
```

### `application-bookmark-api.yml`
```yaml
module:
  aurora:
    schema: bookmark
  mysql:
    port: 3309        # 북마크 전용 MySQL 컨테이너 포트 (로컬)

bookmark:
  restore:
    max-days: 30      # BookmarkConfig 프로퍼티
```

> 복구 가능 기간 관련 메모: `bookmark.restore.max-days` 프로퍼티(`BookmarkConfig`)가 정의돼 있지만, 실제 복구 검증은 현재 `BookmarkCommandServiceImpl`의 상수 `RESTORE_DAYS_LIMIT = 30`을 사용합니다. 두 값 모두 30일로 같습니다.

## 실행

```bash
# 로컬 인프라 (모듈별 MySQL 등)
docker compose up -d

# 북마크 서비스 실행 (기본 local 프로필)
./gradlew :api-bookmark:bootRun

# 테스트
./gradlew :api-bookmark:test
```

## 참고

- Spring Data JPA — https://spring.io/projects/spring-data-jpa
- Spring Data MongoDB — https://spring.io/projects/spring-data-mongodb
- 프로젝트 전체 설계 문서: 루트 `tech-n-ai-backend/README.md`
</content>
</invoke>
