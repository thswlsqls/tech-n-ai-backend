package com.tech.n.ai.batch.graph.job;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.tech.n.ai.batch.graph.write.GraphWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * 잡을 시작하기 전에 준비를 순서대로 한다.
 *
 * 먼저 추출 모델 API 키가 있는지 본다. 키 없이 돌리면 문서마다 호출이 전부 실패하면서
 * 노드 0개짜리 실행이 정상 결과처럼 남는다.
 * 키가 있고 --graph.build.reset=true면 그래프 컬렉션을 비운다.
 * 마지막으로 그래프 컬렉션 두 개에 key unique 인덱스를 만든다. 이 인덱스가 있어야 upsert가
 * 동시에 겹쳐도 같은 키의 노드가 둘 생기지 않는다.
 *
 * @PostConstruct로 만들지 않는다. 그러면 잡을 돌리지 않고 컨텍스트만 띄워도 운영 Atlas에
 * 쓰기가 나간다. 인덱스는 실제로 그래프를 채울 때만 손대야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphBuildJobListener implements JobExecutionListener {

    private static final String KEY_FIELD = "key";

    private final MongoTemplate mongoTemplate;

    @Value("${langchain4j.open-ai.chat-model.api-key:}")
    private String apiKey;

    @Value("${graph.build.reset:false}")
    private boolean reset;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "langchain4j.open-ai.chat-model.api-key가 비어 있다. 추출 모델 없이는 그래프를 만들 수 없다.");
        }
        log.info("Chat model API key check passed");

        if (reset) {
            dropGraphCollections();
        }

        createUniqueKeyIndex(GraphWriter.NODE_COLLECTION);
        createUniqueKeyIndex(GraphWriter.EDGE_COLLECTION);
    }

    /**
     * 타입 목록이나 이름 정규화 규칙을 바꾸면, 이미 쌓인 노드·엣지 중에 새 설정으로는 다시 안 나올
     * 것들이 그대로 남는다. upsert는 없는 것을 지우지 않기 때문이다. 그럴 때 비우고 처음부터
     * 다시 채우려고 쓰는 스위치다.
     *
     * 지우는 대상은 그래프 컬렉션 두 개로 못 박는다. emerging_techs 같은 원본은 어떤 값을 줘도
     * 지워지지 않아야 한다.
     */
    private void dropGraphCollections() {
        mongoTemplate.getCollection(GraphWriter.NODE_COLLECTION).drop();
        mongoTemplate.getCollection(GraphWriter.EDGE_COLLECTION).drop();
        log.warn("graph.build.reset=true — 그래프 컬렉션을 비웠다: {}, {}",
            GraphWriter.NODE_COLLECTION, GraphWriter.EDGE_COLLECTION);
    }

    private void createUniqueKeyIndex(String collectionName) {
        mongoTemplate.getCollection(collectionName)
            .createIndex(Indexes.ascending(KEY_FIELD), new IndexOptions().unique(true));
        log.info("unique index ready: collection={}, field={}", collectionName, KEY_FIELD);
    }
}
