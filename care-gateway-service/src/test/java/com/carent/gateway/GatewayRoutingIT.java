package com.carent.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

/** specs/05 routes, with JWT validation switched off (the local happy-path demo mode from specs/07). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "gateway.security.enabled=false")
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GatewayRoutingIT {

    private static final StubServer CLAIMS = StubServer.start("claims");
    private static final StubServer AUTH = StubServer.start("auth");

    private static final String SUBMISSION = """
            {"memberId":"MEM-99201","providerId":"PRV-44102","serviceDate":"2026-10-25",
             "billedAmount":"150.00","benefitCategory":"IN_NETWORK_PRIMARY_CARE"}""";

    @DynamicPropertySource
    static void downstreams(DynamicPropertyRegistry registry) {
        registry.add("CLAIMS_SERVICE_URI", CLAIMS::uri);
        registry.add("AUTH_SERVICE_URI", AUTH::uri);
    }

    @AfterAll
    static void stopStubs() {
        CLAIMS.close();
        AUTH.close();
    }

    private final WebTestClient client;

    GatewayRoutingIT(WebTestClient client) {
        this.client = client;
    }

    @BeforeEach
    void resetStubs() {
        CLAIMS.reset();
        AUTH.reset();
    }

    @Test
    void post_claims_reaches_the_claims_service_with_method_path_and_body_intact() {
        client.post().uri("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).bodyValue(SUBMISSION)
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.stub").isEqualTo("claims");

        assertThat(CLAIMS.received()).hasSize(1);
        StubServer.Received request = CLAIMS.received().get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/api/v1/claims");
        assertThat(request.body()).isEqualTo(SUBMISSION);
        assertThat(AUTH.received()).isEmpty();
    }

    @Test
    void get_claim_by_reference_reaches_the_claims_service() {
        client.get().uri("/api/v1/claims/CLM-20261031-891023").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/api/v1/claims/CLM-20261031-891023");

        assertThat(CLAIMS.received()).extracting(StubServer.Received::path)
                .containsExactly("/api/v1/claims/CLM-20261031-891023");
    }

    @Test
    void auth_token_endpoint_reaches_the_auth_service_not_the_claims_service() {
        client.post().uri("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"member1\",\"password\":\"x\"}")
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.stub").isEqualTo("auth");

        assertThat(AUTH.received()).hasSize(1);
        assertThat(CLAIMS.received()).isEmpty();
    }

    @Test
    void downstream_status_codes_are_passed_through_unchanged() {
        CLAIMS.failNext(1);

        client.post().uri("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).bodyValue(SUBMISSION)
                .exchange().expectStatus().isEqualTo(503);
    }

    @Test
    void a_failed_get_is_retried_and_the_caller_sees_the_eventual_success() {
        CLAIMS.failNext(1);

        client.get().uri("/api/v1/claims/CLM-20261031-891023").exchange().expectStatus().isOk();

        assertThat(CLAIMS.received()).hasSize(2);
    }

    @Test
    void a_failed_post_is_never_retried_because_that_could_submit_the_claim_twice() {
        CLAIMS.failNext(1);

        client.post().uri("/api/v1/claims").contentType(MediaType.APPLICATION_JSON).bodyValue(SUBMISSION)
                .exchange().expectStatus().isEqualTo(503);

        assertThat(CLAIMS.received()).hasSize(1);
    }

    @Test
    void unknown_paths_get_a_problem_detail_404_and_reach_nobody() {
        client.get().uri("/api/v1/unknown").exchange().expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.title").isEqualTo("Not Found")
                .jsonPath("$.instance").isEqualTo("/api/v1/unknown");

        assertThat(CLAIMS.received()).isEmpty();
        assertThat(AUTH.received()).isEmpty();
    }

    @Test
    void actuator_health_is_up() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
