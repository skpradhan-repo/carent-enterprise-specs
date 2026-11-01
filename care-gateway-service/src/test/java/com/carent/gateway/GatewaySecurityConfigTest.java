package com.carent.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** The gateway must refuse to start in an insecure or half-configured state. */
class GatewaySecurityConfigTest {

    private static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(GatewayServiceApplication.class).run(args);
    }

    @Test
    void validation_enabled_without_a_secret_fails_start_up_naming_the_variable() {
        assertThatThrownBy(() -> start("--server.port=0", "--gateway.security.enabled=true", "--JWT_SECRET=")
                .close()).hasStackTraceContaining("JWT_SECRET");
    }

    @Test
    void validation_enabled_with_a_short_secret_fails_start_up_without_echoing_it() {
        String shortSecret = "only-thirty-one-characters-long";
        assertThat(shortSecret).hasSize(31);

        assertThatThrownBy(() -> start("--server.port=0", "--gateway.security.enabled=true",
                "--JWT_SECRET=" + shortSecret).close())
                .hasStackTraceContaining("at least 32 characters")
                .satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContain(shortSecret));
    }

    @Test
    void validation_disabled_needs_no_secret_and_starts() {
        try (ConfigurableApplicationContext context = start("--server.port=0",
                "--gateway.security.enabled=false", "--JWT_SECRET=")) {
            assertThat(context.isRunning()).isTrue();
        }
    }

    @Test
    void validation_is_enabled_by_default_so_forgetting_the_flag_is_never_insecure() {
        assertThatThrownBy(() -> start("--server.port=0", "--JWT_SECRET=").close())
                .hasStackTraceContaining("JWT_SECRET");
    }
}