package com.carent.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Test-only token factory and random secrets, so no secret literal is ever committed. */
final class TestTokens {

    static final String ISSUER = "care-auth-service";

    private TestTokens() {
    }

    /** 64 characters, above the 32-character minimum from specs/07. */
    static String randomSecret() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
    }

    static String signed(String secret, JWSAlgorithm algorithm, String issuer, String subject,
                         List<String> roles, Instant expiresAt) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(issuer)
                    .issueTime(Date.from(Instant.now().minusSeconds(10)))
                    .expirationTime(Date.from(expiresAt))
                    .claim("roles", roles)
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    static String valid(String secret, String subject, String... roles) {
        return signed(secret, JWSAlgorithm.HS256, ISSUER, subject, List.of(roles), Instant.now().plusSeconds(600));
    }

    static String unsigned(String subject, String... roles) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(subject).issuer(ISSUER)
                .expirationTime(Date.from(Instant.now().plusSeconds(600))).claim("roles", List.of(roles)).build();
        return new PlainJWT(claims).serialize();
    }
}
