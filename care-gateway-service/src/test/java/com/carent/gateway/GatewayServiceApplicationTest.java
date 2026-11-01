package com.carent.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = "gateway.security.enabled=false")
class GatewayServiceApplicationTest {

    private final ApplicationContext context;

    GatewayServiceApplicationTest(ApplicationContext context) {
        this.context = context;
    }

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }
}