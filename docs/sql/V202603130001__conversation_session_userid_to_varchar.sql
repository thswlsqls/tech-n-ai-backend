-- =============================================================================
-- Migration: conversation_sessions.user_id 타입 변경 (BIGINT → VARCHAR(50))
-- 목적: api-agent (String userId)와 api-chatbot (Long userId) 통합 지원
-- 날짜: 2026-03-13
-- =============================================================================

-- 기존 BIGINT 값은 VARCHAR로 자동 변환됨 (예: 12345 → "12345")
ALTER TABLE chatbot.conversation_sessions
    MODIFY COLUMN user_id VARCHAR(50) NOT NULL;

-- user_id 인덱스 재생성 (타입 변경 후 인덱스 최적화)
-- 기존 인덱스가 있다면 자동으로 변환되지만, 명시적으로 확인
-- ALTER TABLE chatbot.conversation_sessions DROP INDEX IF EXISTS idx_conversation_sessions_user_id;
-- CREATE INDEX idx_conversation_sessions_user_id ON chatbot.conversation_sessions (user_id);
