package com.tech.n.ai.api.agent.config;

import com.tech.n.ai.api.agent.tool.EmergingTechAgentTools;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentPromptConfig 단위 테스트
 *
 * <p>통계 Tool을 어떤 기준으로 부를지 말하는 문장 세 곳(rules 4번, tools 설명, @Tool/@P 설명)이
 * 같은 우선순위를 말하는지 핵심 표현으로 확인한다.
 */
@DisplayName("AgentPromptConfig 단위 테스트")
class AgentPromptConfigTest {

    private static final String ONLY_NAMED_GROUP_BY = "그 기준만 호출";
    private static final String PRIORITY_OVER_AMBIGUOUS = "모호한 요청 규칙보다 우선";

    private AgentPromptConfig config;

    @BeforeEach
    void setUp() {
        config = new AgentPromptConfig();
    }

    @Nested
    @DisplayName("통계 규칙 (rules 4번)")
    class StatisticsRule {

        @Test
        @DisplayName("rules에서 '그 기준만 호출'이 든 줄이 '현황'을 함께 말하고, 모호한 요청 규칙보다 우선한다고 적는다")
        void rules_기준명시_우선규칙() {
            // Given
            String namedGroupByLine = Arrays.stream(config.getRules().split("\n"))
                    .filter(line -> line.contains(ONLY_NAMED_GROUP_BY))
                    .findFirst()
                    .orElseThrow();

            // When & Then
            assertThat(namedGroupByLine)
                    .contains("현황")
                    .contains(PRIORITY_OVER_AMBIGUOUS);
        }

        @Test
        @DisplayName("짧은 기준 요청 예시: 'UpdateType별 통계'는 update_type 1회만, 두 기준 요청은 update_type을 호출하지 않는다")
        void rules_짧은기준요청예시() {
            // Given
            String rules = config.getRules();

            // When & Then
            assertThat(rules)
                    .contains("\"UpdateType별 통계\" → groupBy=update_type 1회만 호출")
                    .contains("\"Provider별과 SourceType별 통계\" → groupBy=provider, groupBy=source_type 각 1회만 호출 (update_type은 호출하지 않음)");
        }

        @Test
        @DisplayName("모호한 요청 예시에 기준 단어나 '수집 현황'이 없다")
        void rules_모호한요청예시_기준단어없음() {
            // Given
            String ambiguousLine = Arrays.stream(config.getRules().split("\n"))
                    .filter(line -> line.contains("모호한 요청(예:"))
                    .findFirst()
                    .orElseThrow();

            // When & Then
            assertThat(ambiguousLine)
                    .contains("전체 통계")
                    .doesNotContain("수집 현황", "Provider", "SourceType", "UpdateType");
        }

        @Test
        @DisplayName("같은 groupBy+기간 조합 중복 호출 금지 문장은 그대로 있다")
        void rules_중복호출금지_유지() {
            assertThat(config.getRules()).contains("동일한 groupBy+startDate+endDate 조합으로 두 번 이상 호출하지 마세요");
        }
    }

    @Nested
    @DisplayName("통계 Tool 설명")
    class StatisticsToolDescription {

        @Test
        @DisplayName("tools 문자열의 get_emerging_tech_statistics 설명이 같은 우선순위를 말한다")
        void tools_통계설명_우선순위() {
            // Given
            String statisticsLine = Arrays.stream(config.getTools().split("\n"))
                    .filter(line -> line.contains("get_emerging_tech_statistics"))
                    .findFirst()
                    .orElseThrow();

            // When & Then
            assertThat(statisticsLine)
                    .contains(ONLY_NAMED_GROUP_BY)
                    .contains(PRIORITY_OVER_AMBIGUOUS);
        }

        @Test
        @DisplayName("@Tool 설명과 groupBy @P 설명이 같은 우선순위를 말한다")
        void getStatistics_어노테이션_우선순위() throws NoSuchMethodException {
            // Given
            Method method = EmergingTechAgentTools.class.getMethod(
                    "getStatistics", String.class, String.class, String.class);

            // When
            String toolDescription = String.join("", method.getAnnotation(Tool.class).value());
            String groupByDescription = method.getParameters()[0].getAnnotation(P.class).value();

            // Then
            assertThat(toolDescription)
                    .contains(ONLY_NAMED_GROUP_BY)
                    .contains(PRIORITY_OVER_AMBIGUOUS);
            assertThat(groupByDescription)
                    .contains(ONLY_NAMED_GROUP_BY)
                    .contains(PRIORITY_OVER_AMBIGUOUS);
        }
    }
}
