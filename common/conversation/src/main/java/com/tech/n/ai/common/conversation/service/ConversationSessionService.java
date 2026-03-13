package com.tech.n.ai.common.conversation.service;

import com.tech.n.ai.common.conversation.dto.SessionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.Optional;

/**
 * 대화 세션 서비스 인터페이스
 */
public interface ConversationSessionService {

    /**
     * 새 세션 생성
     *
     * @param userId 사용자 ID
     * @param title 세션 제목 (선택)
     * @return 세션 ID (TSID String)
     */
    String createSession(String userId, String title);

    /**
     * 세션 조회 및 소유권 검증
     *
     * @param sessionId 세션 ID (TSID String)
     * @param userId 사용자 ID
     * @return 세션 정보
     */
    SessionResponse getSession(String sessionId, String userId);

    /**
     * 마지막 메시지 시간 업데이트 및 자동 재활성화
     *
     * @param sessionId 세션 ID (TSID String)
     */
    void updateLastMessageAt(String sessionId);

    /**
     * 세션 목록 조회
     *
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 세션 목록
     */
    Page<SessionResponse> listSessions(String userId, Pageable pageable);

    /**
     * 활성 세션 조회
     *
     * @param userId 사용자 ID
     * @return 활성 세션 정보 (없으면 Optional.empty())
     */
    Optional<SessionResponse> getActiveSession(String userId);

    /**
     * 비활성 세션 처리 (배치 작업용)
     *
     * @param inactiveThreshold 비활성화 임계값
     * @return 처리된 세션 수
     */
    int deactivateInactiveSessions(Duration inactiveThreshold);

    /**
     * 만료 세션 처리 (배치 작업용)
     *
     * @param expirationDays 만료 기간 (일)
     * @return 처리된 세션 수
     */
    int expireInactiveSessions(int expirationDays);

    /**
     * 세션 타이틀 업데이트
     *
     * @param sessionId 세션 ID (TSID String)
     * @param userId    사용자 ID (소유권 검증용)
     * @param title     새 타이틀
     * @return 업데이트된 세션 정보
     */
    SessionResponse updateSessionTitle(String sessionId, String userId, String title);

    /**
     * 세션 삭제
     *
     * @param sessionId 세션 ID (TSID String)
     * @param userId 사용자 ID
     */
    void deleteSession(String sessionId, String userId);
}
