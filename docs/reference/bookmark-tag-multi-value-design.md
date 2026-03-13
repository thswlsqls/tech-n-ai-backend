# 북마크 태그 다중값 저장 설계서

**작성 일시**: 2026-02-06
**대상 모듈**: `domain-aurora`, `api-bookmark`
**목적**: 북마크의 `tag` 컬럼에 구분자(`|`)를 사용하여 N개의 태그를 저장하고, API 응답 시 `List<String>`으로 반환

---

## 목차

1. [개요](#1-개요)
2. [현재 구조 분석](#2-현재-구조-분석)
3. [변경 설계](#3-변경-설계)
4. [상세 변경 사항](#4-상세-변경-사항)
5. [구현 가이드](#5-구현-가이드)
6. [검증 기준](#6-검증-기준)

---

## 1. 개요

### 1.1 변경 배경

현재 `BookmarkEntity`의 `tag` 필드는 단일 문자열(`String`)로 저장되어 하나의 태그만 저장 가능하다. 사용자가 북마크에 여러 개의 태그를 지정할 수 있도록 구분자 기반 다중값 저장 방식으로 개선한다.

### 1.2 설계 요구사항

| 항목 | 요구사항 |
|------|----------|
| DB 컬럼 | 변경 없음 (`VARCHAR(100)` 유지) |
| 구분자 | `|` (파이프) 사용 |
| Request DTO | `List<String> tags` |
| Response DTO | `List<String> tags` |
| Entity | 내부 저장은 `String tag` 유지, 변환 메서드 추가 |

### 1.3 변경 범위

| 모듈 | 변경 내용 |
|------|----------|
| `domain/aurora` | `BookmarkEntity`에 변환 메서드 추가, `updateContent` 시그니처 변경 |
| `api/bookmark` | Request/Response DTO 필드 변경, Service 변환 로직 수정 |

### 1.4 설계 원칙

- DB 스키마 변경 없음
- 별도 태그 테이블 생성 금지 (정규화 불필요)
- JPA AttributeConverter 사용 금지 (단순 변환 메서드로 충분)
- 오버엔지니어링 금지 (태그 검증, 정렬, 중복 제거 등 추가 로직 불필요)

---

## 2. 현재 구조 분석

### 2.1 BookmarkEntity (현재)

```java
@Entity
@Table(name = "bookmarks")
public class BookmarkEntity extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "emerging_tech_id", nullable = false, length = 24)
    private String emergingTechId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "published_at", precision = 6)
    private LocalDateTime publishedAt;

    @Column(name = "tag", length = 100)
    private String tag;  // 현재: 단일 문자열

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    public void updateContent(String tag, String memo) {
        if (tag != null) {
            this.tag = tag;
        }
        if (memo != null) {
            this.memo = memo;
        }
    }
}
```

### 2.2 BookmarkCreateRequest (현재)

```java
public record BookmarkCreateRequest(
    @NotBlank(message = "EmergingTech ID는 필수입니다.")
    String emergingTechId,
    String tag,   // 단일 문자열
    String memo
) {}
```

### 2.3 BookmarkUpdateRequest (현재)

```java
public record BookmarkUpdateRequest(
    String tag,   // 단일 문자열
    String memo
) {}
```

### 2.4 BookmarkDetailResponse (현재)

```java
public record BookmarkDetailResponse(
    String bookmarkTsid,
    String userId,
    String emergingTechId,
    String title,
    String url,
    String provider,
    String summary,
    LocalDateTime publishedAt,
    String tag,   // 단일 문자열
    String memo,
    LocalDateTime createdAt,
    String createdBy,
    LocalDateTime updatedAt,
    String updatedBy
) {
    public static BookmarkDetailResponse from(BookmarkEntity entity) {
        // ... entity.getTag() 사용
    }
}
```

### 2.5 BookmarkCommandServiceImpl (현재)

```java
private BookmarkEntity createBookmark(Long userId, BookmarkCreateRequest request,
                                      EmergingTechDocument emergingTech) {
    BookmarkEntity bookmark = new BookmarkEntity();
    // ...
    bookmark.setTag(request.tag());  // 단일 문자열 저장
    bookmark.setMemo(request.memo());
    return bookmark;
}

public BookmarkEntity updateBookmark(...) {
    // ...
    bookmark.updateContent(request.tag(), request.memo());  // 단일 문자열 전달
    // ...
}
```

---

## 3. 변경 설계

### 3.1 데이터 저장 형식

```
입력: ["AI", "Machine Learning", "Python"]
저장: "AI|Machine Learning|Python"
조회: ["AI", "Machine Learning", "Python"]
```

### 3.2 BookmarkEntity (변경 후)

```java
@Entity
@Table(name = "bookmarks")
public class BookmarkEntity extends BaseEntity {
    // 기존 필드 유지...

    @Column(name = "tag", length = 100)
    private String tag;  // DB 저장은 구분자 포함 문자열

    // 구분자 상수
    private static final String TAG_DELIMITER = "|";

    // List<String> → String 변환 (저장용)
    public void setTagsAsList(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            this.tag = null;
        } else {
            this.tag = String.join(TAG_DELIMITER, tags);
        }
    }

    // String → List<String> 변환 (조회용)
    public List<String> getTagsAsList() {
        if (this.tag == null || this.tag.isBlank()) {
            return List.of();
        }
        return Arrays.asList(this.tag.split("\\" + TAG_DELIMITER));
    }

    // updateContent 시그니처 변경
    public void updateContent(List<String> tags, String memo) {
        if (tags != null) {
            setTagsAsList(tags);
        }
        if (memo != null) {
            this.memo = memo;
        }
    }
}
```

### 3.3 Request DTO (변경 후)

**BookmarkCreateRequest**:
```java
public record BookmarkCreateRequest(
    @NotBlank(message = "EmergingTech ID는 필수입니다.")
    String emergingTechId,
    List<String> tags,  // 변경: String → List<String>
    String memo
) {}
```

**BookmarkUpdateRequest**:
```java
public record BookmarkUpdateRequest(
    List<String> tags,  // 변경: String → List<String>
    String memo
) {}
```

### 3.4 Response DTO (변경 후)

```java
public record BookmarkDetailResponse(
    String bookmarkTsid,
    String userId,
    String emergingTechId,
    String title,
    String url,
    String provider,
    String summary,
    LocalDateTime publishedAt,
    List<String> tags,  // 변경: String → List<String>
    String memo,
    LocalDateTime createdAt,
    String createdBy,
    LocalDateTime updatedAt,
    String updatedBy
) {
    public static BookmarkDetailResponse from(BookmarkEntity entity) {
        if (entity == null) {
            return null;
        }
        return new BookmarkDetailResponse(
            entity.getId() != null ? entity.getId().toString() : null,
            entity.getUserId() != null ? entity.getUserId().toString() : null,
            entity.getEmergingTechId(),
            entity.getTitle(),
            entity.getUrl(),
            entity.getProvider(),
            entity.getSummary(),
            entity.getPublishedAt(),
            entity.getTagsAsList(),  // 변경: getTag() → getTagsAsList()
            entity.getMemo(),
            entity.getCreatedAt(),
            entity.getCreatedBy() != null ? entity.getCreatedBy().toString() : null,
            entity.getUpdatedAt(),
            entity.getUpdatedBy() != null ? entity.getUpdatedBy().toString() : null
        );
    }
}
```

### 3.5 API 요청/응답 예시

**북마크 생성 요청**:
```json
POST /api/v1/bookmark
{
    "emergingTechId": "65a1b2c3d4e5f6789012345",
    "tags": ["AI", "GPT-4", "OpenAI"],
    "memo": "GPT-4 관련 기술 업데이트"
}
```

**북마크 조회 응답**:
```json
{
    "code": 0,
    "message": "Success",
    "data": {
        "bookmarkTsid": "123456789",
        "userId": "987654321",
        "emergingTechId": "65a1b2c3d4e5f6789012345",
        "title": "GPT-4 Turbo Released",
        "url": "https://openai.com/blog/gpt-4-turbo",
        "provider": "OPENAI",
        "summary": "OpenAI releases GPT-4 Turbo...",
        "publishedAt": "2026-02-01T10:00:00",
        "tags": ["AI", "GPT-4", "OpenAI"],
        "memo": "GPT-4 관련 기술 업데이트",
        "createdAt": "2026-02-06T09:30:00",
        "createdBy": "987654321",
        "updatedAt": "2026-02-06T09:30:00",
        "updatedBy": "987654321"
    }
}
```

---

## 4. 상세 변경 사항

### 4.1 domain/aurora 변경

#### 4.1.1 BookmarkEntity.java

**파일**: `domain/aurora/src/main/java/com/ebson/shrimp/tm/demo/domain/mariadb/entity/bookmark/BookmarkEntity.java`

**추가할 import**:
```java
import java.util.Arrays;
import java.util.List;
```

**추가할 코드**:
```java
// 구분자 상수
private static final String TAG_DELIMITER = "|";

// List<String> → String 변환 (저장용)
public void setTagsAsList(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
        this.tag = null;
    } else {
        this.tag = String.join(TAG_DELIMITER, tags);
    }
}

// String → List<String> 변환 (조회용)
public List<String> getTagsAsList() {
    if (this.tag == null || this.tag.isBlank()) {
        return List.of();
    }
    return Arrays.asList(this.tag.split("\\" + TAG_DELIMITER));
}
```

**수정할 코드**:
```java
// 기존
public void updateContent(String tag, String memo) {
    if (tag != null) {
        this.tag = tag;
    }
    if (memo != null) {
        this.memo = memo;
    }
}

// 변경 후
public void updateContent(List<String> tags, String memo) {
    if (tags != null) {
        setTagsAsList(tags);
    }
    if (memo != null) {
        this.memo = memo;
    }
}
```

---

### 4.2 api/bookmark 변경

#### 4.2.1 BookmarkCreateRequest.java

**파일**: `api/bookmark/src/main/java/com/ebson/shrimp/tm/demo/api/bookmark/dto/request/BookmarkCreateRequest.java`

```java
package com.ebson.shrimp.tm.demo.api.bookmark.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 북마크 생성 요청 DTO
 */
public record BookmarkCreateRequest(
    @NotBlank(message = "EmergingTech ID는 필수입니다.")
    String emergingTechId,

    List<String> tags,
    String memo
) {
}
```

#### 4.2.2 BookmarkUpdateRequest.java

**파일**: `api/bookmark/src/main/java/com/ebson/shrimp/tm/demo/api/bookmark/dto/request/BookmarkUpdateRequest.java`

```java
package com.ebson.shrimp.tm.demo.api.bookmark.dto.request;

import java.util.List;

/**
 * 북마크 수정 요청 DTO
 */
public record BookmarkUpdateRequest(
    List<String> tags,
    String memo
) {
}
```

#### 4.2.3 BookmarkDetailResponse.java

**파일**: `api/bookmark/src/main/java/com/ebson/shrimp/tm/demo/api/bookmark/dto/response/BookmarkDetailResponse.java`

```java
package com.ebson.shrimp.tm.demo.api.bookmark.dto.response;

import com.ebson.shrimp.tm.demo.domain.aurora.entity.bookmark.BookmarkEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 북마크 상세 조회 응답 DTO
 */
public record BookmarkDetailResponse(
    String bookmarkTsid,
    String userId,
    String emergingTechId,
    String title,
    String url,
    String provider,
    String summary,
    LocalDateTime publishedAt,
    List<String> tags,
    String memo,
    LocalDateTime createdAt,
    String createdBy,
    LocalDateTime updatedAt,
    String updatedBy
) {
    public static BookmarkDetailResponse from(BookmarkEntity entity) {
        if (entity == null) {
            return null;
        }

        return new BookmarkDetailResponse(
            entity.getId() != null ? entity.getId().toString() : null,
            entity.getUserId() != null ? entity.getUserId().toString() : null,
            entity.getEmergingTechId(),
            entity.getTitle(),
            entity.getUrl(),
            entity.getProvider(),
            entity.getSummary(),
            entity.getPublishedAt(),
            entity.getTagsAsList(),
            entity.getMemo(),
            entity.getCreatedAt(),
            entity.getCreatedBy() != null ? entity.getCreatedBy().toString() : null,
            entity.getUpdatedAt(),
            entity.getUpdatedBy() != null ? entity.getUpdatedBy().toString() : null
        );
    }
}
```

#### 4.2.4 BookmarkCommandServiceImpl.java

**파일**: `api/bookmark/src/main/java/com/ebson/shrimp/tm/demo/api/bookmark/service/BookmarkCommandServiceImpl.java`

**수정할 코드 (createBookmark 메서드 내 createBookmark 헬퍼)**:
```java
// 기존
bookmark.setTag(request.tag());

// 변경 후
bookmark.setTagsAsList(request.tags());
```

**수정할 코드 (updateBookmark 메서드)**:
```java
// 기존
bookmark.updateContent(request.tag(), request.memo());

// 변경 후
bookmark.updateContent(request.tags(), request.memo());
```

---

## 5. 구현 가이드

### 5.1 구현 순서

```
Step 1: BookmarkEntity에 import 추가 (Arrays, List)
Step 2: BookmarkEntity에 TAG_DELIMITER 상수 추가
Step 3: BookmarkEntity에 setTagsAsList() 메서드 추가
Step 4: BookmarkEntity에 getTagsAsList() 메서드 추가
Step 5: BookmarkEntity.updateContent() 시그니처 변경 (String → List<String>)
Step 6: BookmarkCreateRequest의 tag → tags (List<String>)
Step 7: BookmarkUpdateRequest의 tag → tags (List<String>)
Step 8: BookmarkDetailResponse의 tag → tags (List<String>)
Step 9: BookmarkDetailResponse.from()에서 getTagsAsList() 호출
Step 10: BookmarkCommandServiceImpl의 createBookmark에서 setTagsAsList() 사용
Step 11: BookmarkCommandServiceImpl의 updateBookmark에서 request.tags() 사용
Step 12: 빌드 검증
```

### 5.2 영향도 분석

| 파일 | 변경 수준 | 설명 |
|------|----------|------|
| `BookmarkEntity.java` | **Minor** | 변환 메서드 추가, updateContent 시그니처 변경 |
| `BookmarkCreateRequest.java` | **Minor** | `tag` → `tags` 필드 타입 변경 |
| `BookmarkUpdateRequest.java` | **Minor** | `tag` → `tags` 필드 타입 변경 |
| `BookmarkDetailResponse.java` | **Minor** | `tag` → `tags` 필드 타입 변경, from() 메서드 수정 |
| `BookmarkCommandServiceImpl.java` | **Minor** | setTag → setTagsAsList, request.tag() → request.tags() |
| `BookmarkFacade.java` | **None** | 변경 불필요 |
| `BookmarkController.java` | **None** | 변경 불필요 |
| `BookmarkQueryServiceImpl.java` | **None** | 변경 불필요 |

### 5.3 기존 데이터 호환성

기존에 단일 태그로 저장된 데이터(`"AI"`)는 `getTagsAsList()` 호출 시 `["AI"]`로 정상 반환된다. 구분자(`|`)가 없는 문자열은 split 결과가 원본 문자열 하나만 포함된 배열이 되기 때문이다.

---

## 6. 검증 기준

### 6.1 컴파일 검증

```bash
./gradlew :api-bookmark:build
```

### 6.2 기능 검증 체크리스트

- [ ] `BookmarkEntity`에 `TAG_DELIMITER` 상수가 추가됨
- [ ] `BookmarkEntity`에 `setTagsAsList(List<String>)` 메서드가 추가됨
- [ ] `BookmarkEntity`에 `getTagsAsList()` 메서드가 추가됨
- [ ] `BookmarkEntity.updateContent()` 시그니처가 `(List<String>, String)`으로 변경됨
- [ ] `BookmarkCreateRequest`의 필드가 `tags: List<String>`으로 변경됨
- [ ] `BookmarkUpdateRequest`의 필드가 `tags: List<String>`으로 변경됨
- [ ] `BookmarkDetailResponse`의 필드가 `tags: List<String>`으로 변경됨
- [ ] `BookmarkDetailResponse.from()`에서 `entity.getTagsAsList()` 호출함
- [ ] `BookmarkCommandServiceImpl`에서 `setTagsAsList()` 사용함
- [ ] `./gradlew :api-bookmark:build` 성공

### 6.3 코드 검색 검증

```bash
# 기존 단일 tag 필드 사용이 남아있지 않은지 확인 (DTO)
grep -rn "String tag" api/bookmark/src/main/java/com/ebson/shrimp/tm/demo/api/bookmark/dto/

# request.tag() 호출이 남아있지 않은지 확인
grep -rn "request.tag()" api/bookmark/src/
```

### 6.4 API 테스트 케이스

| 테스트 케이스 | 입력 | 예상 DB 저장값 | 예상 응답 |
|--------------|------|---------------|----------|
| 태그 없음 | `tags: null` | `NULL` | `tags: []` |
| 빈 배열 | `tags: []` | `NULL` | `tags: []` |
| 단일 태그 | `tags: ["AI"]` | `"AI"` | `tags: ["AI"]` |
| 다중 태그 | `tags: ["AI", "ML", "Python"]` | `"AI|ML|Python"` | `tags: ["AI", "ML", "Python"]` |
| 기존 데이터 조회 | (DB에 `"AI"` 저장) | - | `tags: ["AI"]` |

---

## 참고 자료

- `domain/aurora/src/main/java/.../entity/bookmark/BookmarkEntity.java` - 현재 Entity 구조
- `api/bookmark/src/main/java/.../dto/` - 현재 DTO 구조
- `api/bookmark/src/main/java/.../service/BookmarkCommandServiceImpl.java` - 현재 Service 구조
