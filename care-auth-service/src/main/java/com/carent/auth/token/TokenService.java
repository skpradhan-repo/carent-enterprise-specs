package com.carent.auth.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Issues HS256 JWTs with {@code sub} and a {@code roles} array (specs/07). Framework-free. */
public class TokenService {

    static final int MIN_SECRET_LENGTH = 32;

    private final MACSigner signer;
    private final String issuer;
    private final Duration lifetime;
    private final Clock clock;

    public TokenService(String secret, String issuer, Duration lifetime, Clock clock) {
        // Boot leaves "${JWT_SECRET}" in place when the variable is unset; that must not count as a secret
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalArgumentException("JWT secret is required (set JWT_SECRET)");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("JWT secret must be at least " + MIN_SECRET_LENGTH + " characters");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer is required");
        }
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("JWT lifetime must be positive");
        }
        try {
            this.signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
        } catch (JOSEException e) {
            throw new IllegalArgumentException("JWT secret cannot be used as an HS256 key");
        }
        this.issuer = issuer;
        this.lifetime = lifetime;
        this.clock = clock;
    }

    public IssuedToken issue(String subject, List<String> roles) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("at least one role is required");
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(lifetime);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .claim("roles", List.copyOf(roles))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return new IssuedToken(jwt.serialize(), expiresAt, lifetime.toSeconds());
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign token");
        }
    }
}
