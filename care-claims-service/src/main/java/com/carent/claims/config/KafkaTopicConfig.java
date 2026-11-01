package com.carent.claims.config;

import com.carent.claims.adapter.out.messaging.KafkaClaimEventPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Creates the lifecycle topic on startup when it does not exist (specs/04: 3 partitions locally). */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic claimLifecycleTopic() {
        return TopicBuilder.name(KafkaClaimEventPublisher.TOPIC).partitions(3).replicas(1).build();
    }
}
