package com.paytm.wallet.dto;

import java.time.Instant;
import java.util.List;

/**
 * Standard error contract for every non-2xx response. Nulls are omitted on the wire
 * (Jackson default-property-inclusion=non_null).
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String path,
        String traceId,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}
