package com.carent.auth.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Auth settings (specs/07). Secrets come from the environment, never from the repository, and are kept
 * out of every {@code toString()} so they cannot leak through logs.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(@DefaultValue Jwt jwt, List<User> users) {

    public AuthProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public record Jwt(String secret,
                      @DefaultValue("care-auth-service") String issuer,
                      @DefaultValue("1h") Duration ttl) {

        @Override
        public String toString() {
            return "Jwt[issuer=" + issuer + ", ttl=" + ttl + "]";
        }
    }

    public record User(String username, String password, List<String> roles) {

        public User {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }

        @Override
        public String toString() {
            return "User[username=" + username + ", roles=" + roles + "]";
        }
    }
}
