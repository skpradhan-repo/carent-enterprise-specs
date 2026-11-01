package com.carent.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.carent.auth.TestSecrets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

    @Test
    void to_string_never_reveals_the_jwt_secret_or_a_password() {
        String secret = TestSecrets.jwtSecret();
        String password = TestSecrets.password();
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Jwt(secret, "care-auth-service", Duration.ofHours(1)),
                List.of(new AuthProperties.User("member1", password, List.of("ROLE_MEMBER"))));

        assertThat(properties.toString()).doesNotContain(secret).doesNotContain(password);
        assertThat(properties.jwt().toString()).doesNotContain(secret);
        assertThat(properties.users().get(0).toString()).doesNotContain(password).contains("member1");
    }

    @Test
    void missing_user_list_becomes_empty() {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Jwt(TestSecrets.jwtSecret(), "care-auth-service", Duration.ofHours(1)), null);

        assertThat(properties.users()).isEmpty();
    }
}
