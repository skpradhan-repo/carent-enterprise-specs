package com.carent.auth;

import java.util.UUID;

/** Test-only random values, so no secret or password literal is ever committed. */
public final class TestSecrets {

    private TestSecrets() {
    }

    /** 64 characters, above the 32-character minimum from specs/07. */
    public static String jwtSecret() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
    }

    public static String password() {
        return UUID.randomUUID().toString();
    }
}
