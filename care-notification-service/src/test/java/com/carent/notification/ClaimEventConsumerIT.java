package com.carent.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestConstructor;

/** Real consumer, real (embedded) broker: a spec-shaped event on the topic produces the audit line. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EmbeddedKafka(partitions = 3, topics = "claim-lifecycle-events",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext
class ClaimEventConsumerIT {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ClaimEventListener.class);
    private final EmbeddedKafkaBroker broker;

    ClaimEventConsumerIT(EmbeddedKafkaBroker broker) {
        this.broker = broker;
    }

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private KafkaTemplate<String, String> producer() {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new StringSerializer(),
                new StringSerializer()));
    }

    private List<String> auditLines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("[AUDIT NOTIFICATION]")).toList();
    }

    @Test
    void adjudicated_event_on_the_topic_is_turned_into_the_audit_line() {
        String json = """
                {"eventId":"6f1c2f3e-0000-4000-8000-000000000002","eventType":"CLAIM_ADJUDICATED",
                 "timestamp":"2026-10-31T20:15:30Z","payload":{"claimReference":"CLM-20261031-891023",
                 "memberId":"MEM-99201","status":"APPROVED","billedAmount":"250.00",
                 "adjudicatedAmount":"200.00","patientCoPay":"50.00","denialReason":null}}""";

        producer().send("claim-lifecycle-events", "CLM-20261031-891023", json);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(auditLines()).containsExactly(
                "[AUDIT NOTIFICATION] Member MEM-99201 notified: Claim CLM-20261031-891023 "
                        + "status updated to APPROVED. Payable: $200.00."));
    }

    @Test
    void a_poison_message_does_not_stop_later_events_from_being_consumed() {
        KafkaTemplate<String, String> template = producer();
        template.send("claim-lifecycle-events", "CLM-20261031-000001", "{this is not json");
        template.send("claim-lifecycle-events", "CLM-20261031-000001", """
                {"eventId":"6f1c2f3e-0000-4000-8000-000000000003","eventType":"CLAIM_REVERSED",
                 "timestamp":"2026-10-31T20:15:30Z","payload":{"claimReference":"CLM-20261031-000001",
                 "memberId":"MEM-55555","previousAdjudicatedAmount":"80.00","status":"REVERSED"}}""");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(auditLines()).contains(
                "[AUDIT NOTIFICATION] Member MEM-55555: Claim CLM-20261031-000001 REVERSED. "
                        + "Prior payable $80.00 reversed."));
    }
}
