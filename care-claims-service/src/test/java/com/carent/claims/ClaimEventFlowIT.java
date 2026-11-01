package com.carent.claims;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

/** specs/06: proves a REST submission really puts spec-shaped events on the broker, keyed by claimReference. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = "claim-lifecycle-events",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext
class ClaimEventFlowIT {

    private static final String TOPIC = "claim-lifecycle-events";

    private final TestRestTemplate rest;
    private final EmbeddedKafkaBroker broker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    ClaimEventFlowIT(TestRestTemplate rest, EmbeddedKafkaBroker broker) {
        this.rest = rest;
        this.broker = broker;
    }

    private ResponseEntity<JsonNode> submit(String member, String billed, String category, LocalDate date) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("memberId", member);
        body.put("providerId", "PRV-44102");
        body.put("serviceDate", date.toString());
        body.put("billedAmount", billed);
        body.put("benefitCategory", category);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/v1/claims", new HttpEntity<>(body, headers), JsonNode.class);
    }

    private List<ConsumerRecord<String, String>> eventsFor(String claimReference, int expected) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("claims-it-" + claimReference, "true", broker);
        props.put("auto.offset.reset", "earliest");
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, TOPIC);
            long deadline = System.currentTimeMillis() + 30_000;
            while (found.size() < expected && System.currentTimeMillis() < deadline) {
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2)).forEach(record -> {
                    if (claimReference.equals(record.key())) {
                        found.add(record);
                    }
                });
            }
        }
        return found;
    }

    @Test
    void E1_post_publishes_submitted_then_adjudicated_with_string_money() throws Exception {
        ResponseEntity<JsonNode> response = submit("MEM-71001", "150.00", "IN_NETWORK_PRIMARY_CARE",
                LocalDate.now(ZoneOffset.UTC).minusDays(6));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String reference = response.getBody().get("claimReference").asText();

        List<ConsumerRecord<String, String>> records = eventsFor(reference, 2);

        assertThat(records).hasSize(2);
        JsonNode submitted = objectMapper.readTree(records.get(0).value());
        JsonNode adjudicated = objectMapper.readTree(records.get(1).value());
        assertThat(submitted.get("eventType").asText()).isEqualTo("CLAIM_SUBMITTED");
        assertThat(submitted.get("payload").get("billedAmount").asText()).isEqualTo("150.00");
        assertThat(adjudicated.get("eventType").asText()).isEqualTo("CLAIM_ADJUDICATED");
        JsonNode payload = adjudicated.get("payload");
        assertThat(payload.get("claimReference").asText()).isEqualTo(reference);
        assertThat(payload.get("status").asText()).isEqualTo("APPROVED");
        assertThat(payload.get("adjudicatedAmount").asText()).isEqualTo("120.00");
        assertThat(payload.get("patientCoPay").asText()).isEqualTo("30.00");
        assertThat(records.get(0).partition()).isEqualTo(records.get(1).partition());
    }

    @Test
    void E7_timely_filing_denial_publishes_a_denied_adjudication() throws Exception {
        ResponseEntity<JsonNode> response = submit("MEM-71002", "100.00", "IN_NETWORK_PRIMARY_CARE",
                LocalDate.now(ZoneOffset.UTC).minusDays(400));
        String reference = response.getBody().get("claimReference").asText();

        List<ConsumerRecord<String, String>> records = eventsFor(reference, 2);

        assertThat(records).hasSize(2);
        JsonNode payload = objectMapper.readTree(records.get(1).value()).get("payload");
        assertThat(payload.get("status").asText()).isEqualTo("DENIED");
        assertThat(payload.get("denialReason").asText()).isEqualTo("TIMELY_FILING_EXCEEDED");
        assertThat(payload.get("adjudicatedAmount").asText()).isEqualTo("0.00");
    }

    @Test
    void E5_rejected_submission_publishes_nothing() {
        ResponseEntity<JsonNode> response = submit("MEM-71003", "4.99", "GENERAL_MEDICAL",
                LocalDate.now(ZoneOffset.UTC).minusDays(1));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(eventsForMember("MEM-71003")).isEmpty();
    }

    private List<String> eventsForMember(String memberId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("claims-it-member-" + memberId, "true", broker);
        props.put("auto.offset.reset", "earliest");
        List<String> found = new ArrayList<>();
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, TOPIC);
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(3)).forEach(record -> {
                if (record.value().contains(memberId)) {
                    found.add(record.value());
                }
            });
        }
        return found;
    }
}
