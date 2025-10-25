package com.devcool.adapters.web.dto.wrapper;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class ApiErrorResponse {
    private String code;              // e.g. USR_409_EMAIL
    private String message;           // e.g. "Email already in use"
    private Map<String, Object> details; // additional data (e.g., which email)
    private Instant timestamp;        // when the error occurred
    private int status;               // HTTP status code
}
