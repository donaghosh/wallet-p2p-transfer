package com.paytm.wallet.constants;

import org.springframework.http.HttpStatus;

/**
 * Central, stable, machine-readable error codes. Clients branch on these, never on
 * the human-readable message.
 */
public enum ErrorCode {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** Same idempotency key replayed with a different request body. */
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    /** from and to refer to the same wallet. */
    SELF_TRANSFER(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
