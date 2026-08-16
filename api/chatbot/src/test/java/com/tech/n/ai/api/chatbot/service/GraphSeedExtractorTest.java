package com.tech.n.ai.api.chatbot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphSeedExtractor 단위 테스트
 *
 * 골든셋의 다중 홉 질문을 그대로 넣어, 그래프 구축 배치가 저장한 키와 같은 문자열이 나오는지 본다.
 */
@DisplayName("GraphSeedExtractor 단위 테스트")
class GraphSeedExtractorTest {

    @Nested
    @DisplayName("extract - 골든셋 다중 홉 질문")
    class GoldensetQuestions {

        @Test
        @DisplayName("G036 - 회사 두 곳의 키와 버전 번호 리터럴을 뽑는다")
        void g036_extractsCompaniesAndVersions() {
            // Given: 골든셋 G036 원문
            String question = "OpenAI 파이썬 SDK v2.26.0과 Anthropic SDK v0.95.0 릴리스를 비교해줘";

            // When
            GraphSeedExtractor.Seeds seeds = GraphSeedExtractor.extract(question);

            // Then: 조사가 붙은 "v2.26.0과"에서 조사를 떼어 회사 키가 만들어진다
            assertThat(seeds.candidateKeys())
                .contains("Company|openai", "Company|anthropic");
            assertThat(seeds.nameLiterals())
                .containsExactly("v2.26.0", "v0.95.0");
        }

        @Test
        @DisplayName("G035 - 회사 키와 두 단어 모델 이름 키를 뽑는다")
        void g035_extractsCompanyAndModelName() {
            // Given: 골든셋 G035 원문
            String question = "Google의 Lyria 3 음악 생성 모델과 관련 제품 소식을 함께 알려줘";

            // When
            GraphSeedExtractor.Seeds seeds = GraphSeedExtractor.extract(question);

            // Then
            assertThat(seeds.candidateKeys())
                .contains("Company|google", "Model|lyria 3");
            // "3"은 네 글자가 안 돼 부분 일치 리터럴로 쓰지 않는다
            assertThat(seeds.nameLiterals()).isEmpty();
        }
    }

    @Nested
    @DisplayName("extract - 토큰 다듬기")
    class Tokenizing {

        @Test
        @DisplayName("영문·숫자 토큰 뒤의 한글 조사를 떼어낸다")
        void stripsKoreanParticleFromAsciiToken() {
            // Given
            String question = "google의 모델과 v2.26.0과";

            // When
            GraphSeedExtractor.Seeds seeds = GraphSeedExtractor.extract(question);

            // Then: 한글뿐인 "모델과"는 그대로 두고, 영문·숫자가 섞인 토큰만 자른다
            assertThat(seeds.candidateKeys())
                .contains("Company|google", "Company|모델과", "Company|v2.26.0")
                .doesNotContain("Company|google의", "Company|v2.26.0과");
        }

        @Test
        @DisplayName("토큰 끝의 문장부호를 떼어낸다")
        void stripsTrailingPunctuation() {
            // Given
            String question = "gpt-4o는 어때?";

            // When
            GraphSeedExtractor.Seeds seeds = GraphSeedExtractor.extract(question);

            // Then
            assertThat(seeds.candidateKeys()).contains("Model|gpt-4o");
        }

        @Test
        @DisplayName("빈 질문이면 후보가 하나도 없다")
        void blankQuestion_returnsEmptySeeds() {
            // Given
            String question = "   ";

            // When
            GraphSeedExtractor.Seeds seeds = GraphSeedExtractor.extract(question);

            // Then
            assertThat(seeds.candidateKeys()).isEmpty();
            assertThat(seeds.nameLiterals()).isEmpty();
        }
    }

    @Nested
    @DisplayName("extract - 상한")
    class Limits {

        @Test
        @DisplayName("후보 키는 300개를 넘지 않는다")
        void candidateKeysAreCapped() {
            // Given: 단어 40개짜리 긴 질문
            StringBuilder question = new StringBuilder();
            for (int i = 0; i < 40; i++) {
                question.append("word").append(i).append(' ');
            }

            // When
            GraphSeedExtractor.Seeds seeds = GraphSeedExtractor.extract(question.toString());

            // Then
            assertThat(seeds.candidateKeys()).hasSize(300);
        }

        @Test
        @DisplayName("부분 일치 리터럴은 5개를 넘지 않는다")
        void nameLiteralsAreCapped() {
            // Given: 버전처럼 생긴 토큰 7개
            String question = "v1.0.0 v2.0.0 v3.0.0 v4.0.0 v5.0.0 v6.0.0 v7.0.0";

            // When
            GraphSeedExtractor.Seeds seeds = GraphSeedExtractor.extract(question);

            // Then
            assertThat(seeds.nameLiterals()).hasSize(5);
        }
    }
}
