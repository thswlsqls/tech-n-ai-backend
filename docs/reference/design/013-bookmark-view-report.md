# 북마크 조회 이벤트·일별 리포트 설계서

**작성 일시**: 2026-08-20
**대상 모듈**: `datasource-aurora`, `api-bookmark`
**목적**: 북마크 상세 조회를 이벤트로 남기고, 사용자별 일별 조회 리포트 API를 제공한다

---

## 목차

1. [개요](#1-개요)
2. [현재 구조 분석](#2-현재-구조-분석)
3. [데이터 모델](#3-데이터-모델)
4. [API 스펙](#4-api-스펙)
5. [처리 흐름](#5-처리-흐름)
6. [검증 기준](#6-검증-기준)

---

## 1. 개요

### 1.1 배경

지금 `api-bookmark`는 북마크를 저장·수정·삭제한 기록만 남긴다. 사용자가 어떤 북마크를 얼마나 자주 열어 보는지는
어디에도 남지 않아서, 화면에 "최근 많이 본 북마크"를 띄우거나 개인 활동 리포트를 만들 수 없다.

조회 행위를 이벤트로 남기고, 그 이벤트를 날짜 단위로 집계해 리포트로 읽을 수 있게 한다.

### 1.2 설계 요구사항

| 항목 | 요구사항 |
|------|----------|
| 이벤트 적재 | 북마크 상세를 연 시점마다 한 건씩 남긴다 |
| 집계 | 사용자·날짜·제공자 조합으로 조회 수를 누적한다 |
| 리포트 조회 | 날짜 구간을 받아 일별 집계를 돌려준다 |
| 조회 구간 상한 | **최대 90일**. 넘으면 400을 준다 |
| 저장소 | Aurora MySQL 단독. `api-bookmark`는 Kafka를 쓰지 않는다 |
| 날짜 기준 | **KST(Asia/Seoul) 기준으로 날짜를 자른다** |

### 1.3 변경 범위

| 모듈 | 내용 |
|------|------|
| `datasource-aurora` | `BookmarkViewEventEntity`·`BookmarkDailyStatEntity`와 reader/writer 리포지토리 추가 |
| `api-bookmark` | 조회 이벤트 기록 API, 일별 리포트 API, 서비스·Facade·컨트롤러 추가 |

기존 북마크 CRUD 경로는 건드리지 않는다.

---

## 2. 현재 구조 분석

`api-bookmark`는 Aurora MySQL 하나만 쓴다. 쓰기는 `BookmarkWriterRepository`(`BaseWriterRepository` 상속)가,
읽기는 `BookmarkReaderRepository`(Spring Data JPA)가 맡는다. 변경 이력은 `BookmarkHistoryEntity`에 남는다.

조회 이벤트는 사용자가 변경한 데이터가 아니라 행위 기록이므로 **이력 테이블 대상이 아니다.**
`BaseWriterRepository`를 쓰지 않고 일반 JPA 리포지토리로 저장한다.

---

## 3. 데이터 모델

### 3.1 `bookmark_view_events` — 이벤트 원본

집계가 틀어졌을 때 다시 계산할 수 있도록 원본을 그대로 남긴다.

```sql
CREATE TABLE bookmark_view_events (
    id            BIGINT       NOT NULL,
    bookmark_id   BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    provider      VARCHAR(50)  NULL,
    viewed_at     DATETIME(6)  NOT NULL,
    source        VARCHAR(20)  NULL,
    is_deleted    TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_at    DATETIME(6)  NULL,
    deleted_by    BIGINT       NULL,
    created_at    DATETIME(6)  NOT NULL,
    created_by    BIGINT       NULL,
    updated_at    DATETIME(6)  NULL,
    updated_by    BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_bookmark_view_events_user_viewed (user_id, viewed_at)
);
```

`source`는 조회 경로(`web`, `app`)다. 요청으로 받아 **이벤트에 함께 저장한다.**

### 3.2 `bookmark_daily_stats` — 일별 집계

리포트가 읽는 쪽이다.

```sql
CREATE TABLE bookmark_daily_stats (
    id            BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    stat_date     DATE         NOT NULL,
    provider      VARCHAR(50)  NULL,
    view_count    BIGINT       NOT NULL DEFAULT 0,
    is_deleted    TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_at    DATETIME(6)  NULL,
    deleted_by    BIGINT       NULL,
    created_at    DATETIME(6)  NOT NULL,
    created_by    BIGINT       NULL,
    updated_at    DATETIME(6)  NULL,
    updated_by    BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bookmark_daily_stats_user_date_provider (user_id, stat_date, provider),
    KEY idx_bookmark_daily_stats_user_date (user_id, stat_date)
);
```

`(user_id, stat_date, provider)`에 UNIQUE 제약을 둔다. 같은 조합의 행이 둘 생기면 리포트 합계가 두 번 세어진다.

> 스키마는 저장소 밖에서 관리한다(`ddl-auto: none`). 위 DDL은 반영해야 할 내용을 적어 둔 것이다.

---

## 4. API 스펙

### 4.1 조회 이벤트 기록

```
POST /api/v1/bookmark/{id}/views
```

요청 본문

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `source` | String(20) | N | 조회 경로 (`web`, `app`) |

응답

| 필드 | 타입 | 설명 |
|------|------|------|
| `bookmarkId` | String | 조회한 북마크 ID |
| `viewedAt` | DateTime | 기록 시각 |
| `todayViewCount` | Number | 기록 후 그 날짜의 누적 조회 수 |

| 상황 | 상태 코드 |
|------|-----------|
| 정상 | 200 |
| 본인 북마크가 아님 | 403 |
| 없는 북마크 · 삭제된 북마크 | 404 |

### 4.2 일별 리포트

```
GET /api/v1/bookmark/reports/daily?from=2026-08-01&to=2026-08-30&provider=github
```

요청 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `from` | String | Y | 시작일 `yyyy-MM-dd` |
| `to` | String | Y | 종료일 `yyyy-MM-dd` |
| `provider` | String | N | 제공자 필터 |

응답

| 필드 | 타입 | 설명 |
|------|------|------|
| `from` | String | 시작일 |
| `to` | String | 종료일 |
| `totalViews` | Number | 구간 전체 조회 수 |
| `days[].date` | String | 날짜 |
| `days[].provider` | String | 제공자 |
| `days[].viewCount` | Number | 그 날짜의 조회 수 |

| 상황 | 상태 코드 |
|------|-----------|
| 정상 | 200 |
| 날짜 형식이 `yyyy-MM-dd`가 아님 | 400 |
| `from`이 `to`보다 늦음 | 400 |
| 구간이 90일을 넘음 | 400 |

---

## 5. 처리 흐름

### 5.1 이벤트 기록

```
POST /{id}/views
  → 북마크를 찾는다. 없거나 삭제됐으면 404, 남의 것이면 403
  → bookmark_view_events 에 한 건 저장 (source 포함)
  → bookmark_daily_stats 의 그 날짜 행을 1 올린다
  → 갱신 결과를 응답에 담는다
```

**이벤트 저장과 집계 갱신은 같은 트랜잭션에서 처리한다.** 이벤트만 남고 집계가 안 오르면
리포트 값과 원본이 어긋나고, 그 차이는 재집계 전까지 드러나지 않는다.

집계 갱신은 `UPDATE ... SET view_count = view_count + 1` 한 문장으로 처리한다.
읽어서 더한 뒤 다시 쓰면 인스턴스 두 대가 같은 순간에 처리할 때 한쪽 증가분이 사라진다.
`api-bookmark`는 ECS에서 여러 태스크로 뜬다.

### 5.2 리포트 조회

```
GET /reports/daily
  → from·to 를 파싱한다. 형식이 틀리면 400
  → 구간이 90일을 넘으면 400
  → 구간 전체 집계를 한 번에 읽는다
  → 날짜별로 정리해 돌려준다
```

**구간 전체를 한 번에 읽는다.** 날짜마다 따로 조회하면 90일 구간에서 쿼리가 90번 나간다.

### 5.3 날짜 기준

프로젝트 전체가 KST로 돈다(루트 `build.gradle`이 `-Duser.timezone=Asia/Seoul`을 강제한다).
집계 날짜도 KST 기준으로 자른다. UTC로 자르면 KST 00시~09시 사이의 조회가 전날 칸에 들어간다.

---

## 6. 검증 기준

### 6.1 컴파일·테스트

```bash
./gradlew :datasource-aurora:build
./gradlew :api-bookmark:test
```

### 6.2 기능 체크리스트

- [ ] 조회 이벤트가 `bookmark_view_events`에 남는다 (`source` 포함)
- [ ] 같은 날짜에 두 번 조회하면 `view_count`가 2가 된다
- [ ] 남의 북마크 ID로 요청하면 403
- [ ] 삭제된 북마크 ID로 요청하면 404
- [ ] KST 00시 30분의 조회가 그 날짜 칸에 들어간다
- [ ] 90일 구간 리포트 조회에서 집계 쿼리가 1회만 나간다
- [ ] 91일 구간을 요청하면 400
- [ ] `from`이 `to`보다 늦으면 400

### 6.3 부하 관점

`bookmark_daily_stats`는 사용자당 하루 최대 (제공자 수)행이다. 제공자가 5종이면 사용자 1만 명 기준
하루 5만 행, 1년 약 1,800만 행이다. `(user_id, stat_date)` 인덱스로 구간 조회를 커버한다.

`bookmark_view_events`는 조회마다 한 행이라 훨씬 빨리 는다. 집계가 끝난 구간의 원본은
보관 기간 정책을 따로 정해 지운다 — 이번 변경 범위 밖이다.

---

## 참고 자료

- `api/bookmark/README.md` — 모듈 개요
- `docs/reference/design/003-bookmark-emerging-tech-redesign.md` — 북마크 도메인 재설계
- `docs/reference/design/007-bookmark-tag-multi-value.md` — 태그 다중값 저장
