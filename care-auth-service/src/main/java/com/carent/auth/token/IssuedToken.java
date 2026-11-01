package com.carent.auth.token;

import java.time.Instant;

/** A signed token plus when it stops being valid. {@code toString()} hides the token itself. */
public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {

    @Override
    public String toString() {
        return "IssuedToken[expiresAt=" + expiresAt + "]";
    }
}
