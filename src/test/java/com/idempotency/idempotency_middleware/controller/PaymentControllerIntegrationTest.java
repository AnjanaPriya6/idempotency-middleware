package com.idempotency.idempotency_middleware.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturn400_whenIdempotencyKeyIsMissing() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400_whenAmountIsInvalid() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "int-validation-key")
                        .content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn200_whenValidRequest() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "int-valid-key-001")
                        .content("{\"amount\": 1000}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReplayResponse_whenDuplicateRequest() throws Exception {
        String key = "int-duplicate-key-001";
        String body = "{\"amount\": 1000}";

        MvcResult first = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", key)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", key)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());

        assertThat(second.getResponse().getHeader("X-Idempotency-Replayed"))
                .isEqualTo("true");
    }
}