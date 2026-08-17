package com.tech.n.ai.batch.eval.job;

import com.tech.n.ai.api.chatbot.chain.InputInterpretationChain;
import com.tech.n.ai.api.chatbot.chain.ResultRefinementChain;
import com.tech.n.ai.api.chatbot.service.IntentClassificationService;
import com.tech.n.ai.api.chatbot.service.RetrievalService;
import com.tech.n.ai.api.chatbot.service.SearchOptionsFactory;
import com.tech.n.ai.api.chatbot.service.TokenService;
import com.tech.n.ai.api.chatbot.service.dto.AugmentOutcome;
import com.tech.n.ai.api.chatbot.service.dto.GraphSearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.Intent;
import com.tech.n.ai.api.chatbot.service.dto.RetrievalOutcome;
import com.tech.n.ai.api.chatbot.service.dto.RetrievalPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchOptions;
import com.tech.n.ai.api.chatbot.service.dto.SearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.SearchPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchQuery;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetItem;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetItemType;
import com.tech.n.ai.batch.eval.report.EvalReport;
import com.tech.n.ai.batch.eval.scoring.ExternalIdExtractor;
import com.tech.n.ai.batch.eval.scoring.QuestionOutcome;
import com.tech.n.ai.batch.eval.scoring.RankedCandidate;
import com.tech.n.ai.batch.eval.scoring.RetrievalScorer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 골든셋 질문 하나를 운영과 같은 순서로 실행하고 결과를 기록한다.
 *
 * 답변 생성만 뺀다. 검색 품질만 재는 것이 이번 잡의 목적이라 생성 모델을 부르지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionRunner {

    /** RRF 직전 후보가 최대 15건이라 k도 1~15로 잡는다 */
    public static final List<Integer> K_VALUES = IntStream.rangeClosed(1, 15).boxed().toList();

    private static final String EXCLUDED_INTENT_NOT_RAG = "INTENT_NOT_RAG";
    private static final String EXCLUDED_FALLBACK_PATH = "FALLBACK_PATH";
    private static final String EXCLUDED_SEARCH_FAILED = "SEARCH_FAILED";
    private static final String EXCLUDED_NO_EVIDENCE_TYPE = "NO_EVIDENCE_TYPE";

    private static final String SOURCE_VECTOR = "VECTOR";
    private static final String SOURCE_GRAPH = "GRAPH";

    private final IntentClassificationService intentService;
    private final InputInterpretationChain inputChain;
    private final SearchOptionsFactory searchOptionsFactory;
    private final RetrievalService retrievalService;
    private final ResultRefinementChain refinementChain;
    private final TokenService tokenService;

    public QuestionRunResult run(GoldenSetItem item) {
        Intent intent = intentService.classifyIntent(item.question());
        if (intent != Intent.RAG_REQUIRED) {
            log.info("Question {} skipped: intent={}", item.id(), intent);
            return notRagResult(item, intent);
        }

        SearchQuery searchQuery = inputChain.interpret(item.question());
        SearchOptions searchOptions = searchOptionsFactory.create(searchQuery);

        RetrievalOutcome retrieval =
            retrievalService.retrieve(searchQuery.query(), 0L, searchOptions);
        // 기준선 리포트와 같은 뜻을 유지해야 전·후 지연을 비교할 수 있다. 그래프 시간은 따로 적는다.
        SearchOutcome searchOutcome = retrieval.vector();
        long searchMs = retrieval.vectorLatencyMs();

        boolean recencyDetected = searchQuery.context().isRecencyDetected();
        boolean scoreFusionApplied = Boolean.TRUE.equals(searchOptions.enableScoreFusion());

        long refineStart = System.currentTimeMillis();
        // refine의 첫 인자는 검색 쿼리가 아니라 사용자가 입력한 질문 원문이다
        List<SearchResult> refined = refinementChain.refine(
            item.question(), retrieval.merged(), recencyDetected, scoreFusionApplied);
        long refineMs = System.currentTimeMillis() - refineStart;

        Set<String> expected = new HashSet<>(item.expectedExternalIds());
        List<RankedCandidate> candidates = toCandidates(searchOutcome.candidates(), expected);
        List<EvalReport.ChainOutputItem> chainOutput = toChainOutput(refined, expected);
        List<EvalReport.MergedItem> mergedOutput =
            toMergedOutput(retrieval.merged(), searchOutcome.results().size(), expected);

        List<String> byFusionRank = candidates.stream()
            .sorted(Comparator.comparingInt(RankedCandidate::fusionRank))
            .map(RankedCandidate::externalId)
            .toList();
        List<String> byVectorRank = candidates.stream()
            .sorted(Comparator.comparingInt(RankedCandidate::vectorRank))
            .map(RankedCandidate::externalId)
            .toList();
        List<String> byChainOutput = chainOutput.stream()
            .map(EvalReport.ChainOutputItem::externalId)
            .toList();
        List<String> byMergedRank = mergedOutput.stream()
            .map(EvalReport.MergedItem::externalId)
            .toList();

        boolean fallbackPath = searchOutcome.path() == SearchPath.HYBRID_FALLBACK_STANDARD
            || searchOutcome.path() == SearchPath.HYBRID_FALLBACK_FAILED;
        boolean searchFailed = searchOutcome.path() == SearchPath.HYBRID_FALLBACK_FAILED
            || searchOutcome.path() == SearchPath.STANDARD_FAILED;
        boolean candidatesEmpty = candidates.isEmpty();
        boolean noEvidenceType = item.type() == GoldenSetItemType.NO_EVIDENCE;

        String excludedReason = excludedReason(true, fallbackPath, searchFailed, noEvidenceType);

        EvalReport.Question question = new EvalReport.Question(
            item.id(),
            item.type(),
            item.question(),
            intent.name(),
            searchOutcome.path().name(),
            searchOutcome.recencyQueryFailed(),
            excludedReason == null,
            excludedReason,
            // fallback·예외로 빠진 질문은 후보가 비어도 "맞혔다"가 아니다. 집계와 판정 순서를 맞춘다.
            noEvidenceType && EXCLUDED_NO_EVIDENCE_TYPE.equals(excludedReason)
                ? new EvalReport.NoEvidence(candidatesEmpty, candidatesEmpty)
                : null,
            new EvalReport.LatencyMs(searchMs, refineMs, null, retrieval.graphLatencyMs()),
            new EvalReport.Tokens(tokenService.estimateTokens(item.question()), 0, 0),
            item.expectedExternalIds(),
            item.latestExternalId(),
            candidates,
            chainOutput,
            mergedOutput,
            toGraphBlock(retrieval.graph()),
            retrieval.path().name(),
            toAugmentBlock(retrieval.augment()),
            new EvalReport.Metrics(
                RetrievalScorer.score(byVectorRank, expected, K_VALUES),
                RetrievalScorer.score(byFusionRank, expected, K_VALUES),
                RetrievalScorer.score(byChainOutput, expected, K_VALUES),
                RetrievalScorer.score(byMergedRank, expected, K_VALUES)
            )
        );

        return new QuestionRunResult(
            question,
            outcome(item, true, fallbackPath, searchFailed, candidatesEmpty, byVectorRank, expected),
            outcome(item, true, fallbackPath, searchFailed, candidatesEmpty, byFusionRank, expected),
            outcome(item, true, fallbackPath, searchFailed, candidatesEmpty, byChainOutput, expected),
            outcome(item, true, fallbackPath, searchFailed, candidatesEmpty, byMergedRank, expected),
            refined
        );
    }

    private QuestionRunResult notRagResult(GoldenSetItem item, Intent intent) {
        Set<String> expected = new HashSet<>(item.expectedExternalIds());
        EvalReport.Question question = new EvalReport.Question(
            item.id(),
            item.type(),
            item.question(),
            intent.name(),
            null,
            false,
            false,
            EXCLUDED_INTENT_NOT_RAG,
            null,
            new EvalReport.LatencyMs(null, null, null, null),
            new EvalReport.Tokens(0, 0, 0),
            item.expectedExternalIds(),
            item.latestExternalId(),
            List.of(),
            List.of(),
            List.of(),
            toGraphBlock(GraphSearchOutcome.disabled()),
            RetrievalPath.NONE.name(),
            toAugmentBlock(AugmentOutcome.none()),
            new EvalReport.Metrics(
                RetrievalScorer.score(List.of(), expected, K_VALUES),
                RetrievalScorer.score(List.of(), expected, K_VALUES),
                RetrievalScorer.score(List.of(), expected, K_VALUES),
                RetrievalScorer.score(List.of(), expected, K_VALUES)
            )
        );
        QuestionOutcome outcome =
            outcome(item, false, false, false, true, List.of(), expected);
        return new QuestionRunResult(question, outcome, outcome, outcome, outcome, List.of());
    }

    private QuestionOutcome outcome(GoldenSetItem item, boolean intentRagRequired,
                                     boolean fallbackPath, boolean searchFailed,
                                     boolean candidatesEmpty, List<String> rankedExternalIds,
                                     Set<String> expected) {
        return new QuestionOutcome(
            item.id(), item.type(), intentRagRequired, fallbackPath, searchFailed,
            candidatesEmpty, rankedExternalIds, expected, item.latestExternalId());
    }

    /**
     * 판정 순서는 집계와 같다: intentNotRag → fallbackPath → searchFailed → noEvidenceType
     */
    private String excludedReason(boolean intentRagRequired, boolean fallbackPath,
                                   boolean searchFailed, boolean noEvidenceType) {
        if (!intentRagRequired) {
            return EXCLUDED_INTENT_NOT_RAG;
        }
        if (fallbackPath) {
            return EXCLUDED_FALLBACK_PATH;
        }
        if (searchFailed) {
            return EXCLUDED_SEARCH_FAILED;
        }
        if (noEvidenceType) {
            return EXCLUDED_NO_EVIDENCE_TYPE;
        }
        return null;
    }

    private List<RankedCandidate> toCandidates(List<SearchResult> candidates, Set<String> expected) {
        List<Integer> byVectorScore = IntStream.range(0, candidates.size()).boxed()
            .sorted(Comparator.comparingDouble(
                (Integer i) -> vectorScore(candidates.get(i)) == null ? -1.0 : vectorScore(candidates.get(i)))
                .reversed())
            .toList();

        int[] vectorRank = new int[candidates.size()];
        for (int rank = 0; rank < byVectorScore.size(); rank++) {
            vectorRank[byVectorScore.get(rank)] = rank + 1;
        }

        List<RankedCandidate> result = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            SearchResult candidate = candidates.get(i);
            String externalId = identifierOf(candidate);
            result.add(new RankedCandidate(
                externalId,
                candidate.documentId(),
                i + 1,
                vectorRank[i],
                vectorScore(candidate),
                combinedScore(candidate),
                expected.contains(externalId)
            ));
        }
        return result;
    }

    private List<EvalReport.ChainOutputItem> toChainOutput(List<SearchResult> refined, Set<String> expected) {
        List<EvalReport.ChainOutputItem> result = new ArrayList<>();
        for (int i = 0; i < refined.size(); i++) {
            SearchResult item = refined.get(i);
            String externalId = identifierOf(item);
            result.add(new EvalReport.ChainOutputItem(
                externalId, item.documentId(), i + 1, item.score(), expected.contains(externalId)));
        }
        return result;
    }

    /**
     * 합친 목록을 순서 그대로 기록한다.
     * 앞쪽 vectorResultCount건은 벡터가 물고 온 문서이고 그 뒤는 그래프가 더한 문서다.
     */
    private List<EvalReport.MergedItem> toMergedOutput(List<SearchResult> merged,
                                                        int vectorResultCount,
                                                        Set<String> expected) {
        List<EvalReport.MergedItem> result = new ArrayList<>();
        for (int i = 0; i < merged.size(); i++) {
            SearchResult item = merged.get(i);
            String externalId = identifierOf(item);
            result.add(new EvalReport.MergedItem(
                externalId, item.documentId(), i + 1, item.score(), expected.contains(externalId),
                i < vectorResultCount ? SOURCE_VECTOR : SOURCE_GRAPH));
        }
        return result;
    }

    /**
     * 그래프를 타지 않은 질문도 키를 비우지 않는다. 꺼진 상태 그대로를 값으로 적는다.
     */
    private EvalReport.Graph toGraphBlock(GraphSearchOutcome graph) {
        return new EvalReport.Graph(
            graph.enabled(),
            graph.seedKeys(),
            graph.expandedKeys(),
            graph.externalIds(),
            graph.results().size(),
            graph.capped(),
            graph.latencyMs());
    }

    /**
     * 보강을 끄고 돌린 질문도 키를 비우지 않는다. 발동하지 않은 상태 그대로를 값으로 적는다.
     */
    private EvalReport.Augment toAugmentBlock(AugmentOutcome augment) {
        return new EvalReport.Augment(augment.triggered(), augment.attempts(), augment.adopted());
    }

    /**
     * external_id가 없는 문서는 documentId로 자리를 지킨다. 순위가 밀리지 않아야 k별 수치가 맞는다.
     */
    private String identifierOf(SearchResult result) {
        return ExternalIdExtractor.from(result).orElse(result.documentId());
    }

    private Double vectorScore(SearchResult result) {
        return metadataDouble(result, "vectorScore");
    }

    private Double combinedScore(SearchResult result) {
        return metadataDouble(result, "combinedScore");
    }

    private Double metadataDouble(SearchResult result, String field) {
        if (result.metadata() instanceof Document doc) {
            return doc.getDouble(field);
        }
        return null;
    }
}
