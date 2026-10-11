package com.idempotency.idempotency_middleware.controller;

import com.idempotency.idempotency_middleware.model.PaymentRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @PostMapping
    public ResponseEntity<Map<String, Object>> createPayment(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(Map.of(
                "paymentId", UUID.randomUUID().toString(),
                "status", "SUCCESS",
                "amount", request.getAmount()
        ));
    }
}
