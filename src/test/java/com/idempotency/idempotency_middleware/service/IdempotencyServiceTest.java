package com.idempotency.idempotency_middleware.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idempotency.idempotency_middleware.model.IdempotencyRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotencyService idempotencyService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
    }

    @Test
    void setInProgress_shouldReturnTrue_whenKeyDoesNotExist() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        boolean result = idempotencyService.setInProgress("test-key");

        assertThat(result).isTrue();
    }

    @Test
    void setInProgress_shouldReturnFalse_whenKeyAlreadyExists() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        boolean result = idempotencyService.setInProgress("test-key");

        assertThat(result).isFalse();
    }

    @Test
    void get_shouldReturnEmpty_whenKeyDoesNotExist() {
        when(valueOperations.get(anyString())).thenReturn(null);

        Optional<IdempotencyRecord> result = idempotencyService.get("test-key");

        assertThat(result).isEmpty();
    }

    @Test
    void get_shouldReturnRecord_whenKeyExists() throws Exception {
        IdempotencyRecord record = new IdempotencyRecord("COMPLETED", 200, "{\"status\":\"SUCCESS\"}");
        String json = objectMapper.writeValueAsString(record);
        when(valueOperations.get(anyString())).thenReturn(json);

        Optional<IdempotencyRecord> result = idempotencyService.get("test-key");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("COMPLETED");
        assertThat(result.get().getHttpStatus()).isEqualTo(200);
    }

    @Test
    void markCompleted_shouldStoreCompletedRecord() {
        idempotencyService.markCompleted("test-key", 200, "{\"status\":\"SUCCESS\"}");

        verify(valueOperations).set(eq("idempotency:test-key"), anyString(), any());
    }
}
