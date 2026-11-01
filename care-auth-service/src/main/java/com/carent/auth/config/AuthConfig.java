package com.carent.auth.config;

import com.carent.auth.token.TokenService;
import com.carent.auth.user.UserDirectory;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Wires the framework-free token and user classes. Bad configuration fails start-up, not the first login. */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    TokenService tokenService(AuthProperties properties, Clock clock) {
        AuthProperties.Jwt jwt = properties.jwt();
        return new TokenService(jwt.secret(), jwt.issuer(), jwt.ttl(), clock);
    }

    @Bean
    UserDirectory userDirectory(AuthProperties properties, PasswordEncoder encoder) {
        return new UserDirectory(properties.users(), encoder);
    }
}
