package com.carent.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carent.auth.TestSecrets;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** specs/07: HS256 JWT with sub and a roles array; secret of at least 32 characters. */
class TokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofHours(1);

    private final String secret = TestSecrets.jwtSecret();
    private final TokenService service = new TokenService(secret, "care-auth-service", TTL, CLOCK);

    private static SignedJWT parse(IssuedToken token) throws ParseException {
        return SignedJWT.parse(token.value());
    }

    @Test
    void token_is_hs256_and_verifies_with_the_shared_secret() throws Exception {
        SignedJWT jwt = parse(service.issue("member1", List.of("ROLE_MEMBER")));

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(jwt.verify(new MACVerifier(secret))).isTrue();
    }

    @Test
    void token_does_not_verify_with_a_different_secret() throws Exception {
        SignedJWT jwt = parse(service.issue("member1", List.of("ROLE_MEMBER")));

        assertThat(jwt.verify(new MACVerifier(TestSecrets.jwtSecret()))).isFalse();
    }

    @Test
    void claims_carry_subject_issuer_times_and_a_roles_array() throws Exception {
        JWTClaimsSet claims = parse(service.issue("adjudicator1",
                List.of("ROLE_CLAIMS_ADJUDICATOR", "ROLE_MEMBER"))).getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo("adjudicator1");
        assertThat(claims.getIssuer()).isEqualTo("care-auth-service");
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plus(TTL));
        assertThat(claims.getStringListClaim("roles")).containsExactly("ROLE_CLAIMS_ADJUDICATOR", "ROLE_MEMBER");
    }

    @Test
    void roles_claim_is_a_json_array_even_for_a_single_role() throws Exception {
        JWTClaimsSet claims = parse(service.issue("member1", List.of("ROLE_MEMBER"))).getJWTClaimsSet();

        assertThat(claims.getClaim("roles")).isInstanceOf(List.class);
    }

    @Test
    void every_token_gets_its_own_id() throws Exception {
        String first = parse(service.issue("member1", List.of("ROLE_MEMBER"))).getJWTClaimsSet().getJWTID();
        String second = parse(service.issue("member1", List.of("ROLE_MEMBER"))).getJWTClaimsSet().getJWTID();

        assertThat(first).isNotBlank().isNotEqualTo(second);
    }

    @Test
    void issued_token_reports_expiry_and_lifetime() {
        IssuedToken token = service.issue("member1", List.of("ROLE_MEMBER"));

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(token.expiresInSeconds()).isEqualTo(3600);
    }

    // ---- fail fast on weak configuration ------------------------------------------------------

    @Test
    void secret_of_exactly_32_characters_is_accepted() {
        new TokenService("a".repeat(32), "care-auth-service", TTL, CLOCK);
    }

    @Test
    void secret_shorter_than_32_characters_is_rejected_without_echoing_it() {
        String shortSecret = "short-secret-31-characters-long";
        assertThat(shortSecret).hasSize(31);

        assertThatThrownBy(() -> new TokenService(shortSecret, "care-auth-service", TTL, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32")
                .hasMessageNotContaining(shortSecret);
    }

    @Test
    void missing_or_blank_secret_is_rejected() {
        assertThatThrownBy(() -> new TokenService(null, "care-auth-service", TTL, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenService(" ".repeat(40), "care-auth-service", TTL, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_unresolved_placeholder_is_treated_as_a_missing_secret() {
        // Spring Boot leaves "${JWT_SECRET}" in place when the variable is unset; 40 chars would pass the length check
        String unresolved = "${JWT_SECRET_NOT_SET_IN_THE_ENVIRONMENT}";

        assertThatThrownBy(() -> new TokenService(unresolved, "care-auth-service", TTL, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void non_positive_lifetime_is_rejected() {
        assertThatThrownBy(() -> new TokenService(secret, "care-auth-service", Duration.ZERO, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenService(secret, "care-auth-service", Duration.ofSeconds(-1), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blank_subject_or_empty_roles_cannot_be_issued() {
        assertThatThrownBy(() -> service.issue(" ", List.of("ROLE_MEMBER")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issue("member1", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
