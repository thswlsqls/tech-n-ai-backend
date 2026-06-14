# datasource-aurora 모듈

CQRS에서 **쓰기(Command) 쪽**을 맡는 데이터 접근 모듈입니다. Aurora MySQL에 연결하며 도메인 엔티티, 리포지토리, 데이터소스/트랜잭션 설정, 변경 이력 추적을 모아 둡니다.

라이브러리 모듈(`jar`)이며, `api-auth`·`api-bookmark`·`api-chatbot`·`batch-source` 등이 의존성으로 씁니다. 자바 패키지 루트는 `com.tech.n.ai.domain.aurora` 입니다.

## 두 가지 실행 모드 (Spring 프로필)

켜지는 프로필에 따라 다른 설정 묶음이 활성화됩니다. 설정 클래스는 모두 `config/`에 있고 `@Profile`로 구분됩니다.

- **`api-domain`**: API 서비스용. writer/reader 두 HikariCP 풀, JPA 리포지토리, QueryDSL `JPAQueryFactory`, MyBatis `SqlSessionTemplate`.
- **`batch-domain`**: 배치용. 메타 데이터소스와 업무(business) 데이터소스를 분리하고, EntityManagerFactory 두 개(primary/secondary)와 트랜잭션 매니저를 둠.

| 클래스 | 프로필 | 역할 |
|--------|--------|------|
| `ApiDataSourceConfig` | `api-domain` | writer/reader HikariCP 풀 |
| `ApiDomainConfig` | `api-domain` | JPA 리포지토리·엔티티 스캔, `JPAQueryFactory` 빈 |
| `ApiMybatisConfig` | `api-domain` | writer/reader MyBatis 세션 |
| `BatchMetaDataSourceConfig` | `batch-domain` | Spring Batch 메타 데이터소스 |
| `BatchBusinessDataSourceConfig` | `batch-domain` | 업무 데이터 writer/reader 데이터소스 |
| `BatchEntityManagerConfig` | `batch-domain` | `primaryEMF`/`secondaryEMF` |
| `BatchJpaTransactionConfig` | `batch-domain` | JPA 트랜잭션 매니저 |
| `BatchMyBatisConfig` | `batch-domain` | 배치용 MyBatis 세션 |
| `BatchDomainConfig` | `batch-domain` | 위 배치 설정들을 묶는 진입점 |

## 모듈 구조

```
datasource/aurora/src/main/
├── java/com/tech/n/ai/domain/aurora/
│   ├── annotation/Tsid.java            # @IdGeneratorType(TsidGenerator) 커스텀 어노테이션
│   ├── config/                         # 위 표의 설정 클래스들
│   ├── entity/
│   │   ├── BaseEntity.java             # 공통 필드 + soft delete + 감사 타임스탬프
│   │   ├── auth/                       # User, Admin, Provider, RefreshToken, EmailVerification,
│   │   │                               #   AuthenticationState(enum), UserHistory, AdminHistory
│   │   ├── bookmark/                   # Bookmark, BookmarkHistory
│   │   └── conversation/               # ConversationSession, ConversationMessage
│   ├── generator/TsidGenerator.java    # Tsid.fast().toLong() 로 PK 생성
│   ├── repository/
│   │   ├── reader/                     # 읽기용 Spring Data JPA 인터페이스
│   │   └── writer/                     # BaseWriterRepository + 도메인별 Writer + 히스토리 Writer
│   ├── service/history/                # 변경 이력 추적 (HistoryService + Factory들)
│   └── utils/                          # CamelCase → snake_case 등 JPA 네이밍 전략
└── resources/
    ├── application-api-domain.yml      # api-domain 데이터소스/JPA 설정
    ├── application-batch-domain.yml    # batch-domain 데이터소스/JPA 설정
    ├── mapper-config.xml               # MyBatis 전역 설정
    └── db/migration/                   # Flyway 위치 (현재 비어 있음)
```

## TSID 기본키

메인 엔티티는 TSID(Time-Sorted Unique Identifier)를 64비트 `Long` PK로 씁니다. `@Tsid`는 Hibernate 6.5+ 방식인 `@IdGeneratorType(TsidGenerator.class)`로 동작하고, `TsidGenerator`는 `Tsid.fast().toLong()`으로 값을 만듭니다. TSID는 JS `Number.MAX_SAFE_INTEGER`를 넘으므로 API 경계에서는 문자열로 직렬화합니다(전역 Jackson 설정, 루트 `CLAUDE.md` 참고).

