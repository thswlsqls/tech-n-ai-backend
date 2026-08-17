package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.api.chatbot.service.dto.Intent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IntentClassificationService 단위 테스트
 *
 * 순수 비즈니스 로직 테스트 (외부 의존성 없음)
 */
@DisplayName("IntentClassificationService 단위 테스트")
class IntentClassificationServiceTest {

    private final IntentClassificationService intentService = new IntentClassificationServiceImpl();

    // ========== AGENT_COMMAND 테스트 ==========

    @Nested
    @DisplayName("classifyIntent - AGENT_COMMAND")
    class AgentCommand {

        @Test
        @DisplayName("@agent 프리픽스가 있으면 AGENT_COMMAND 반환")
        void classifyIntent_agent_prefix() {
            // Given
            String input = "@agent AI 트렌드 분석해줘";

            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.AGENT_COMMAND);
        }

        @Test
        @DisplayName("@agent 대소문자 무시")
        void classifyIntent_agent_caseInsensitive() {
            // Given
            String input = "@AGENT 작업 실행";

            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.AGENT_COMMAND);
        }

        @Test
        @DisplayName("@agent가 문장 중간에 있으면 AGENT_COMMAND 아님")
        void classifyIntent_agent_notPrefix() {
            // Given
            String input = "이것은 @agent 테스트입니다";

            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isNotEqualTo(Intent.AGENT_COMMAND);
        }
    }

    // ========== WEB_SEARCH_REQUIRED 테스트 ==========

    @Nested
    @DisplayName("classifyIntent - WEB_SEARCH_REQUIRED")
    class WebSearchRequired {

        @ParameterizedTest
        @DisplayName("웹 검색 키워드만 포함 시 WEB_SEARCH_REQUIRED 반환")
        @ValueSource(strings = {
            "오늘 날씨 어때?",
            "현재 비트코인 시세"
        })
        void classifyIntent_webSearchKeywords(String input) {
            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.WEB_SEARCH_REQUIRED);
        }

        @ParameterizedTest
        @DisplayName("웹 검색 + RAG 키워드 동시 포함 시 RAG 우선")
        @ValueSource(strings = {
            "지금 뉴스 알려줘",
            "최근 AI 트렌드",
            "today's news",
            "latest technology news"
        })
        void classifyIntent_webSearchWithRagKeywords_ragWins(String input) {
            // When
            Intent result = intentService.classifyIntent(input);

            // Then: RAG 키워드가 있으므로 RAG 우선
            assertThat(result).isEqualTo(Intent.RAG_REQUIRED);
        }

        @Test
        @DisplayName("실시간 정보 요청 시 WEB_SEARCH_REQUIRED 반환")
        void classifyIntent_realTimeInfo() {
            // Given: RAG 키워드 없는 순수 실시간 정보 요청
            String input = "실시간 주가 시세";

            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.WEB_SEARCH_REQUIRED);
        }
    }

    // ========== RAG_REQUIRED 테스트 ==========

    @Nested
    @DisplayName("classifyIntent - RAG_REQUIRED")
    class RagRequired {

        @ParameterizedTest
        @DisplayName("RAG 키워드 포함 시 RAG_REQUIRED 반환")
        @ValueSource(strings = {
            "대회 정보",
            "kaggle 대회 목록",
            "codeforces 관련",
            "AI 트렌드 분석",
            "openai 릴리즈"
        })
        void classifyIntent_ragKeywords(String input) {
            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.RAG_REQUIRED);
        }

        @ParameterizedTest
        @DisplayName("질문 형태 입력 시 RAG_REQUIRED 반환")
        @ValueSource(strings = {
            "무엇이 좋을까요?",
            "어떤 기술을 배워야 할까?",
            "이것은 뭔가요?",
            "언제 시작하나요?"
        })
        void classifyIntent_questionPattern(String input) {
            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.RAG_REQUIRED);
        }

        @Test
        @DisplayName("물음표가 있는 질문은 RAG_REQUIRED 반환")
        void classifyIntent_questionMark() {
            // Given
            String input = "이 기술 스택은 어떤가요?";

            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.RAG_REQUIRED);
        }
    }

    // ========== LLM_DIRECT 테스트 ==========

    @Nested
    @DisplayName("classifyIntent - LLM_DIRECT")
    class LlmDirect {

        @ParameterizedTest
        @DisplayName("창작/번역 요청 시 LLM_DIRECT 반환")
        @ValueSource(strings = {
            "이 문장 번역해줘",
            "코드 작성해줘",
            "이 내용 요약해줘",
            "설명해줘"
        })
        void classifyIntent_llmDirectKeywords(String input) {
            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.LLM_DIRECT);
        }

        @Test
        @DisplayName("일반 대화는 LLM_DIRECT 반환")
        void classifyIntent_generalConversation() {
            // Given
            String input = "안녕하세요";

            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.LLM_DIRECT);
        }

        @Test
        @DisplayName("기본값은 LLM_DIRECT")
        void classifyIntent_default() {
            // Given
            String input = "그냥 테스트 메시지";

            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.LLM_DIRECT);
        }
    }

    // ========== 우선순위 테스트 ==========

    @Nested
    @DisplayName("classifyIntent - 우선순위")
    class Priority {

        @Test
        @DisplayName("@agent가 최우선순위")
        void classifyIntent_agentHighestPriority() {
            // Given: @agent + 웹검색 키워드
            String input = "@agent 오늘 날씨 검색해줘";

            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.AGENT_COMMAND);
        }

        @Test
        @DisplayName("RAG가 웹 검색보다 우선")
        void classifyIntent_ragOverWebSearch() {
            // Given: 웹검색 키워드 + RAG 키워드
            String input = "오늘 kaggle 대회 뉴스";

            // When
            Intent result = intentService.classifyIntent(input);

            // Then: RAG 키워드(kaggle, 대회, 뉴스)가 있으므로 RAG 우선
            assertThat(result).isEqualTo(Intent.RAG_REQUIRED);
        }
    }

    // ========== 엣지 케이스 테스트 ==========

    @Nested
    @DisplayName("classifyIntent - 엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("빈 문자열 처리")
        void classifyIntent_emptyString() {
            // Given
            String input = "";

            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.LLM_DIRECT);
        }

        @Test
        @DisplayName("공백만 있는 입력 처리")
        void classifyIntent_whitespaceOnly() {
            // Given
            String input = "   ";

            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.LLM_DIRECT);
        }

        @Test
        @DisplayName("대소문자 혼합 키워드")
        void classifyIntent_mixedCase() {
            // Given
            String input = "KAGGLE 대회 정보";

            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(Intent.RAG_REQUIRED);
        }
    }

    // ========== 기준 표 25건 (docs/plans/20260812124047/04-intent-baseline.md) ==========

    @Nested
    @DisplayName("classifyIntent - 기준 표 25건")
    class IntentBaselineTable {

        @ParameterizedTest
        @DisplayName("영어 키워드를 단어 경계로 바꾸기 전에도 의도와 맞던 15건은 그대로다")
        @CsvSource(delimiter = '|', value = {
            "OpenAI Daybreak 사이버 보안 도구 소식을 알려줘                                                     | RAG_REQUIRED",
            "avatarin이 리테일 에이전트를 만든 사례를 다룬 블로그 포스트를 알려줘                                | RAG_REQUIRED",
            "OpenAI의 Codex Security 미리보기와 Anthropic이 방어자에게 사이버 보안 기능을 공개한 소식을 비교해줘 | RAG_REQUIRED",
            "OpenAI의 최신 모델 출시 소식을 알려줘                                                              | RAG_REQUIRED",
            "Anthropic Claude Code에서 프롬프트 캐시를 1시간으로 늘리는 환경 변수 이름이 뭔가요?                 | RAG_REQUIRED",
            "지금 서울 날씨 어때?                                                                               | WEB_SEARCH_REQUIRED",
            "이 문단을 영어로 번역해줘                                                                          | LLM_DIRECT",
            "회의록 초안 작성해줘                                                                               | LLM_DIRECT",
            "아래 글을 세 줄로 요약해줘                                                                         | LLM_DIRECT",
            "안녕하세요                                                                                         | LLM_DIRECT",
            "반갑습니다                                                                                         | LLM_DIRECT",
            "@agent 최근 AI 릴리즈 통계 내줘                                                                    | AGENT_COMMAND",
            "@agent 이번 달 수집 건수 차트로 보여줘                                                             | AGENT_COMMAND",
            "OpenAI의 새 모델 정보 알려줘                                                                       | RAG_REQUIRED",
            "이 로그 메시지가 무슨 뜻인지 설명해줘                                                              | LLM_DIRECT"
        })
        void classifyIntent_unchangedCases(String input, Intent expected) {
            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest
        @DisplayName("영어 키워드를 단어 경계로 보면 ai가 explain·email·available·plain에 걸리지 않아 RAG_REQUIRED에서 LLM_DIRECT로 바로잡힌다")
        @CsvSource(delimiter = '|', value = {
            "Explain the difference between a stack and a queue | LLM_DIRECT",
            "Write a polite email to my manager about the schedule | LLM_DIRECT",
            "이 코드에서 available 옵션이 뭔지 설명해줘 | LLM_DIRECT",
            "Please summarize this article in plain English | LLM_DIRECT"
        })
        void classifyIntent_fixedByWordBoundary(String input, Intent expected) {
            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest
        @DisplayName("아직 의도와 어긋난 채 남은 6건 — 원인 B·C는 이번 범위 밖이라 남겼다 (04-intent-baseline.md 참고)")
        @CsvSource(delimiter = '|', value = {
            "오늘 최신 AI 뉴스 알려줘             | RAG_REQUIRED",
            "현재 애플 주가 알려줘                | RAG_REQUIRED",
            "오늘 환율 정보 찾아줘                | RAG_REQUIRED",
            "실시간으로 지금 인터넷에서 검색해줘  | RAG_REQUIRED",
            "hi, how are you?                     | RAG_REQUIRED",
            "점심 뭐 먹을까?                      | RAG_REQUIRED"
        })
        void classifyIntent_stillMismatchedCases(String input, Intent expected) {
            // When
            Intent result = intentService.classifyIntent(input);

            // Then
            assertThat(result).isEqualTo(expected);
        }
    }
}
