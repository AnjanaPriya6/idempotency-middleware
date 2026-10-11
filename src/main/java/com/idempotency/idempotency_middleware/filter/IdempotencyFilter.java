package com.idempotency.idempotency_middleware.filter;

import com.idempotency.idempotency_middleware.model.IdempotencyRecord;
import com.idempotency.idempotency_middleware.service.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final Set<String> IDEMPOTENT_METHODS = Set.of("POST", "PATCH", "PUT");
    private final IdempotencyService idempotencyService;
    private static final String REPLAYED_HEADER = "X-Idempotency-Replayed";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();
        //get the method. We only fitler for POST, PUT or PATCH methods only
        if (!IDEMPOTENT_METHODS.contains(method)) {
            filterChain.doFilter(request, response);
            return;
        }
        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);
        // Return 400 if header is missing
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"X-Idempotency-Key header is required\"}");
            return;
        }

        // Check if we've seen this key before
        Optional<IdempotencyRecord> existing = idempotencyService.get(idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();

            if ("IN_PROGRESS".equals(record.getStatus())) {
                // Request is still being processed
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Request with this idempotency key is already being processed\"}");
                return;
            }

            if ("COMPLETED".equals(record.getStatus())) {
                log.info("Replaying response for idempotency key: {}", idempotencyKey);
                response.setStatus(record.getHttpStatus());
                response.setContentType("application/json");
                response.setHeader(REPLAYED_HEADER, "true");
                response.getWriter().write(record.getResponseBody());
                return;
            }
        }

        // First time seeing this key, IN_PROGRESS
        //fall back safe
        boolean acquired;
        try {
            acquired = idempotencyService.setInProgress(idempotencyKey);
        } catch (Exception e) {
            log.error("Redis unavailable, failing open for key: {}", idempotencyKey, e);
            filterChain.doFilter(request, response);
            return;
        }
        if (!acquired) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Request with this idempotency key is already being processed\"}");
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(request, wrappedResponse);
        }  finally {
        String responseBody = new String(wrappedResponse.getContentAsByteArray());
        int httpStatus = wrappedResponse.getStatus();
        if (httpStatus >= 200 && httpStatus < 300) {
            idempotencyService.markCompleted(idempotencyKey, httpStatus, responseBody);
            log.info("Stored completed response for idempotency key: {}", idempotencyKey);
        } else {
            idempotencyService.delete(idempotencyKey);
            log.warn("Request failed with status {}, removing idempotency key: {}", httpStatus, idempotencyKey);
        }
        wrappedResponse.copyBodyToResponse();
    }
    }
}