## BaseEntity와 Soft Delete

`BaseEntity`(`@MappedSuperclass`)가 제공하는 컬럼:

| 필드 | 컬럼 | 설명 |
|------|------|------|
| `id` | `id` | TSID PK |
| `isDeleted` | `is_deleted` | soft delete 여부 |
| `deletedAt` / `deletedBy` | `deleted_at` / `deleted_by` | 삭제 시각·주체 |
| `createdAt` / `createdBy` | `created_at` / `created_by` | 생성 시각·주체 |
| `updatedAt` / `updatedBy` | `updated_at` / `updated_by` | 수정 시각·주체 |

`@PrePersist`에서 생성/수정 시각을, `@PreUpdate`에서 수정 시각을 채웁니다. `ConversationMessageEntity`는 한 번 쓰면 바꾸지 않는 로그성 데이터라 `BaseEntity`를 상속하지 않고 `message_id`·`created_at`만 가집니다.

## 엔티티

### auth

| 엔티티 | 테이블 | 메모 |
|--------|--------|------|
| `UserEntity` | `users` | 이메일/비밀번호 또는 OAuth 가입. `AuthenticationState` 계산, 정적 팩토리 제공 |
| `AdminEntity` | `admins` | 로그인 실패 횟수·잠금 시각으로 계정 잠금 처리 |
| `ProviderEntity` | `providers` | OAuth 제공자(Google/Naver/Kakao) 설정 |
| `RefreshTokenEntity` | `refresh_tokens` | JWT Refresh Token. 사용자용/관리자용을 `user_id`/`admin_id`로 분리 |
| `EmailVerificationEntity` | `email_verifications` | 이메일 인증·비밀번호 재설정 토큰 |
| `AuthenticationState` | (enum) | `UserEntity` 상태 열거형(EMAIL_NOT_VERIFIED/ACTIVE/DELETED) |
| `UserHistoryEntity` / `AdminHistoryEntity` | `user_history` / `admin_history` | 변경 이력 |

### bookmark

| 엔티티 | 테이블 | 메모 |
|--------|--------|------|
| `BookmarkEntity` | `bookmarks` | EmergingTech 북마크 전용. MongoDB `EmergingTechDocument` 값을 비정규화해 보관. 태그는 `\|` 구분자로 한 컬럼에 저장, soft delete 후 기간 내 복구 지원 |
| `BookmarkHistoryEntity` | `bookmark_history` | 변경 이력 |

### conversation

`@Table(schema = "chatbot")`로 스키마가 고정됩니다.

| 엔티티 | 테이블 | 메모 |
|--------|--------|------|
| `ConversationSessionEntity` | `chatbot.conversation_sessions` | `@AttributeOverride`로 PK 컬럼명을 `session_id`로. `user_id`, `title`, `last_message_at`, `is_active` |
| `ConversationMessageEntity` | `chatbot.conversation_messages` | `message_id`(TSID PK), `role`(USER/ASSISTANT/SYSTEM), `content`, `token_count`, `sequence_number` |

## 리포지토리

**reader (읽기)** — `repository/reader/`의 인터페이스는 모두 Spring Data JPA(`JpaRepository`, 일부 `JpaSpecificationExecutor`)입니다. 예: `UserReaderRepository.findByEmail`, `ConversationSessionReaderRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse`.

**writer (쓰기 + 이력)** — 세 겹입니다.

- `BaseWriterRepository<E>`: 공통 추상 클래스. `save`/`saveAndFlush`/`delete`/`deleteById`를 제공하고 매번 `HistoryService`로 이력을 남김. `delete` 계열은 실제 삭제 대신 `is_deleted = true`로 soft delete.
- 도메인별 Writer(예: `UserWriterRepository`): `@Service`로 `BaseWriterRepository`를 상속해 자신이 쓸 `JpaRepository`·`HistoryService`·`EntityManager`·엔티티 클래스를 연결.
- `*WriterJpaRepository`: 실제 JPA 인터페이스. `history/` 하위는 이력 엔티티를 저장하는 얇은 래퍼.

## 변경 이력 추적

JPA 엔티티 리스너가 아니라 **`BaseWriterRepository`가 명시적으로 호출하는 서비스**로 구현했습니다.

