package com.tech.n.ai.batch.graph.config;

import com.tech.n.ai.domain.mongodb.config.MongoClientConfig;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 배치 기본 설정
 *
 * 실행 이력을 남기지 않는다. @EnableBatchProcessing 기본값인 ResourcelessJobRepository를 쓰고
 * JDBC JobRepository는 붙이지 않는다. 실행 기록은 타임스탬프가 붙은 리포트가 대신한다.
 *
 * MongoTemplate은 MongoClientConfig에서 가져온다. 이 설정을 빼면 Boot 기본 MongoTemplate이
 * 대신 뜨면서 WriteConcern.MAJORITY가 빠진다.
 * 같은 패키지의 MongoIndexConfig와 VectorSearchIndexConfig는 일부러 올리지 않는다. 두 설정은
 * @PostConstruct에서 운영 Atlas의 인덱스를 다시 만드는데, 이 배치가 손댈 대상은 그래프 컬렉션
 * 두 개뿐이다. 그래프 컬렉션의 unique 인덱스는 GraphBuildJobListener가 따로 만든다.
 */
@Configuration
@EnableBatchProcessing
@Import(MongoClientConfig.class)
public class BatchGraphConfig {

}
