# 002 - 에이전트 실행 실패 시 ASSISTANT 메시지 미저장

## 기본 정보
- **발생일**: 2026-03-20
- **모듈**: `api/agent`
- **심각도**: Medium (기능 누락)
- **상태**: 해결 완료

## 증상
- 에이전트 실행이 실패하면 사용자(USER) 메시지만 저장되고, 에이전트의 실패 응답(ASSISTANT)은 대화 이력에 저장되지 않음
- 세션 재조회 시 실패한 실행의 결과를 관리자가 확인할 수 없음
- 프론트엔드에서는 현재 세션의 optimistic UI로 보이지만, 페이지 새로고침/세션 전환 후 재조회하면 사라짐

## 근본 원인

### AgentFacade의 조건부 메시지 저장
```java
// Before (문제 코드) - AgentFacade.java:53-56
if (result.success()) {
    conversationMessageService.saveMessage(sessionId, "ASSISTANT", result.summary(), null);
}
```

`result.success()`가 `false`인 경우 ASSISTANT 메시지가 저장되지 않음. 설계 의도는 "실패 시 에러 텍스트가 대화 이력에 혼입되지 않도록" 하기 위함이었으나, 관리자 관점에서 실패 결과를 확인할 수 없는 것이 더 큰 문제.

### 영향 범위
- 에이전트 루프 감지(`AgentLoopDetectedException`) 시에도 `success=true`로 반환하므로 이 경우는 저장됨
- 실제 실패(예외 발생) 시에만 ASSISTANT 메시지 미저장
- LangChain4j 호출 실패, 외부 API 타임아웃 등의 경우

## 수정 내용

### 파일: `api/agent/src/main/java/com/tech/n/ai/api/agent/facade/AgentFacade.java`

```java
// After (수정 코드) - AgentFacade.java:53-54
// ASSISTANT 메시지 저장 (성공/실패 무관 — 관리자가 실행 결과를 세션 재조회 시 확인 가능)
conversationMessageService.saveMessage(sessionId, "ASSISTANT", result.summary(), null);
```

`if (result.success())` 조건 제거. `result.summary()`는 실패 시에도 에러 메시지를 포함한 요약을 반환하므로 항상 의미 있는 값.

## 교훈
- 관리자 도구에서는 실패 정보도 이력으로 남기는 것이 운영/디버깅에 유리
- "깨끗한 이력"보다 "추적 가능한 이력"이 더 중요한 경우가 많음
