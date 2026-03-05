-- =====================================================================
-- Aurora MySQL 초기 데이터 INSERT DML
-- =====================================================================
-- 작성 일시: 2026-03-05
-- 대상: auth 스키마 - 슈퍼 관리자 계정 및 생성 이력
-- 비밀번호: Admin1234! (BCrypt encoded)
--
-- 실행 순서:
-- 1. create.sql 실행 후 본 스크립트 실행
-- 2. admins 테이블 INSERT → admin_history 테이블 INSERT
-- =====================================================================

USE auth;

-- =====================================================================
-- 1. 슈퍼 관리자 계정
-- =====================================================================

INSERT INTO admins (
    id,
    email,
    username,
    password,
    role,
    is_active,
    is_deleted,
    created_at,
    created_by,
    updated_at,
    updated_by
) VALUES (
    817225402408821626,
    'thsdmsqlsspdlqj@naver.com',
    'superadmin',
    '$2a$10$VGUv3P8kz9fTBqkrMHCyyehGpDeoSV4hPzebGhMefDUWQOCiucjh6',
    'ADMIN',
    TRUE,
    FALSE,
    CURRENT_TIMESTAMP(6),
    NULL,
    CURRENT_TIMESTAMP(6),
    NULL
);

-- =====================================================================
-- 2. 슈퍼 관리자 생성 이력
-- =====================================================================

INSERT INTO admin_history (
    history_id,
    admin_id,
    operation_type,
    before_data,
    after_data,
    changed_by,
    changed_at,
    change_reason
) VALUES (
    817225402429793147,
    817225402408821626,
    'CREATE',
    NULL,
    JSON_OBJECT(
        'email', 'thsdmsqlsspdlqj@naver.com',
        'username', 'superadmin',
        'role', 'ADMIN',
        'isActive', TRUE
    ),
    NULL,
    CURRENT_TIMESTAMP(6),
    '시스템 초기 슈퍼 관리자 계정 생성'
);
