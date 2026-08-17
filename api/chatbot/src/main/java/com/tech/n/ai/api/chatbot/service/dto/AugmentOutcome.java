package com.tech.n.ai.api.chatbot.service.dto;

/**
 * 근거가 약해서 검색 조건을 완화해 다시 찾은 기록
 *
 * 보강을 꺼두면 {@code none()} 값이 그대로 실린다. 평가 잡은 이 값을 리포트에 옮겨 적는다.
 *
 * @param triggered 보강이 켜져 있고 1차 검색 결과가 약함 판정에 걸렸는지. 재검색이 실제로 돌아간 질문은 {@code attempts > 0}으로 센다
 * @param attempts 실제로 돌린 재검색 횟수
 * @param adopted 재검색이 새로 물어온 문서가 최종 결과 목록에 들어갔는지. 그 문서가 정답인지까지는 모른다
 */
public record AugmentOutcome(
    boolean triggered,
    int attempts,
    boolean adopted
) {

    public static AugmentOutcome none() {
        return new AugmentOutcome(false, 0, false);
    }
}