1. `save`가 신규/수정을 구분하고, 수정이면 변경 전 데이터를 먼저 스냅샷으로 떠 둡니다. 1차 캐시의 dirty 값이 섞이지 않도록 `FlushMode.COMMIT` 네이티브 쿼리로 DB에서 직접 읽습니다.
2. 저장 후 `HistoryService.saveHistory(엔티티, INSERT|UPDATE|DELETE, before, after)`를 호출합니다.
3. `HistoryServiceImpl`이 before/after를 JSON으로 직렬화합니다. Jackson 3(`tools.jackson.*`) `JsonMapper` + `Hibernate7Module`을 쓰고, `null` 필드도 항상 포함(`Include.ALWAYS`)해 복원 손실을 막습니다.
4. 엔티티 타입에 맞는 `HistoryEntityFactory`(`supports`)를 골라 이력을 저장합니다. 현재 Factory는 User/Admin/Bookmark 셋이며, 미지원 타입은 `IllegalArgumentException`.

이력 테이블 공통 컬럼: `history_id`(TSID PK), `{entity}_id`(FK), `operation_type`, `before_data`(JSON), `after_data`(JSON), `changed_by`, `changed_at`, `change_reason`.

> `changedBy`는 현재 `getCurrentUserId()`가 `null`을 반환합니다. SecurityContext 연동은 TODO입니다.

## 데이터소스 설정

기본 드라이버는 AWS Advanced JDBC Wrapper(`software.aws.rds.jdbc.mysql.Driver`)이고 HikariCP로 풀을 구성합니다. writer 풀엔 `readWriteSplitting,failover,efm` 플러그인이, reader 풀은 `read-only`로 설정됩니다. `local` 프로필에서는 failover/EFM 플러그인을 끕니다(`wrapperPlugins: ""`, `useConnectionPlugins: false`). build.gradle엔 `mariadb-java-client`도 `runtimeOnly`로 들어 있습니다.

JDBC URL의 데이터베이스 이름은 `${module.aurora.schema}`로 비워 두고 각 서비스가 자기 `application.yml`에서 채웁니다(한 모듈 = 한 스키마). 로컬 인프라는 루트 `docker-compose.yml`로 띄웁니다.

| 서비스 | 로컬 MySQL 포트 | 스키마 |
|--------|----------------|--------|
| `batch-source` | 3307 | batch |
| `api-auth` | 3308 | auth |
| `api-bookmark` | 3309 | bookmark |
| `api-chatbot` | 3310 | chatbot |

`dev`/`beta`/`prod`는 Aurora 클러스터 엔드포인트에 붙고, JDBC 옵션은 `${AURORA_OPTIONS}`로 덮어쓸 수 있습니다.

## Flyway / MyBatis

- **Flyway**: 의존성(`flyway-core`, `flyway-mysql`)과 위치(`db/migration/`)는 있으나 디렉터리는 비어 있습니다. 스키마는 외부에서 관리되며 `hibernate.ddl-auto: none`으로 JPA가 DDL을 만들지 않습니다.
- **MyBatis**: 양쪽 프로필 모두 writer/reader `SqlSessionTemplate` 빈과 전역 설정(`mapper-config.xml`)을 구성하지만, 이 모듈엔 매퍼 XML이 없습니다. 복잡한 읽기 쿼리가 필요한 서비스가 자기 모듈에서 매퍼를 추가해 이 인프라를 씁니다.

## 의존성

`jpa.gradle`(HikariCP, QueryDSL, AWS MySQL JDBC 등)과 `docs.gradle`을 적용합니다. 직접 선언하는 주요 의존성: `tsid-creator`(TSID), `spring-boot-starter-data-jpa`, `jackson-datatype-hibernate7`(이력 JSON 직렬화 시 Hibernate 프록시 처리), `spring-boot-starter-data-mongodb`(EmergingTech 식별자 연동), `flyway-core`/`flyway-mysql`, `mybatis-spring-boot-starter`, `mariadb-java-client`(runtimeOnly).

## CQRS에서의 위치

Command 쪽입니다. 쓰기가 끝나면 Kafka 이벤트가 발행되고 Query 쪽(`datasource-mongodb`)이 받아 동기화합니다(목표 지연 1초 이내, Redis 멱등 처리). 이벤트 발행은 각 API 서비스와 `common-kafka`가 담당합니다.

## 참고

- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/) · [Amazon Aurora MySQL](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.AuroraMySQL.html)
- [AWS Advanced JDBC Wrapper](https://github.com/aws/aws-advanced-jdbc-wrapper) · [TSID Creator](https://github.com/f4b6a3/tsid-creator) · [Flyway](https://documentation.red-gate.com/fd)
