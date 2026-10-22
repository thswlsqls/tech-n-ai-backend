-- =====================================================================
-- auth 초기 데이터: 슈퍼 관리자 계정 및 생성 이력
-- 비밀번호는 저장소에 두지 않는다. 각자 로컬 http-client.private.env.json 의 adminPassword 에 둔다 (BCrypt encoded, strength=12)
-- =====================================================================

INSERT INTO admins (
    id, email, username, password, role, is_active, is_deleted,
    failed_login_attempts, account_locked_until, last_login_at,
    created_at, created_by, updated_at, updated_by
) VALUES (
    817225402408821626,
    'thsdmsqlsspdlqj@naver.com',
    'superadmin',
    '$2a$12$ieowjy031qun82NPlzuw1uTvrukmoVxsZo5YdlVPTctR5hRsyqw7.',
    'ADMIN', TRUE, FALSE, 0, NULL, NULL,
    CURRENT_TIMESTAMP(6), NULL, CURRENT_TIMESTAMP(6), NULL
) ON DUPLICATE KEY UPDATE
    password              = VALUES(password),
    is_active             = TRUE,
    is_deleted            = FALSE,
    failed_login_attempts = 0,
    account_locked_until  = NULL,
    updated_at            = CURRENT_TIMESTAMP(6);

INSERT INTO admin_history (
    history_id, admin_id, operation_type, before_data, after_data,
    changed_by, changed_at, change_reason
) VALUES (
    817225402429793147,
    817225402408821626,
    'CREATE', NULL,
    JSON_OBJECT(
        'email', 'thsdmsqlsspdlqj@naver.com',
        'username', 'superadmin',
        'role', 'ADMIN',
        'isActive', TRUE
    ),
    NULL, CURRENT_TIMESTAMP(6),
    '시스템 초기 슈퍼 관리자 계정 생성'
) ON DUPLICATE KEY UPDATE
    changed_at = CURRENT_TIMESTAMP(6);
