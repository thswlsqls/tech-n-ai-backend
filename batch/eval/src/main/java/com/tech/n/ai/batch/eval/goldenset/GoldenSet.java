package com.tech.n.ai.batch.eval.goldenset;

import java.util.List;

/**
 * 골든셋 전체
 *
 * @param version 골든셋 판 이름. 리포트에 그대로 기록한다
 * @param collection 질문이 겨냥한 MongoDB 컬렉션
 * @param items 질문 목록
 */
public record GoldenSet(
    String version,
    String collection,
    List<GoldenSetItem> items
) {}
