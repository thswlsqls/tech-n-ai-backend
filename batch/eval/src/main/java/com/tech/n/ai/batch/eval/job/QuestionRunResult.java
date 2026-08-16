package com.tech.n.ai.batch.eval.job;

import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.batch.eval.report.EvalReport;
import com.tech.n.ai.batch.eval.scoring.QuestionOutcome;

import java.util.List;

/**
 * 질문 한 건 실행 결과
 *
 * 리포트에 그대로 실을 질문 항목과, 네 기준으로 각각 집계에 넘길 결과를 함께 담는다.
 * refined는 정제 체인이 남긴 문서로, 답변 품질 잡이 답변 생성과 채점의 근거로 쓴다.
 */
public record QuestionRunResult(
    EvalReport.Question question,
    QuestionOutcome byVectorRank,
    QuestionOutcome byFusionRank,
    QuestionOutcome byChainOutput,
    QuestionOutcome byMergedRank,
    List<SearchResult> refined
) {}
