package com.carent.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

/** specs/07: with validation on, /api/v1/claims/** needs a valid HS256 token; the auth endpoint stays open. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "gateway.security.enabled=true")
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GatewaySecurityIT {

    private static final String SECRET = TestTokens.randomSecret();
    private static final StubServer CLAIMS = StubServer.start("claims");
    private static final StubServer AUTH = StubServer.start("auth");

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> SECRET);
        registry.add("CLAIMS_SERVICE_URI", CLAIMS::uri);
        registry.add("AUTH_SERVICE_URI", AUTH::uri);
    }

    @AfterAll
    static void stopStubs() {
        CLAIMS.close();
        AUTH.close();
    }

    private final WebTestClient client;

    GatewaySecurityIT(WebTestClient client) {
        this.client = client;
    }

    @BeforeEach
    void resetStubs() {
        CLAIMS.reset();
        AUTH.reset();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private WebTestClient.ResponseSpec getClaim(String authorization) {
        WebTestClient.RequestHeadersSpec<?> request = client.get().uri("/api/v1/claims/CLM-20261031-891023");
        if (authorization != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request.exchange();
    }

    private void assertRejectedAs401(WebTestClient.ResponseSpec response) {
        response.expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.title").isEqualTo("Unauthorized")
                .jsonPath("$.detail").isEqualTo("Authentication is required to access this resource")
                .jsonPath("$.instance").isEqualTo("/api/v1/claims/CLM-20261031-891023");
        assertThat(CLAIMS.received()).as("a rejected request must never reach the claims service").isEmpty();
    }

    @Test
    void valid_token_is_accepted_and_forwarded_to_the_claims_service() {
        String token = TestTokens.valid(SECRET, "member1", "ROLE_MEMBER");

        getClaim(bearer(token)).expectStatus().isOk();

        assertThat(CLAIMS.received()).hasSize(1);
        assertThat(CLAIMS.received().get(0).authorization()).isEqualTo(bearer(token));
    }

    @Test
    void post_with_a_valid_token_reaches_the_claims_service() {
        client.post().uri("/api/v1/claims").header(HttpHeaders.AUTHORIZATION,
                        bearer(TestTokens.valid(SECRET, "adjudicator1", "ROLE_CLAIMS_ADJUDICATOR")))
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isCreated();

        assertThat(CLAIMS.received()).hasSize(1);
    }

    @Test
    void no_token_is_rejected_with_a_problem_detail() {
        assertRejectedAs401(getClaim(null));
    }

    @Test
    void post_without_a_token_is_rejected_and_never_forwarded() {
        client.post().uri("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isUnauthorized();

        assertThat(CLAIMS.received()).isEmpty();
    }

    @Test
    void garbage_token_is_rejected() {
        assertRejectedAs401(getClaim(bearer("not.a.jwt")));
    }

    @Test
    void non_bearer_scheme_is_rejected() {
        assertRejectedAs401(getClaim("Basic dXNlcjpwYXNz"));
    }

    @Test
    void token_signed_with_another_secret_is_rejected() {
        assertRejectedAs401(getClaim(bearer(TestTokens.valid(TestTokens.randomSecret(), "member1", "ROLE_MEMBER"))));
    }

    @Test
    void expired_token_is_rejected() {
        String expired = TestTokens.signed(SECRET, JWSAlgorithm.HS256, TestTokens.ISSUER, "member1",
                List.of("ROLE_MEMBER"), Instant.now().minusSeconds(3600));

        assertRejectedAs401(getClaim(bearer(expired)));
    }

    @Test
    void token_from_a_different_issuer_is_rejected() {
        String foreign = TestTokens.signed(SECRET, JWSAlgorithm.HS256, "someone-else", "member1",
                List.of("ROLE_MEMBER"), Instant.now().plusSeconds(600));

        assertRejectedAs401(getClaim(bearer(foreign)));
    }

    @Test
    void unsigned_alg_none_token_is_rejected() {
        assertRejectedAs401(getClaim(bearer(TestTokens.unsigned("admin1", "ROLE_ADMIN"))));
    }

    @Test
    void token_using_another_hmac_algorithm_is_rejected() {
        String hs512 = TestTokens.signed(SECRET, JWSAlgorithm.HS512, TestTokens.ISSUER, "member1",
                List.of("ROLE_MEMBER"), Instant.now().plusSeconds(600));

        assertRejectedAs401(getClaim(bearer(hs512)));
    }

    @Test
    void the_401_never_echoes_the_token() {
        String token = "not.a.jwt";
        byte[] body = getClaim(bearer(token)).returnResult(byte[].class).getResponseBodyContent();

        assertThat(new String(body)).doesNotContain(token).doesNotContain("Bearer");
    }

    @Test
    void the_auth_endpoint_is_reachable_without_a_token() {
        client.post().uri("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isCreated();

        assertThat(AUTH.received()).hasSize(1);
    }

    @Test
    void actuator_health_is_reachable_without_a_token() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void unknown_paths_need_a_token_too_so_the_gateway_does_not_reveal_which_routes_exist() {
        client.get().uri("/api/v1/unknown").exchange().expectStatus().isUnauthorized();
    }
}