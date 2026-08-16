package com.tech.n.ai.batch.eval.config;

import com.tech.n.ai.api.chatbot.chain.AnswerGenerationChain;
import com.tech.n.ai.api.chatbot.chain.InputInterpretationChain;
import com.tech.n.ai.api.chatbot.chain.ResultRefinementChain;
import com.tech.n.ai.api.chatbot.config.LangChain4jConfig;
import com.tech.n.ai.api.chatbot.service.CohereReRankingServiceImpl;
import com.tech.n.ai.api.chatbot.service.IntentClassificationServiceImpl;
import com.tech.n.ai.api.chatbot.service.LLMServiceImpl;
import com.tech.n.ai.api.chatbot.service.PromptServiceImpl;
import com.tech.n.ai.api.chatbot.service.SearchOptionsFactory;
import com.tech.n.ai.api.chatbot.service.TokenServiceImpl;
import com.tech.n.ai.api.chatbot.service.VectorSearchServiceImpl;
import com.tech.n.ai.domain.mongodb.config.MongoClientConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 평가 잡이 운영 챗봇과 같은 검색 경로를 타도록 필요한 빈만 골라 등록한다.
 *
 * api-chatbot 패키지를 통째로 스캔하지 않는다. 스케줄러·컨트롤러·Kafka 컨슈머까지 함께 뜨고
 * Aurora 쪽 빈은 프로필 가드가 없어 DataSource 없이 죽기 때문이다.
 * MongoIndexConfig와 VectorSearchIndexConfig도 뺀다. @PostConstruct가 운영 Atlas의
 * 인덱스를 갱신하는데, 평가는 읽기만 해야 한다.
 */
@Configuration
@ComponentScan("com.tech.n.ai.batch.eval")
@Import({
    LangChain4jConfig.class,
    MongoClientConfig.class,
    VectorSearchServiceImpl.class,
    InputInterpretationChain.class,
    IntentClassificationServiceImpl.class,
    ResultRefinementChain.class,
    CohereReRankingServiceImpl.class,
    TokenServiceImpl.class,
    SearchOptionsFactory.class,
    AnswerGenerationChain.class,
    PromptServiceImpl.class,
    LLMServiceImpl.class
})
public class ChatbotBridgeConfig {

}
