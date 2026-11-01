package com.carent.claims.domain.exception;

/** No claim exists for the requested business reference. Mapped to HTTP 404 in the web adapter only. */
public class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(String claimReference) {
        super("Claim not found: " + claimReference);
    }
}
