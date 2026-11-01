package com.carent.claims;

import static org.assertj.core.api.Assertions.assertThat;

import com.carent.claims.application.port.out.ClaimEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

/**
 * Acceptance tests: real HTTP, real Spring wiring, real Flyway schema on H2 (specs/06). Each test uses its
 * own member so the 24-hour duplicate rule (R6) does not couple tests together.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ClaimApiIT {

    private static final String CLAIMS = "/api/v1/claims";
    private static final AtomicInteger MEMBER_SEQUENCE = new AtomicInteger(10_000);

    // Kafka is covered by ClaimEventFlowIT; here a real broker would only slow every request down
    @MockBean
    private ClaimEventPublisher events;

    private final TestRestTemplate rest;

    ClaimApiIT(TestRestTemplate rest) {
        this.rest = rest;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static String uniqueMember() {
        return "MEM-" + MEMBER_SEQUENCE.incrementAndGet();
    }

    private static Map<String, String> request(String member, LocalDate serviceDate, String billed,
                                               String category) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("memberId", member);
        body.put("providerId", "PRV-44102");
        body.put("serviceDate", serviceDate.toString());
        body.put("billedAmount", billed);
        body.put("benefitCategory", category);
        return body;
    }

    private ResponseEntity<JsonNode> submit(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(CLAIMS, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private static void assertMoney(JsonNode body, String adjudicated, String coPay) {
        assertThat(body.get("adjudicatedAmount").asText()).isEqualTo(adjudicated);
        assertThat(body.get("patientCoPay").asText()).isEqualTo(coPay);
    }

    // ---- E1, E3, E5, E7 (required by specs/06) ------------------------------------------------

    @Test
    void E1_in_network_primary_care_is_approved_with_80_percent_coverage() {
        ResponseEntity<JsonNode> response = submit(
                request(uniqueMember(), today().minusDays(6), "150.00", "IN_NETWORK_PRIMARY_CARE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body.get("status").asText()).isEqualTo("APPROVED");
        assertThat(body.get("billedAmount").asText()).isEqualTo("150.00");
        assertMoney(body, "120.00", "30.00");
        assertThat(body.get("denialReason").isNull()).isTrue();
        assertThat(body.get("claimReference").asText()).matches("^CLM-\\d{8}-\\d{6}$");
    }

    @Test
    void E3_preventive_screening_is_fully_covered() {
        ResponseEntity<JsonNode> response = submit(
                request(uniqueMember(), today().minusDays(6), "200.00", "PREVENTIVE_SCREENING"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status").asText()).isEqualTo("APPROVED");
        assertMoney(response.getBody(), "200.00", "0.00");
    }

    @Test
    void E5_below_minimum_billed_amount_returns_400_problem_detail() {
        ResponseEntity<JsonNode> response = submit(
                request(uniqueMember(), today().minusDays(6), "4.99", "GENERAL_MEDICAL"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        JsonNode problem = response.getBody();
        assertThat(problem.get("type").asText()).isEqualTo("about:blank");
        assertThat(problem.get("title").asText()).isEqualTo("Validation Failed");
        assertThat(problem.get("status").asInt()).isEqualTo(400);
        assertThat(problem.get("detail").asText()).contains("5.00");
        assertThat(problem.get("instance").asText()).isEqualTo(CLAIMS);
    }

    @Test
    void E7_timely_filing_exceeded_is_accepted_but_denied() {
        ResponseEntity<JsonNode> response = submit(
                request(uniqueMember(), today().minusDays(400), "100.00", "IN_NETWORK_PRIMARY_CARE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body.get("status").asText()).isEqualTo("DENIED");
        assertThat(body.get("denialReason").asText()).isEqualTo("TIMELY_FILING_EXCEEDED");
        assertMoney(body, "0.00", "100.00");
    }

    // ---- the remaining spec examples ---------------------------------------------------------

    @Test
    void E4_excluded_cosmetic_is_denied_with_full_member_responsibility() {
        ResponseEntity<JsonNode> response = submit(
                request(uniqueMember(), today().minusDays(6), "500.00", "EXCLUDED_COSMETIC"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status").asText()).isEqualTo("DENIED");
        assertThat(response.getBody().get("denialReason").asText()).isEqualTo("EXCLUDED_SERVICE");
        assertMoney(response.getBody(), "0.00", "500.00");
    }

    @Test
    void E6_exactly_the_minimum_is_accepted() {
        ResponseEntity<JsonNode> response = submit(
                request(uniqueMember(), today().minusDays(6), "5.00", "GENERAL_MEDICAL"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status").asText()).isEqualTo("APPROVED");
        assertMoney(response.getBody(), "3.50", "1.50");
    }

    @Test
    void E9_future_service_date_returns_400_problem_detail() {
        ResponseEntity<JsonNode> response = submit(
                request(uniqueMember(), today().plusDays(1), "50.00", "GENERAL_MEDICAL"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("title").asText()).isEqualTo("Validation Failed");
        assertThat(response.getBody().get("detail").asText()).contains("serviceDate");
    }

    // ---- R6 duplicate soft-check over HTTP ---------------------------------------------------

    @Test
    void R6_identical_resubmission_within_24_hours_goes_under_review() {
        Map<String, String> body = request(uniqueMember(), today().minusDays(6), "75.00", "GENERAL_MEDICAL");

        ResponseEntity<JsonNode> first = submit(body);
        ResponseEntity<JsonNode> second = submit(body);

        assertThat(first.getBody().get("status").asText()).isEqualTo("APPROVED");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().get("status").asText()).isEqualTo("UNDER_REVIEW");
        assertThat(second.getBody().get("adjudicatedAmount").isNull()).isTrue();
        assertThat(second.getBody().get("claimReference").asText())
                .isNotEqualTo(first.getBody().get("claimReference").asText());
    }

    // ---- GET -----------------------------------------------------------------------------------

    @Test
    void GET_returns_the_stored_claim_identical_to_the_post_response() {
        JsonNode created = submit(
                request(uniqueMember(), today().minusDays(6), "150.00", "IN_NETWORK_PRIMARY_CARE")).getBody();

        ResponseEntity<JsonNode> fetched =
                rest.getForEntity(CLAIMS + "/" + created.get("claimReference").asText(), JsonNode.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isEqualTo(created);
    }

    @Test
    void GET_unknown_reference_returns_404_problem_detail() {
        ResponseEntity<JsonNode> response = rest.getForEntity(CLAIMS + "/CLM-20000101-000000", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode problem = response.getBody();
        assertThat(problem.get("type").asText()).isEqualTo("about:blank");
        assertThat(problem.get("title").asText()).isEqualTo("Claim Not Found");
        assertThat(problem.get("status").asInt()).isEqualTo(404);
        assertThat(problem.get("instance").asText()).isEqualTo(CLAIMS + "/CLM-20000101-000000");
    }

    @Test
    void GET_malformed_reference_returns_400_problem_detail() {
        ResponseEntity<JsonNode> response = rest.getForEntity(CLAIMS + "/not-a-reference", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("title").asText()).isEqualTo("Validation Failed");
    }

    @Test
    void validation_failure_for_missing_member_returns_400_problem_detail() {
        Map<String, String> body = request(uniqueMember(), today().minusDays(6), "50.00", "GENERAL_MEDICAL");
        body.remove("memberId");

        ResponseEntity<JsonNode> response = submit(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asText()).contains("memberId");
    }

    // ---- actuator (hardening) ------------------------------------------------------------------

    @Test
    void health_is_up_and_exposes_no_internal_details() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/actuator/health", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status").asText()).isEqualTo("UP");
        assertThat(response.getBody().has("components")).as("no datasource or disk details").isFalse();
    }

    @Test
    void only_health_and_info_are_exposed_over_http() {
        for (String endpoint : new String[] {"env", "beans", "mappings", "heapdump", "configprops"}) {
            assertThat(rest.getForEntity("/actuator/" + endpoint, String.class).getStatusCode())
                    .as("/actuator/" + endpoint).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
