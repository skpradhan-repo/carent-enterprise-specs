package com.carent.claims.adapter.out.messaging;

import static com.carent.claims.domain.ClaimFixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carent.claims.domain.model.AdjudicationResult;
import com.carent.claims.domain.model.BenefitCategory;
import com.carent.claims.domain.model.Claim;
import com.carent.claims.domain.model.ClaimReference;
import com.carent.claims.domain.model.ClaimStatus;
import com.carent.claims.domain.model.DenialReason;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

/** Envelope and payload shapes from specs/04 sections 1-3. The broker is mocked; see ClaimEventFlowIT. */
@ExtendWith(MockitoExtension.class)
class KafkaClaimEventPublisherTest {

    private static final String REFERENCE = "CLM-20261031-891023";
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 10, 25);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaClaimEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaClaimEventPublisher(kafkaTemplate, objectMapper, CLOCK);
    }

    private static Claim pendingClaim(String billed) {
        return Claim.submit(ClaimReference.of(REFERENCE), "MEM-99201", "PRV-44102", SERVICE_DATE,
                new BigDecimal(billed), BenefitCategory.IN_NETWORK_PRIMARY_CARE, CLOCK);
    }

    private static Claim approvedClaim() {
        Claim claim = pendingClaim("250.00");
        claim.applyAdjudication(new AdjudicationResult(ClaimStatus.APPROVED, new BigDecimal("200.00"), new BigDecimal("50.00"), null,
                        new BigDecimal("200.00")), CLOCK);
        return claim;
    }

    private static Claim deniedClaim() {
        Claim claim = pendingClaim("100.00");
        claim.applyAdjudication(new AdjudicationResult(ClaimStatus.DENIED, new BigDecimal("0.00"), new BigDecimal("100.00"),
                DenialReason.TIMELY_FILING_EXCEEDED, new BigDecimal("0.00")), CLOCK);
        return claim;
    }

    private void sendSucceeds() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private ArgumentCaptor<String> captureValue() {
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("claim-lifecycle-events"), eq(REFERENCE), value.capture());
        return value;
    }

    @Test
    void submitted_event_matches_the_spec_envelope_and_payload() throws Exception {
        sendSucceeds();

        publisher.claimSubmitted(pendingClaim("250.00"));

        JsonNode event = objectMapper.readTree(captureValue().getValue());
        assertThat(event.fieldNames()).toIterable().containsExactly("eventId", "eventType", "timestamp", "payload");
        assertThat(UUID.fromString(event.get("eventId").asText())).isNotNull();
        assertThat(event.get("eventType").asText()).isEqualTo("CLAIM_SUBMITTED");
        assertThat(event.get("timestamp").asText()).isEqualTo("2026-10-31T10:00:00Z");

        JsonNode payload = event.get("payload");
        assertThat(payload.fieldNames()).toIterable().containsExactly("claimReference", "memberId", "providerId",
                "serviceDate", "billedAmount", "benefitCategory");
        assertThat(payload.get("claimReference").asText()).isEqualTo(REFERENCE);
        assertThat(payload.get("memberId").asText()).isEqualTo("MEM-99201");
        assertThat(payload.get("providerId").asText()).isEqualTo("PRV-44102");
        assertThat(payload.get("serviceDate").asText()).isEqualTo("2026-10-25");
        assertThat(payload.get("billedAmount").isTextual()).isTrue();
        assertThat(payload.get("billedAmount").asText()).isEqualTo("250.00");
        assertThat(payload.get("benefitCategory").asText()).isEqualTo("IN_NETWORK_PRIMARY_CARE");
    }

    @Test
    void adjudicated_event_for_an_approved_claim_carries_money_as_strings() throws Exception {
        sendSucceeds();

        publisher.claimAdjudicated(approvedClaim());

        JsonNode event = objectMapper.readTree(captureValue().getValue());
        assertThat(event.get("eventType").asText()).isEqualTo("CLAIM_ADJUDICATED");
        JsonNode payload = event.get("payload");
        assertThat(payload.fieldNames()).toIterable().containsExactly("claimReference", "memberId", "status",
                "billedAmount", "adjudicatedAmount", "patientCoPay", "denialReason");
        assertThat(payload.get("status").asText()).isEqualTo("APPROVED");
        assertThat(payload.get("billedAmount").asText()).isEqualTo("250.00");
        assertThat(payload.get("adjudicatedAmount").isTextual()).isTrue();
        assertThat(payload.get("adjudicatedAmount").asText()).isEqualTo("200.00");
        assertThat(payload.get("patientCoPay").asText()).isEqualTo("50.00");
        assertThat(payload.has("denialReason")).isTrue();
        assertThat(payload.get("denialReason").isNull()).isTrue();
    }

    @Test
    void adjudicated_event_for_a_denied_claim_carries_the_denial_reason() throws Exception {
        sendSucceeds();

        publisher.claimAdjudicated(deniedClaim());

        JsonNode payload = objectMapper.readTree(captureValue().getValue()).get("payload");
        assertThat(payload.get("status").asText()).isEqualTo(ClaimStatus.DENIED.name());
        assertThat(payload.get("adjudicatedAmount").asText()).isEqualTo("0.00");
        assertThat(payload.get("patientCoPay").asText()).isEqualTo("100.00");
        assertThat(payload.get("denialReason").asText()).isEqualTo("TIMELY_FILING_EXCEEDED");
    }

    @Test
    void every_event_gets_its_own_event_id() throws Exception {
        sendSucceeds();
        Claim claim = approvedClaim();

        publisher.claimSubmitted(claim);
        publisher.claimAdjudicated(claim);

        ArgumentCaptor<String> values = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, org.mockito.Mockito.times(2)).send(anyString(), anyString(), values.capture());
        String first = objectMapper.readTree(values.getAllValues().get(0)).get("eventId").asText();
        String second = objectMapper.readTree(values.getAllValues().get(1)).get("eventId").asText();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void a_failed_send_is_logged_but_never_reaches_the_caller() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatCode(() -> publisher.claimSubmitted(pendingClaim("250.00"))).doesNotThrowAnyException();
    }

    @Test
    void a_send_that_throws_synchronously_never_reaches_the_caller() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("metadata timeout"));

        assertThatCode(() -> publisher.claimAdjudicated(approvedClaim())).doesNotThrowAnyException();
    }
}
