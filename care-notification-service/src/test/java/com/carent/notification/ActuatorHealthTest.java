package com.carent.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestConstructor;

/** The notification service has no public API; actuator health is its only HTTP surface. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ActuatorHealthTest {

    private final TestRestTemplate rest;

    ActuatorHealthTest(TestRestTemplate rest) {
        this.rest = rest;
    }

    @Test
    void health_is_up_and_exposes_no_internal_details() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/actuator/health", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status").asText()).isEqualTo("UP");
        assertThat(response.getBody().has("components")).isFalse();
    }

    @Test
    void only_health_and_info_are_exposed_over_http() {
        for (String endpoint : new String[] {"env", "beans", "mappings", "heapdump", "configprops"}) {
            assertThat(rest.getForEntity("/actuator/" + endpoint, String.class).getStatusCode())
                    .as("/actuator/" + endpoint).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}