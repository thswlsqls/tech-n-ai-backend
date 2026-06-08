package com.tech.n.ai.common.kafka.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    
    private static final String PROCESSED_EVENT_PREFIX = "processed_event:";
    private static final Duration PROCESSED_EVENT_TTL = Duration.ofDays(7);
    
    private final RedisTemplate<String, String> redisTemplate;
    
    public boolean isEventProcessed(String eventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(keyFor(eventId)));
    }

    public void markEventAsProcessed(String eventId) {
        redisTemplate.opsForValue().set(keyFor(eventId), "processed", PROCESSED_EVENT_TTL);
    }

    private String keyFor(String eventId) {
        return PROCESSED_EVENT_PREFIX + eventId;
    }
}
