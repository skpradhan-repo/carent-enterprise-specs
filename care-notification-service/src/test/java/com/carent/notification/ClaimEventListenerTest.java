package com.carent.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Audit line formats are copied verbatim from specs/04 section 4. */
class ClaimEventListenerTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ClaimEventListener.class);
    private final ClaimEventListener listener = new ClaimEventListener(new ObjectMapper());

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private static String event(String type, String payload) {
        return "{\"eventId\":\"6f1c2f3e-0000-4000-8000-000000000001\",\"eventType\":\"" + type
                + "\",\"timestamp\":\"2026-10-31T20:15:30Z\",\"payload\":" + payload + "}";
    }

    private List<String> auditLines() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("[AUDIT NOTIFICATION]"))
                .toList();
    }

    @Test
    void approved_adjudication_writes_the_exact_audit_line() {
        listener.onClaimEvent(event("CLAIM_ADJUDICATED", """
                {"claimReference":"CLM-20261031-891023","memberId":"MEM-99201","status":"APPROVED",
                 "billedAmount":"250.00","adjudicatedAmount":"200.00","patientCoPay":"50.00","denialReason":null}"""));

        assertThat(auditLines()).containsExactly(
                "[AUDIT NOTIFICATION] Member MEM-99201 notified: Claim CLM-20261031-891023 "
                        + "status updated to APPROVED. Payable: $200.00.");
    }

    @Test
    void denied_adjudication_writes_the_audit_line_with_zero_payable() {
        listener.onClaimEvent(event("CLAIM_ADJUDICATED", """
                {"claimReference":"CLM-20261031-891023","memberId":"MEM-99201","status":"DENIED",
                 "billedAmount":"100.00","adjudicatedAmount":"0.00","patientCoPay":"100.00",
                 "denialReason":"TIMELY_FILING_EXCEEDED"}"""));

        assertThat(auditLines()).containsExactly(
                "[AUDIT NOTIFICATION] Member MEM-99201 notified: Claim CLM-20261031-891023 "
                        + "status updated to DENIED. Payable: $0.00.");
    }

    @Test
    void reversal_writes_the_exact_audit_line() {
        listener.onClaimEvent(event("CLAIM_REVERSED", """
                {"claimReference":"CLM-20261031-891023","memberId":"MEM-99201",
                 "previousAdjudicatedAmount":"200.00","status":"REVERSED"}"""));

        assertThat(auditLines()).containsExactly(
                "[AUDIT NOTIFICATION] Member MEM-99201: Claim CLM-20261031-891023 REVERSED. "
                        + "Prior payable $200.00 reversed.");
    }

    @Test
    void submitted_events_are_not_notified() {
        listener.onClaimEvent(event("CLAIM_SUBMITTED", """
                {"claimReference":"CLM-20261031-891023","memberId":"MEM-99201","providerId":"PRV-44102",
                 "serviceDate":"2026-10-25","billedAmount":"250.00","benefitCategory":"IN_NETWORK_PRIMARY_CARE"}"""));

        assertThat(auditLines()).isEmpty();
    }

    @Test
    void adjudicated_event_with_a_status_other_than_approved_or_denied_is_not_notified() {
        listener.onClaimEvent(event("CLAIM_ADJUDICATED", """
                {"claimReference":"CLM-20261031-891023","memberId":"MEM-99201","status":"UNDER_REVIEW",
                 "adjudicatedAmount":null}"""));

        assertThat(auditLines()).isEmpty();
    }

    @Test
    void unknown_event_types_are_ignored() {
        listener.onClaimEvent(event("CLAIM_SOMETHING_NEW", "{}"));

        assertThat(auditLines()).isEmpty();
    }

    @Test
    void duplicate_delivery_logs_twice_which_the_spec_allows() {
        String message = event("CLAIM_REVERSED", """
                {"claimReference":"CLM-20261031-891023","memberId":"MEM-99201",
                 "previousAdjudicatedAmount":"200.00","status":"REVERSED"}""");

        listener.onClaimEvent(message);
        listener.onClaimEvent(message);

        assertThat(auditLines()).hasSize(2);
    }

    @Test
    void malformed_json_is_skipped_without_throwing_and_without_echoing_the_message() {
        assertThatCode(() -> listener.onClaimEvent("{not json MEM-99201")).doesNotThrowAnyException();

        assertThat(auditLines()).isEmpty();
        assertThat(appender.list).anySatisfy(entry -> assertThat(entry.getLevel()).isEqualTo(Level.WARN));
        assertThat(appender.list).noneSatisfy(entry -> assertThat(entry.getFormattedMessage())
                .contains("MEM-99201"));
    }

    @Test
    void event_missing_required_fields_is_skipped_without_throwing() {
        assertThatCode(() -> listener.onClaimEvent(event("CLAIM_ADJUDICATED", "{\"status\":\"APPROVED\"}")))
                .doesNotThrowAnyException();

        assertThat(auditLines()).isEmpty();
    }

    @Test
    void null_or_blank_messages_are_skipped_without_throwing() {
        assertThatCode(() -> {
            listener.onClaimEvent(null);
            listener.onClaimEvent("  ");
        }).doesNotThrowAnyException();

        assertThat(auditLines()).isEmpty();
    }
}
