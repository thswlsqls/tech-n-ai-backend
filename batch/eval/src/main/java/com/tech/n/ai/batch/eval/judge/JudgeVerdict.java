package com.tech.n.ai.batch.eval.judge;

/**
 * 판정 모델이 낸 결과 한 건
 *
 * parsed가 false면 응답을 읽지 못한 것이라 score는 null이고 reason에 실패 사유가 들어간다.
 * 그런 건은 축의 분모에서 뺀다.
 */
public record JudgeVerdict(
    Integer score,
    String reason,
    boolean parsed
) {}
