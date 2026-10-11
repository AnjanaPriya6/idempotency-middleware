package com.idempotency.idempotency_middleware.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idempotency.idempotency_middleware.model.IdempotencyRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public boolean setInProgress(String key) {
        IdempotencyRecord record = new IdempotencyRecord("IN_PROGRESS");
        try {
            String json = objectMapper.writeValueAsString(record);
            Boolean set = redisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + key, json, TTL);
            return Boolean.TRUE.equals(set);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set idempotency key", e);
        }
    }

    public Optional<IdempotencyRecord> get(String key) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + key);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, IdempotencyRecord.class));
        } catch (Exception e) {
            throw new RuntimeException("Failed to get idempotency record", e);
        }
    }

    public void markCompleted(String key, int httpStatus, String responseBody) {
        try {
            IdempotencyRecord record = new IdempotencyRecord("COMPLETED");
            record.setHttpStatus(httpStatus);
            record.setResponseBody(responseBody);
            String json = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(KEY_PREFIX + key, json, TTL);
        } catch (Exception e) {
            throw new RuntimeException("Failed to mark idempotency key completed", e);
        }
    }
}