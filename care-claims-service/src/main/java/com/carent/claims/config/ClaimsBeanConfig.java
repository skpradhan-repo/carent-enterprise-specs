package com.carent.claims.config;

import com.carent.claims.application.port.out.ClaimEventPublisher;
import com.carent.claims.application.port.out.ClaimRepository;
import com.carent.claims.application.service.ClaimApplicationService;
import com.carent.claims.domain.service.AdjudicationEngine;
import com.carent.claims.domain.service.ClaimReferenceGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free domain and application classes as Spring beans. */
@Configuration
public class ClaimsBeanConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AdjudicationEngine adjudicationEngine(Clock clock) {
        return new AdjudicationEngine(clock);
    }

    @Bean
    ClaimReferenceGenerator claimReferenceGenerator(Clock clock) {
        return new ClaimReferenceGenerator(clock, new SecureRandom());
    }

    @Bean
    ClaimApplicationService claimApplicationService(ClaimRepository repository, ClaimEventPublisher events,
                                                    AdjudicationEngine engine,
                                                    ClaimReferenceGenerator generator, Clock clock) {
        return new ClaimApplicationService(repository, events, engine, generator, clock);
    }
}
