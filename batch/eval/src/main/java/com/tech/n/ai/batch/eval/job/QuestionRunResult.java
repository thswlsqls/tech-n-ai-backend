package com.tech.n.ai.batch.eval.job;

import com.tech.n.ai.batch.eval.report.EvalReport;
import com.tech.n.ai.batch.eval.scoring.QuestionOutcome;

/**
 * 질문 한 건 실행 결과
 *
 * 리포트에 그대로 실을 질문 항목과, 세 기준으로 각각 집계에 넘길 결과를 함께 담는다.
 */
public record QuestionRunResult(
    EvalReport.Question question,
    QuestionOutcome byVectorRank,
    QuestionOutcome byFusionRank,
    QuestionOutcome byChainOutput
) {}
