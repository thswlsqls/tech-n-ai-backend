package com.tech.n.ai.api.chatbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CacheServiceImpl 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CacheServiceImpl 단위 테스트")
class CacheServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CacheServiceImpl cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new CacheServiceImpl(redisTemplate);
        ReflectionTestUtils.setField(cacheService, "cacheEnabled", true);
        ReflectionTestUtils.setField(cacheService, "ttlHours", 1);
    }

    // ========== get 테스트 ==========

    @Nested
    @DisplayName("get")
    class Get {

        @Test
        @DisplayName("캐시 히트 - 값 반환")
        void get_캐시히트() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("chatbot:cache:test-key")).thenReturn("cached-value");

            // When
            String result = cacheService.get("test-key", String.class);

            // Then
            assertThat(result).isEqualTo("cached-value");
        }

        @Test
        @DisplayName("캐시 미스 - null 반환")
        void get_캐시미스() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("chatbot:cache:test-key")).thenReturn(null);

            // When
            String result = cacheService.get("test-key", String.class);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("타입 불일치 - null 반환")
        void get_타입불일치() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("chatbot:cache:test-key")).thenReturn(123);

            // When
            String result = cacheService.get("test-key", String.class);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("캐시 비활성화 시 null 반환")
        void get_캐시비활성화() {
            // Given
            ReflectionTestUtils.setField(cacheService, "cacheEnabled", false);

            // When
            String result = cacheService.get("test-key", String.class);

            // Then
            assertThat(result).isNull();
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("Redis 예외 시 null 반환")
        void get_예외() {
            // Given
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis 연결 실패"));

            // When
            String result = cacheService.get("test-key", String.class);

            // Then
            assertThat(result).isNull();
        }
    }

    // ========== put 테스트 ==========

    @Nested
    @DisplayName("put")
    class Put {

        @Test
        @DisplayName("TTL 지정 저장")
        void put_TTL지정() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            Duration ttl = Duration.ofMinutes(30);

            // When
            cacheService.put("test-key", "value", ttl);

            // Then
            verify(valueOperations).set("chatbot:cache:test-key", "value", ttl);
        }

        @Test
        @DisplayName("기본 TTL 저장")
        void put_기본TTL() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // When
            cacheService.put("test-key", "value");

            // Then
            verify(valueOperations).set("chatbot:cache:test-key", "value", Duration.ofHours(1));
        }

        @Test
        @DisplayName("캐시 비활성화 시 저장하지 않음")
        void put_캐시비활성화() {
            // Given
            ReflectionTestUtils.setField(cacheService, "cacheEnabled", false);

            // When
            cacheService.put("test-key", "value");

            // Then
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("Redis 예외 시 무시")
        void put_예외() {
            // Given
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis 연결 실패"));

            // When - 예외 전파 안함
            cacheService.put("test-key", "value", Duration.ofMinutes(30));

            // Then
            verify(redisTemplate).opsForValue();
        }
    }

    // ========== delete 테스트 ==========

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("정상 삭제")
        void delete_정상() {
            // When
            cacheService.delete("test-key");

            // Then
            verify(redisTemplate).delete("chatbot:cache:test-key");
        }

        @Test
        @DisplayName("캐시 비활성화 시 삭제하지 않음")
        void delete_캐시비활성화() {
            // Given
            ReflectionTestUtils.setField(cacheService, "cacheEnabled", false);

            // When
            cacheService.delete("test-key");

            // Then
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("Redis 예외 시 무시")
        void delete_예외() {
            // Given
            when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("Redis 연결 실패"));

            // When - 예외 전파 안함
            cacheService.delete("test-key");

            // Then
            verify(redisTemplate).delete("chatbot:cache:test-key");
        }
    }
}
