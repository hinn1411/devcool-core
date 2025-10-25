package com.devcool.adapters.web.dto.wrapper;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class ApiSuccessResponse<T> {
    private String code;               // success code (e.g., USR_200, USR_201)
    private String message;            // human-readable message
    private T data;                    // actual payload
    private Map<String, Object> meta;  // optional metadata (e.g. pagination)
    private Instant timestamp;         // server time
    private int status;                // HTTP status
}
