package com.carent.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class AuthServiceApplicationTest {

    @DynamicPropertySource
    static void requiredSettings(DynamicPropertyRegistry registry) {
        registry.add("auth.jwt.secret", TestSecrets::jwtSecret);
        registry.add("auth.users[0].username", () -> "member1");
        registry.add("auth.users[0].password", TestSecrets::password);
        registry.add("auth.users[0].roles[0]", () -> "ROLE_MEMBER");
    }

    private final ApplicationContext context;

    AuthServiceApplicationTest(ApplicationContext context) {
        this.context = context;
    }

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void start_up_fails_when_the_jwt_secret_is_too_short() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(AuthServiceApplication.class)
                .run("--server.port=0", "--auth.jwt.secret=too-short",
                        "--auth.users[0].username=member1", "--auth.users[0].password=pw",
                        "--auth.users[0].roles[0]=ROLE_MEMBER").close())
                .hasStackTraceContaining("at least 32 characters");
    }

    @Test
    void start_up_fails_when_no_jwt_secret_is_configured() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(AuthServiceApplication.class)
                .run("--server.port=0", "--auth.jwt.secret=", "--auth.users=").close())
                .hasStackTraceContaining("JWT secret is required");
    }

    @Test
    void start_up_fails_with_a_clear_message_when_the_jwt_env_var_is_not_set() {
        // A complete user list is supplied (Boot replaces lists, it does not merge them), so the only gap
        // is application.yml's ${JWT_SECRET}, which is unset in the test JVM
        assertThatThrownBy(() -> new SpringApplicationBuilder(AuthServiceApplication.class)
                .run("--server.port=0", "--auth.users[0].username=member1", "--auth.users[0].password=pw",
                        "--auth.users[0].roles[0]=ROLE_MEMBER").close())
                .hasStackTraceContaining("JWT_SECRET");
    }

    @Test
    void start_up_fails_when_a_user_password_env_var_is_not_set() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(AuthServiceApplication.class)
                .run("--server.port=0", "--auth.jwt.secret=" + TestSecrets.jwtSecret()).close())
                .hasStackTraceContaining("no password configured");
    }
}