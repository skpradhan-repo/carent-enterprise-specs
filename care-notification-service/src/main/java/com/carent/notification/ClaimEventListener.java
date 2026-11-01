package com.carent.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Single listener on {@value #TOPIC} (specs/04 section 4). Writes the audit lines verbatim from the spec.
 * Delivery is at-least-once, so a duplicate event simply logs again. A message that cannot be understood
 * is skipped with a warning that carries no message content, so one bad record never blocks the partition.
 */
@Component
public class ClaimEventListener {

    static final String TOPIC = "claim-lifecycle-events";

    private static final Logger LOG = LoggerFactory.getLogger(ClaimEventListener.class);

    private final ObjectMapper objectMapper;

    public ClaimEventListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = "care-notification-group")
    public void onClaimEvent(String message) {
        if (message == null || message.isBlank()) {
            LOG.warn("Skipping empty claim event");
            return;
        }
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = text(event, "eventType");
            JsonNode payload = event.path("payload");
            if ("CLAIM_ADJUDICATED".equals(eventType)) {
                onAdjudicated(payload);
            } else if ("CLAIM_REVERSED".equals(eventType)) {
                onReversed(payload);
            }
            // CLAIM_SUBMITTED and unknown types need no notification
        } catch (JsonProcessingException | IllegalArgumentException e) {
            LOG.warn("Skipping claim event that could not be processed: {}", e.getClass().getSimpleName());
        }
    }

    private void onAdjudicated(JsonNode payload) {
        String status = text(payload, "status");
        if (!"APPROVED".equals(status) && !"DENIED".equals(status)) {
            return;
        }
        LOG.info("[AUDIT NOTIFICATION] Member {} notified: Claim {} status updated to {}. Payable: ${}.",
                require(payload, "memberId"), require(payload, "claimReference"), status,
                require(payload, "adjudicatedAmount"));
    }

    private void onReversed(JsonNode payload) {
        LOG.info("[AUDIT NOTIFICATION] Member {}: Claim {} REVERSED. Prior payable ${} reversed.",
                require(payload, "memberId"), require(payload, "claimReference"),
                require(payload, "previousAdjudicatedAmount"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static String require(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value;
    }
}
