package com.carent.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Nothing listens downstream: the gateway must answer with RFC 7807 and leak no internals (specs/07). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "gateway.security.enabled=false")
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GatewayUpstreamDownIT {

    private static final int DEAD_PORT = freePort();

    @DynamicPropertySource
    static void deadDownstream(DynamicPropertyRegistry registry) {
        registry.add("CLAIMS_SERVICE_URI", () -> "http://127.0.0.1:" + DEAD_PORT);
        registry.add("AUTH_SERVICE_URI", () -> "http://127.0.0.1:" + DEAD_PORT);
    }

    private final WebTestClient client;

    GatewayUpstreamDownIT(WebTestClient client) {
        this.client = client;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void connection_refused_becomes_a_502_problem_detail_without_internal_text() {
        byte[] body = client.get().uri("/api/v1/claims/CLM-20261031-891023").exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(502)
                .jsonPath("$.title").isEqualTo("Bad Gateway")
                .jsonPath("$.detail").isEqualTo("The upstream service is unavailable")
                .returnResult().getResponseBody();

        assertThat(new String(body)).doesNotContain("127.0.0.1").doesNotContain("refused")
                .doesNotContain(String.valueOf(DEAD_PORT));
    }
}