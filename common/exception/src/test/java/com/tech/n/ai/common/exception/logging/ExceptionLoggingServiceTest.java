package com.tech.n.ai.common.exception.logging;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExceptionLoggingService 단위 테스트
 *
 * MongoTemplate.save에 넘긴 객체를 운영과 같은 방식으로 만든 MappingMongoConverter로
 * BSON 문서로 바꿔, exception_logs 문서 키가 MongoIndexConfig의 인덱스 필드(snake_case)와 같은지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExceptionLoggingService 단위 테스트")
class ExceptionLoggingServiceTest {

    private static final String COLLECTION = "exception_logs";

    @Mock
    private MongoTemplate mongoTemplate;

    private ExceptionLoggingService service;
    private MappingMongoConverter converter;

    @BeforeEach
    void setUp() {
        service = new ExceptionLoggingService(mongoTemplate);
        converter = createConverter();
    }

    private static MappingMongoConverter createConverter() {
        MongoCustomConversions conversions = new MongoCustomConversions(List.of());
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.afterPropertiesSet();

        MappingMongoConverter mappingConverter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        mappingConverter.setCustomConversions(conversions);
        mappingConverter.afterPropertiesSet();
        return mappingConverter;
    }

    private static ExceptionContext.ContextInfo contextInfo() {
        return new ExceptionContext.ContextInfo(
            "api-bookmark",
            "BookmarkService.create",
            Map.of("id", "1"),
            "/api/v1/bookmark",
            "user-1",
            "request-1"
        );
    }

    private Document savedDocument() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(mongoTemplate).save(captor.capture(), eq(COLLECTION));
        Document document = new Document();
        converter.write(captor.getValue(), document);
        return document;
    }

    @Nested
    @DisplayName("logReadException")
    class LogReadException {

        @Test
        @DisplayName("저장 문서의 최상위 키가 snake_case이고 camelCase 키는 없다")
        void 최상위_키가_snake_case() {
            // Given
            Exception exception = new RuntimeException("read failed");

            // When
            service.logReadException(exception, contextInfo());

            // Then
            Document document = savedDocument();
            assertThat(document).containsKeys(
                "occurred_at", "exception_type", "exception_message", "stack_trace", "source", "severity", "context");
            assertThat(document).doesNotContainKeys("occurredAt", "exceptionType", "exceptionMessage", "stackTrace");
            assertThat(document.get("source")).isEqualTo("READ");
        }

        @Test
        @DisplayName("context 하위 문서의 키가 snake_case이고 camelCase 키는 없다")
        void context_하위_키가_snake_case() {
            // Given
            Exception exception = new RuntimeException("read failed");

            // When
            service.logReadException(exception, contextInfo());

            // Then
            Document context = savedDocument().get("context", Document.class);
            assertThat(context).containsKeys("user_id", "request_id", "request_uri", "module", "method", "parameters");
            assertThat(context).doesNotContainKeys("userId", "requestId", "requestUri");
        }

        @Test
        @DisplayName("occurred_at 값은 TTL 인덱스가 동작하는 Date 타입이다")
        void occurred_at_은_Date() {
            // Given
            Exception exception = new RuntimeException("read failed");

            // When
            service.logReadException(exception, contextInfo());

            // Then
            assertThat(savedDocument().get("occurred_at")).isInstanceOf(Date.class);
        }
    }

    @Nested
    @DisplayName("logWriteException")
    class LogWriteException {

        @Test
        @DisplayName("저장 문서의 최상위 키가 snake_case이고 camelCase 키는 없다")
        void 최상위_키가_snake_case() {
            // Given
            Exception exception = new IllegalStateException("write failed");

            // When
            service.logWriteException(exception, contextInfo());

            // Then
            Document document = savedDocument();
            assertThat(document).containsKeys(
                "occurred_at", "exception_type", "exception_message", "stack_trace", "source", "severity", "context");
            assertThat(document).doesNotContainKeys("occurredAt", "exceptionType", "exceptionMessage", "stackTrace");
            assertThat(document.get("source")).isEqualTo("WRITE");
        }

        @Test
        @DisplayName("save가 예외를 던져도 밖으로 던지지 않는다")
        void save_실패시_예외를_던지지_않는다() {
            // Given
            when(mongoTemplate.save(any(), eq(COLLECTION))).thenThrow(new RuntimeException("mongo down"));

            // When & Then
            assertThatCode(() -> service.logWriteException(new RuntimeException("write failed"), contextInfo()))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("MongoTemplate이 없으면 로컬 로그로 대체하고 예외를 밖으로 던지지 않는다")
        void mongoTemplate_없으면_예외를_던지지_않는다() {
            // Given
            ExceptionLoggingService serviceWithoutMongo = new ExceptionLoggingService(null);

            // When & Then
            assertThatCode(() -> serviceWithoutMongo.logWriteException(new RuntimeException("write failed"), contextInfo()))
                .doesNotThrowAnyException();
        }
    }
}
