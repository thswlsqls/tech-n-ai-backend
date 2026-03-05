-- =============================================================================
-- ALTER: admins 테이블에 로그인 실패 제한 필드 추가
-- 목적: Brute-force 공격 방지를 위한 계정 잠금 메커니즘
-- 참고: OWASP Authentication Cheatsheet, CWE-307
-- =============================================================================

ALTER TABLE admins
    ADD COLUMN failed_login_attempts INT          NOT NULL DEFAULT 0   COMMENT '연속 로그인 실패 횟수' AFTER last_login_at,
    ADD COLUMN account_locked_until  TIMESTAMP(6) NULL                 COMMENT '계정 잠금 해제 시각'   AFTER failed_login_attempts;
