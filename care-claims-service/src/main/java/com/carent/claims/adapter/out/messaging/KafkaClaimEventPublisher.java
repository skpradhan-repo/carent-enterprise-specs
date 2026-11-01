package com.carent.claims.adapter.out.messaging;

import com.carent.claims.application.port.out.ClaimEventPublisher;
import com.carent.claims.domain.model.Claim;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes the specs/04 envelope to {@value #TOPIC}, keyed by claimReference. Money is written as strings
 * with scale 2. Publishing is best effort: the claim is already stored, so a broker problem is logged and
 * never turned into a failed submission (known MVP limitation: no outbox, so an event can be lost).
 */
@Component
public class KafkaClaimEventPublisher implements ClaimEventPublisher {

    public static final String TOPIC = "claim-lifecycle-events";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaClaimEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public KafkaClaimEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                    Clock clock) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void claimSubmitted(Claim claim) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("claimReference", claim.claimReference().value());
        payload.put("memberId", claim.memberId());
        payload.put("providerId", claim.providerId());
        payload.put("serviceDate", claim.serviceDate().toString());
        payload.put("billedAmount", money(claim.billedAmount()));
        payload.put("benefitCategory", claim.benefitCategory().name());
        publish("CLAIM_SUBMITTED", claim.claimReference().value(), payload);
    }

    @Override
    public void claimAdjudicated(Claim claim) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("claimReference", claim.claimReference().value());
        payload.put("memberId", claim.memberId());
        payload.put("status", claim.status().name());
        payload.put("billedAmount", money(claim.billedAmount()));
        payload.put("adjudicatedAmount", money(claim.adjudicatedAmount()));
        payload.put("patientCoPay", money(claim.patientCoPay()));
        payload.put("denialReason", claim.denialReason() == null ? null : claim.denialReason().name());
        publish("CLAIM_ADJUDICATED", claim.claimReference().value(), payload);
    }

    private void publish(String eventType, String claimReference, Map<String, Object> payload) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("eventType", eventType);
            envelope.put("timestamp", clock.instant().toString());
            envelope.put("payload", payload);

            kafkaTemplate.send(TOPIC, claimReference, objectMapper.writeValueAsString(envelope))
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            LOG.error("Could not deliver {} for claim {}", eventType, claimReference, failure);
                        }
                    });
        } catch (JsonProcessingException | RuntimeException e) {
            LOG.error("Could not publish {} for claim {}", eventType, claimReference, e);
        }
    }

    private static String money(BigDecimal amount) {
        return amount == null ? null : amount.toPlainString();
    }
}
