package com.carent.claims.domain.exception;

/** Validation failure (amount, date, format, invariant). Mapped to HTTP 400 in the web adapter only. */
public class InvalidClaimException extends RuntimeException {

    public InvalidClaimException(String message) {
        super(message);
    }
}
