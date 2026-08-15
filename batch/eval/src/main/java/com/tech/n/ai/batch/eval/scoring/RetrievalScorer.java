package com.tech.n.ai.batch.eval.scoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 순위 목록과 기대 근거를 대조해 질문 한 건의 지표를 계산한다.
 */
public final class RetrievalScorer {

    private RetrievalScorer() {
    }

    /**
     * @param rankedExternalIds 순위가 매겨진 external_id 목록. 앞이 1위다
     * @param expectedExternalIds 기대 근거 external_id 집합
     * @param kValues 계산할 k 목록. 리스트 길이보다 큰 k는 리스트 전체를 본 값으로 채우고 키는 그대로 남긴다
     */
    public static RetrievalMetrics score(List<String> rankedExternalIds,
                                          Set<String> expectedExternalIds,
                                          List<Integer> kValues) {
        Map<Integer, Double> recallAtK = new LinkedHashMap<>();
        Map<Integer, Boolean> hitAtK = new LinkedHashMap<>();
        Map<Integer, Integer> falsePositiveAtK = new LinkedHashMap<>();

        for (int k : kValues) {
            List<String> topK = rankedExternalIds.subList(0, Math.min(k, rankedExternalIds.size()));
            // recall은 찾아낸 기대 근거의 가짓수로, 오검출은 자리 수로 센다
            int distinctHits = (int) topK.stream().filter(expectedExternalIds::contains).distinct().count();
            int hitPositions = (int) topK.stream().filter(expectedExternalIds::contains).count();
            // 기대 근거가 없는 질문(NO_EVIDENCE)은 recall을 정의할 수 없어 0으로 둔다
            recallAtK.put(k, expectedExternalIds.isEmpty()
                ? 0.0 : (double) distinctHits / expectedExternalIds.size());
            hitAtK.put(k, distinctHits > 0);
            falsePositiveAtK.put(k, topK.size() - hitPositions);
        }

        Integer firstHitRank = findFirstHitRank(rankedExternalIds, expectedExternalIds);
        double reciprocalRank = firstHitRank == null ? 0.0 : 1.0 / firstHitRank;

        return new RetrievalMetrics(recallAtK, hitAtK, reciprocalRank, firstHitRank, falsePositiveAtK);
    }

    private static Integer findFirstHitRank(List<String> rankedExternalIds, Set<String> expectedExternalIds) {
        for (int i = 0; i < rankedExternalIds.size(); i++) {
            if (expectedExternalIds.contains(rankedExternalIds.get(i))) {
                return i + 1;
            }
        }
        return null;
    }
}
