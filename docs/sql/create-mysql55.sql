-- =====================================================================
-- Aurora MySQL Schema & Table CREATE DDL (MySQL 5.5 호환)
-- =====================================================================
-- 작성 일시: 2026-02-22
-- 호환 대상: MySQL 5.5.x
-- 소스: datasource/aurora 모듈 JPA Entity 기반
--
-- MySQL 5.5 호환을 위한 변경 사항:
--   - TIMESTAMP(6) / DATETIME(6) → DATETIME (소수초 정밀도 미지원, 5.6.4+)
--   - DEFAULT CURRENT_TIMESTAMP → 제거 (DATETIME에서 미지원, 5.6.5+)
--     * JPA @PrePersist / @PreUpdate 가 애플리케이션 레벨에서 처리
--   - JSON → TEXT (JSON 타입 미지원, 5.7.8+)
--
-- 실행 순서:
-- 1. 스키마(데이터베이스) 생성
-- 2. auth 스키마 테이블 생성 (의존성 순서 고려)
-- 3. bookmark 스키마 테이블 생성
-- 4. chatbot 스키마 테이블 생성
-- =====================================================================


-- =====================================================================
-- 1. 스키마(데이터베이스) 생성
-- =====================================================================

CREATE DATABASE IF NOT EXISTS auth
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS bookmark
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS chatbot
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;


-- =====================================================================
-- 2. auth 스키마 테이블 생성
-- =====================================================================

USE auth;

-- ---------------------------------------------------------------------
-- 2.1. providers (OAuth 제공자)
-- 의존성: 없음
-- Entity: com.tech.n.ai.domain.aurora.entity.auth.ProviderEntity
-- ---------------------------------------------------------------------

