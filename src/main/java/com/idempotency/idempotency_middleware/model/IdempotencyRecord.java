package com.idempotency.idempotency_middleware.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {
    private String status;
    private int httpStatus;
    private String responseBody;

    public IdempotencyRecord(String status) {
        this.status = status;
    }
}