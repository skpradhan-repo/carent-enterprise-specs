package com.carent.claims.domain.exception;

/** Forbidden status transition. Mapped to HTTP 409 in the web adapter only. */
public class IllegalClaimStateException extends RuntimeException {

    public IllegalClaimStateException(String message) {
        super(message);
    }
}
