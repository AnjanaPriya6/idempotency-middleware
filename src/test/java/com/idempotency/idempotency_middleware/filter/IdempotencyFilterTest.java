package com.idempotency.idempotency_middleware.filter;

import com.idempotency.idempotency_middleware.model.IdempotencyRecord;
import com.idempotency.idempotency_middleware.service.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private IdempotencyFilter idempotencyFilter;

    @Test
    void shouldReturn400_whenIdempotencyKeyIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments");
        MockHttpServletResponse response = new MockHttpServletResponse();

        idempotencyFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(idempotencyService);
    }

    @Test
    void shouldPassThrough_whenMethodIsGet() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payments");
        MockHttpServletResponse response = new MockHttpServletResponse();

        idempotencyFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(idempotencyService);
    }

    @Test
    void shouldReturn409_whenRequestIsInProgress() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments");
        request.addHeader("X-Idempotency-Key", "test-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        IdempotencyRecord inProgress = new IdempotencyRecord("IN_PROGRESS", 0, null);
        when(idempotencyService.get("test-key")).thenReturn(Optional.of(inProgress));

        idempotencyFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
        verifyNoMoreInteractions(filterChain);
    }

    @Test
    void shouldReplayResponse_whenRequestIsCompleted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments");
        request.addHeader("X-Idempotency-Key", "test-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        IdempotencyRecord completed = new IdempotencyRecord("COMPLETED", 200, "{\"status\":\"SUCCESS\"}");
        when(idempotencyService.get("test-key")).thenReturn(Optional.of(completed));

        idempotencyFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-Idempotency-Replayed")).isEqualTo("true");
        assertThat(response.getContentAsString()).isEqualTo("{\"status\":\"SUCCESS\"}");
    }

    @Test
    void shouldProcessRequest_whenKeyIsNew() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments");
        request.addHeader("X-Idempotency-Key", "new-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(idempotencyService.get("new-key")).thenReturn(Optional.empty());
        when(idempotencyService.setInProgress("new-key")).thenReturn(true);

        idempotencyFilter.doFilterInternal(request, response, filterChain);

        verify(idempotencyService).setInProgress("new-key");
        verify(filterChain).doFilter(eq(request), any());
    }
}
