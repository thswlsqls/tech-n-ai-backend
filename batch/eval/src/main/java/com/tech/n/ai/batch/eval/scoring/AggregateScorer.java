package com.tech.n.ai.batch.eval.scoring;

import com.tech.n.ai.batch.eval.goldenset.GoldenSetItemType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 질문별 결과를 골든셋 전체 수치로 묶는다.
 */
public final class AggregateScorer {

    private static final int RECENCY_HIT_K = 5;

    private AggregateScorer() {
    }

    public static AggregateMetrics aggregate(List<QuestionOutcome> outcomes, List<Integer> kValues) {
        int intentNotRag = 0;
        int fallbackPath = 0;
        int searchFailed = 0;
        int noEvidenceType = 0;
        int noEvidenceCorrectlyEmpty = 0;
        int noEvidenceWronglyNonEmpty = 0;

        List<RetrievalMetrics> scored = new ArrayList<>();
        Map<GoldenSetItemType, Integer> scoredCountByType = new EnumMap<>(GoldenSetItemType.class);
        int recencyLatestTarget = 0;
        int recencyLatestHit = 0;

        for (QuestionOutcome outcome : outcomes) {
            // 판정 순서 고정. 한 질문은 한 버킷에만 들어간다.
            if (!outcome.intentRagRequired()) {
                intentNotRag++;
                continue;
            }
            if (outcome.fallbackPath()) {
                fallbackPath++;
                continue;
            }
            if (outcome.searchFailed()) {
                searchFailed++;
                continue;
            }
            if (outcome.type() == GoldenSetItemType.NO_EVIDENCE) {
                noEvidenceType++;
                if (outcome.candidatesEmpty()) {
                    noEvidenceCorrectlyEmpty++;
                } else {
                    noEvidenceWronglyNonEmpty++;
                }
                continue;
            }

            scored.add(RetrievalScorer.score(
                outcome.rankedExternalIds(), outcome.expectedExternalIds(), kValues));
            scoredCountByType.merge(outcome.type(), 1, Integer::sum);

            if (outcome.type() == GoldenSetItemType.RECENCY && outcome.latestExternalId() != null) {
                recencyLatestTarget++;
                if (containsWithinTopK(outcome.rankedExternalIds(), outcome.latestExternalId())) {
                    recencyLatestHit++;
                }
            }
        }

        return new AggregateMetrics(
            outcomes.size(),
            scored.size(),
            averageRecallAtK(scored, kValues),
            hitRateAtK(scored, kValues),
            averageReciprocalRank(scored),
            countZeroHit(scored),
            averageFalsePositiveAtK(scored, kValues),
            new AggregateMetrics.Excluded(intentNotRag, fallbackPath, searchFailed, noEvidenceType),
            scoredCountByType,
            recencyLatestTarget == 0 ? null : (double) recencyLatestHit / recencyLatestTarget,
            new AggregateMetrics.NoEvidence(
                noEvidenceType, noEvidenceCorrectlyEmpty, noEvidenceWronglyNonEmpty)
        );
    }

    private static boolean containsWithinTopK(List<String> rankedExternalIds, String externalId) {
        return rankedExternalIds.subList(0, Math.min(RECENCY_HIT_K, rankedExternalIds.size()))
            .contains(externalId);
    }

    private static Map<Integer, Double> averageRecallAtK(List<RetrievalMetrics> scored, List<Integer> kValues) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        for (int k : kValues) {
            result.put(k, average(scored.stream().mapToDouble(m -> m.recallAtK().get(k)).sum(), scored.size()));
        }
        return result;
    }

    private static Map<Integer, Double> hitRateAtK(List<RetrievalMetrics> scored, List<Integer> kValues) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        for (int k : kValues) {
            long hits = scored.stream().filter(m -> m.hitAtK().get(k)).count();
            result.put(k, average(hits, scored.size()));
        }
        return result;
    }

    private static Map<Integer, Double> averageFalsePositiveAtK(List<RetrievalMetrics> scored, List<Integer> kValues) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        for (int k : kValues) {
            result.put(k, average(scored.stream().mapToInt(m -> m.falsePositiveAtK().get(k)).sum(), scored.size()));
        }
        return result;
    }

    private static double averageReciprocalRank(List<RetrievalMetrics> scored) {
        return average(scored.stream().mapToDouble(RetrievalMetrics::reciprocalRank).sum(), scored.size());
    }

    private static int countZeroHit(List<RetrievalMetrics> scored) {
        return (int) scored.stream().filter(m -> m.firstHitRank() == null).count();
    }

    private static double average(double total, int count) {
        return count == 0 ? 0.0 : total / count;
    }
}