CREATE TABLE providers (
    id              BIGINT UNSIGNED     NOT NULL    PRIMARY KEY     COMMENT 'TSID',
    name            VARCHAR(50)         NOT NULL                    COMMENT '제공자 이름 (google, github 등)',
    display_name    VARCHAR(100)        NOT NULL                    COMMENT '표시 이름',
    client_id       VARCHAR(255)        NULL                        COMMENT 'OAuth Client ID',
    client_secret   VARCHAR(500)        NULL                        COMMENT 'OAuth Client Secret',
    is_enabled      BOOLEAN             NOT NULL    DEFAULT TRUE    COMMENT '활성화 여부',
    is_deleted      BOOLEAN             NOT NULL    DEFAULT FALSE   COMMENT '삭제 여부',
    deleted_at      DATETIME            NULL                        COMMENT '삭제 일시',
    deleted_by      BIGINT UNSIGNED     NULL                        COMMENT '삭제한 사용자 ID',
    created_at      DATETIME            NOT NULL                    COMMENT '생성 일시',
    created_by      BIGINT UNSIGNED     NULL                        COMMENT '생성한 사용자 ID',
    updated_at      DATETIME            NULL                        COMMENT '수정 일시',
    updated_by      BIGINT UNSIGNED     NULL                        COMMENT '수정한 사용자 ID',
    UNIQUE  KEY uk_provider_name        (name),
    INDEX       idx_provider_is_enabled (is_enabled),
    INDEX       idx_provider_is_deleted (is_deleted)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='OAuth 제공자 테이블';

-- ---------------------------------------------------------------------
-- 2.2. users (사용자)
-- 의존성: providers (provider_id FK)
-- Entity: com.tech.n.ai.domain.aurora.entity.auth.UserEntity
-- ---------------------------------------------------------------------

CREATE TABLE users (
    id                  BIGINT UNSIGNED     NOT NULL    PRIMARY KEY     COMMENT 'TSID',
    email               VARCHAR(100)        NOT NULL                    COMMENT '이메일',
    username            VARCHAR(50)         NOT NULL                    COMMENT '사용자명',
    password            VARCHAR(255)        NULL                        COMMENT '비밀번호 해시 (OAuth 사용자는 NULL)',
    provider_id         BIGINT UNSIGNED     NULL                        COMMENT 'OAuth Provider ID',
    provider_user_id    VARCHAR(255)        NULL                        COMMENT 'OAuth 제공자의 사용자 ID',
    is_email_verified   BOOLEAN             NOT NULL    DEFAULT FALSE   COMMENT '이메일 인증 완료 여부',
    last_login_at       DATETIME            NULL                        COMMENT '마지막 로그인 일시',
    is_deleted          BOOLEAN             NOT NULL    DEFAULT FALSE   COMMENT '삭제 여부',
    deleted_at          DATETIME            NULL                        COMMENT '삭제 일시',
    deleted_by          BIGINT UNSIGNED     NULL                        COMMENT '삭제한 사용자 ID',
    created_at          DATETIME            NOT NULL                    COMMENT '생성 일시',
    created_by          BIGINT UNSIGNED     NULL                        COMMENT '생성한 사용자 ID',
    updated_at          DATETIME            NULL                        COMMENT '수정 일시',
    updated_by          BIGINT UNSIGNED     NULL                        COMMENT '수정한 사용자 ID',
    UNIQUE  KEY uk_user_email           (email),
    UNIQUE  KEY uk_user_username        (username),
    INDEX       idx_user_provider       (provider_id, provider_user_id),
    INDEX       idx_user_is_deleted     (is_deleted),
    CONSTRAINT fk_user_provider FOREIGN KEY (provider_id) REFERENCES providers (id) ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='사용자 테이블';

-- ---------------------------------------------------------------------
-- 2.3. admins (관리자)
-- 의존성: 없음
-- Entity: com.tech.n.ai.domain.aurora.entity.auth.AdminEntity
-- ---------------------------------------------------------------------

CREATE TABLE admins (
    id              BIGINT UNSIGNED     NOT NULL    PRIMARY KEY     COMMENT 'TSID',
    email           VARCHAR(100)        NOT NULL                    COMMENT '이메일',
    username        VARCHAR(50)         NOT NULL                    COMMENT '사용자명',
    password        VARCHAR(255)        NOT NULL                    COMMENT '비밀번호 해시',
    role            VARCHAR(50)         NOT NULL                    COMMENT '역할 (ADMIN, SUPER_ADMIN 등)',
    is_active       BOOLEAN             NOT NULL    DEFAULT TRUE    COMMENT '활성화 여부',
    last_login_at   DATETIME            NULL                        COMMENT '마지막 로그인 일시',
    is_deleted      BOOLEAN             NOT NULL    DEFAULT FALSE   COMMENT '삭제 여부',
    deleted_at      DATETIME            NULL                        COMMENT '삭제 일시',
    deleted_by      BIGINT UNSIGNED     NULL                        COMMENT '삭제한 사용자 ID',
    created_at      DATETIME            NOT NULL                    COMMENT '생성 일시',
    created_by      BIGINT UNSIGNED     NULL                        COMMENT '생성한 사용자 ID',
    updated_at      DATETIME            NULL                        COMMENT '수정 일시',
    updated_by      BIGINT UNSIGNED     NULL                        COMMENT '수정한 사용자 ID',
    UNIQUE  KEY uk_admin_email      (email),
    UNIQUE  KEY uk_admin_username   (username),
    INDEX       idx_admin_role      (role),
    INDEX       idx_admin_is_active (is_active),
    INDEX       idx_admin_is_deleted(is_deleted)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='관리자 테이블';

-- ---------------------------------------------------------------------
-- 2.4. refresh_tokens (JWT Refresh Token)
-- 의존성: users (user_id FK), admins (admin_id FK)
-- Entity: com.tech.n.ai.domain.aurora.entity.auth.RefreshTokenEntity
-- ---------------------------------------------------------------------

CREATE TABLE refresh_tokens (
    id          BIGINT UNSIGNED     NOT NULL    PRIMARY KEY     COMMENT 'TSID',
    user_id     BIGINT UNSIGNED     NULL                        COMMENT '사용자 ID (User용 토큰)',
    admin_id    BIGINT UNSIGNED     NULL                        COMMENT '관리자 ID (Admin용 토큰)',
    token       VARCHAR(500)        NOT NULL                    COMMENT 'Refresh Token',
    expires_at  DATETIME            NOT NULL                    COMMENT '만료 일시',
    is_deleted  BOOLEAN             NOT NULL    DEFAULT FALSE   COMMENT '삭제 여부',
    deleted_at  DATETIME            NULL                        COMMENT '삭제 일시',
    deleted_by  BIGINT UNSIGNED     NULL                        COMMENT '삭제한 사용자 ID',
    created_at  DATETIME            NOT NULL                    COMMENT '생성 일시',
    created_by  BIGINT UNSIGNED     NULL                        COMMENT '생성한 사용자 ID',
    updated_at  DATETIME            NULL                        COMMENT '수정 일시',
    updated_by  BIGINT UNSIGNED     NULL                        COMMENT '수정한 사용자 ID',
    UNIQUE  KEY uk_refresh_token        (token),
    INDEX       idx_refresh_user_id     (user_id),
    INDEX       idx_refresh_admin_id    (admin_id),
    INDEX       idx_refresh_expires_at  (expires_at),
    INDEX       idx_refresh_is_deleted  (is_deleted),
    CONSTRAINT fk_refresh_token_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_refresh_token_admin FOREIGN KEY (admin_id) REFERENCES admins (id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Refresh Token 테이블';

-- ---------------------------------------------------------------------
-- 2.5. email_verifications (이메일 인증/비밀번호 재설정 토큰)
-- 의존성: 없음
-- Entity: com.tech.n.ai.domain.aurora.entity.auth.EmailVerificationEntity
-- ---------------------------------------------------------------------

CREATE TABLE email_verifications (
    id          BIGINT UNSIGNED     NOT NULL    PRIMARY KEY     COMMENT 'TSID',
    email       VARCHAR(100)        NOT NULL                    COMMENT '인증 대상 이메일',
    token       VARCHAR(255)        NOT NULL                    COMMENT '인증 토큰',
    type        VARCHAR(50)         NOT NULL                    COMMENT '토큰 타입 (EMAIL_VERIFICATION, PASSWORD_RESET)',
    expires_at  DATETIME            NOT NULL                    COMMENT '만료 일시',
    verified_at DATETIME            NULL                        COMMENT '인증 완료 일시',
    is_deleted  BOOLEAN             NOT NULL    DEFAULT FALSE   COMMENT '삭제 여부',
    deleted_at  DATETIME            NULL                        COMMENT '삭제 일시',
    deleted_by  BIGINT UNSIGNED     NULL                        COMMENT '삭제한 사용자 ID',
    created_at  DATETIME            NOT NULL                    COMMENT '생성 일시',
    created_by  BIGINT UNSIGNED     NULL                        COMMENT '생성한 사용자 ID',
    updated_at  DATETIME            NULL                        COMMENT '수정 일시',
    updated_by  BIGINT UNSIGNED     NULL                        COMMENT '수정한 사용자 ID',
    UNIQUE  KEY uk_email_verification_token     (token),
    INDEX       idx_email_verification_email    (email),
    INDEX       idx_email_verification_type     (email, type),
    INDEX       idx_email_verification_expires  (expires_at),
    INDEX       idx_email_verification_deleted  (is_deleted)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='이메일 인증 테이블';

-- ---------------------------------------------------------------------
-- 2.6. user_history (사용자 변경 이력)
-- 의존성: users (user_id FK)
-- Entity: com.tech.n.ai.domain.aurora.entity.auth.UserHistoryEntity
-- ---------------------------------------------------------------------

CREATE TABLE user_history (
    history_id      BIGINT UNSIGNED     NOT NULL    PRIMARY KEY     COMMENT 'TSID',
    user_id         BIGINT UNSIGNED     NOT NULL                    COMMENT '사용자 ID',
    operation_type  VARCHAR(20)         NOT NULL                    COMMENT '작업 타입 (INSERT, UPDATE, DELETE)',
    before_data     TEXT                NULL                        COMMENT '변경 전 데이터 (JSON)',
    after_data      TEXT                NULL                        COMMENT '변경 후 데이터 (JSON)',
    changed_by      BIGINT UNSIGNED     NULL                        COMMENT '변경한 사용자 ID',
    changed_at      DATETIME            NOT NULL                    COMMENT '변경 일시',
    change_reason   VARCHAR(500)        NULL                        COMMENT '변경 사유',
    INDEX       idx_user_history_user_id    (user_id),
    INDEX       idx_user_history_changed_at (changed_at),
    INDEX       idx_user_history_operation  (operation_type, changed_at),
    CONSTRAINT fk_user_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='사용자 변경 이력 테이블';

-- ---------------------------------------------------------------------
-- 2.7. admin_history (관리자 변경 이력)
-- 의존성: admins (admin_id FK)
-- Entity: com.tech.n.ai.domain.aurora.entity.auth.AdminHistoryEntity
-- ---------------------------------------------------------------------

CREATE TABLE admin_history (
    history_id      BIGINT UNSIGNED     NOT NULL    PRIMARY KEY     COMMENT 'TSID',
    admin_id        BIGINT UNSIGNED     NOT NULL                    COMMENT '관리자 ID',
    operation_type  VARCHAR(20)         NOT NULL                    COMMENT '작업 타입 (INSERT, UPDATE, DELETE)',
    before_data     TEXT                NULL                        COMMENT '변경 전 데이터 (JSON)',
    after_data      TEXT                NULL                        COMMENT '변경 후 데이터 (JSON)',
    changed_by      BIGINT UNSIGNED     NULL                        COMMENT '변경한 관리자 ID',
    changed_at      DATETIME            NOT NULL                    COMMENT '변경 일시',
    change_reason   VARCHAR(500)        NULL                        COMMENT '변경 사유',
    INDEX       idx_admin_history_admin_id   (admin_id),
    INDEX       idx_admin_history_changed_at (changed_at),
    INDEX       idx_admin_history_operation  (operation_type, changed_at),
    CONSTRAINT fk_admin_history_admin FOREIGN KEY (admin_id) REFERENCES admins (id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='관리자 변경 이력 테이블';


-- =====================================================================
-- 3. bookmark 스키마 테이블 생성
-- =====================================================================

USE bookmark;

-- ---------------------------------------------------------------------
-- 3.1. bookmarks (EmergingTech 북마크)
-- 의존성: 없음 (user_id는 auth.users 참조이나 스키마 간 FK 미지원)
-- Entity: com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkEntity
-- ---------------------------------------------------------------------

CREATE TABLE bookmarks (
    id                  BIGINT UNSIGNED     NOT NULL    PRIMARY KEY     COMMENT 'TSID',
    user_id             BIGINT UNSIGNED     NOT NULL                    COMMENT '사용자 ID (auth.users 참조)',
    emerging_tech_id    VARCHAR(24)         NOT NULL                    COMMENT 'MongoDB EmergingTech ObjectId',
    title               VARCHAR(500)        NOT NULL                    COMMENT '북마크 제목',
    url                 VARCHAR(2048)       NOT NULL                    COMMENT 'URL',
    provider            VARCHAR(50)         NULL                        COMMENT '콘텐츠 제공자 (hacker-news, dev.to 등)',
    summary             TEXT                NULL                        COMMENT '콘텐츠 요약',
    published_at        DATETIME            NULL                        COMMENT '원본 게시일',
    tag                 VARCHAR(100)        NULL                        COMMENT '태그 (파이프 구분: tag1|tag2|tag3)',
    memo                TEXT                NULL                        COMMENT '사용자 메모',
    is_deleted          BOOLEAN             NOT NULL    DEFAULT FALSE   COMMENT '삭제 여부',
    deleted_at          DATETIME            NULL                        COMMENT '삭제 일시',
    deleted_by          BIGINT UNSIGNED     NULL                        COMMENT '삭제한 사용자 ID',
    created_at          DATETIME            NOT NULL                    COMMENT '생성 일시',
    created_by          BIGINT UNSIGNED     NULL                        COMMENT '생성한 사용자 ID',
    updated_at          DATETIME            NULL                        COMMENT '수정 일시',
    updated_by          BIGINT UNSIGNED     NULL                        COMMENT '수정한 사용자 ID',
    INDEX       idx_bookmark_user_id        (user_id),
    INDEX       idx_bookmark_user_deleted   (user_id, is_deleted),
    INDEX       idx_bookmark_emerging_tech  (emerging_tech_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='EmergingTech 북마크 테이블';

-- ---------------------------------------------------------------------
-- 3.2. bookmark_history (북마크 변경 이력)
-- 의존성: bookmarks (bookmark_id FK)
-- Entity: com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkHistoryEntity
-- ---------------------------------------------------------------------

CREATE TABLE bookmark_history (
    history_id      BIGINT UNSIGNED     NOT NULL    PRIMARY KEY     COMMENT 'TSID',
    bookmark_id     BIGINT UNSIGNED     NOT NULL                    COMMENT '북마크 ID',
    operation_type  VARCHAR(20)         NOT NULL                    COMMENT '작업 타입 (INSERT, UPDATE, DELETE)',
    before_data     TEXT                NULL                        COMMENT '변경 전 데이터 (JSON)',
    after_data      TEXT                NULL                        COMMENT '변경 후 데이터 (JSON)',
    changed_by      BIGINT UNSIGNED     NULL                        COMMENT '변경한 사용자 ID',
    changed_at      DATETIME            NOT NULL                    COMMENT '변경 일시',
    change_reason   VARCHAR(500)        NULL                        COMMENT '변경 사유',
    INDEX       idx_bookmark_history_bookmark_id (bookmark_id),
    INDEX       idx_bookmark_history_changed_at  (changed_at),
    INDEX       idx_bookmark_history_operation   (operation_type, changed_at),
    CONSTRAINT fk_bookmark_history_bookmark FOREIGN KEY (bookmark_id) REFERENCES bookmarks (id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='북마크 변경 이력 테이블';


-- =====================================================================
-- 4. chatbot 스키마 테이블 생성
-- =====================================================================

USE chatbot;

-- ---------------------------------------------------------------------
-- 4.1. conversation_sessions (대화 세션)
-- 의존성: 없음 (user_id는 auth.users 참조이나 스키마 간 FK 미지원)
-- Entity: com.tech.n.ai.domain.aurora.entity.chatbot.ConversationSessionEntity
-- Note: BaseEntity의 id 컬럼이 session_id로 @AttributeOverride됨
-- ---------------------------------------------------------------------

CREATE TABLE conversation_sessions (
    session_id      BIGINT UNSIGNED     NOT NULL    PRIMARY KEY     COMMENT 'TSID',
    user_id         BIGINT UNSIGNED     NOT NULL                    COMMENT '사용자 ID (auth.users 참조)',
    title           VARCHAR(200)        NULL                        COMMENT '세션 제목',
    last_message_at DATETIME            NOT NULL                    COMMENT '마지막 메시지 시간',
    is_active       BOOLEAN             NOT NULL    DEFAULT TRUE    COMMENT '활성 세션 여부',
    is_deleted      BOOLEAN             NOT NULL    DEFAULT FALSE   COMMENT '삭제 여부',
    deleted_at      DATETIME            NULL                        COMMENT '삭제 일시',
    deleted_by      BIGINT UNSIGNED     NULL                        COMMENT '삭제자 ID',
    created_at      DATETIME            NOT NULL                    COMMENT '생성 일시',
    created_by      BIGINT UNSIGNED     NULL                        COMMENT '생성자 ID',
    updated_at      DATETIME            NULL                        COMMENT '수정 일시',
    updated_by      BIGINT UNSIGNED     NULL                        COMMENT '수정자 ID',
    INDEX       idx_session_user_active_lastmsg (user_id, is_active, last_message_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='대화 세션 테이블';

-- ---------------------------------------------------------------------
-- 4.2. conversation_messages (대화 메시지)
-- 의존성: conversation_sessions (session_id FK)
-- Entity: com.tech.n.ai.domain.aurora.entity.chatbot.ConversationMessageEntity
-- Note: BaseEntity를 상속하지 않음 (독립 PK, 최소 감사 필드)
-- ---------------------------------------------------------------------

CREATE TABLE conversation_messages (
    message_id      BIGINT UNSIGNED     NOT NULL    PRIMARY KEY     COMMENT 'TSID',
    session_id      BIGINT UNSIGNED     NOT NULL                    COMMENT '세션 ID',
    role            VARCHAR(20)         NOT NULL                    COMMENT '메시지 역할 (USER, ASSISTANT, SYSTEM)',
    content         TEXT                NOT NULL                    COMMENT '메시지 내용',
    token_count     INT                 NULL                        COMMENT '토큰 수 (비용 계산용)',
    sequence_number INT                 NOT NULL                    COMMENT '대화 순서 (1부터 시작)',
    created_at      DATETIME            NOT NULL                    COMMENT '생성 일시',
    INDEX       idx_message_session_sequence (session_id, sequence_number),
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES conversation_sessions (session_id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='대화 메시지 테이블';


-- =====================================================================
-- DDL 실행 완료
-- =====================================================================
--
-- 생성된 스키마: auth, bookmark, chatbot (3개)
-- 생성된 테이블: 12개
--   auth (7): providers, users, admins, refresh_tokens,
--             email_verifications, user_history, admin_history
--   bookmark (2): bookmarks, bookmark_history
--   chatbot (2): conversation_sessions, conversation_messages
--
-- =====================================================================
