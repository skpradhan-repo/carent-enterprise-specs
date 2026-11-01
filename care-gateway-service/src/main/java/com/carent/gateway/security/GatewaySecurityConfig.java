package com.carent.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

/**
 * Optional JWT validation (specs/07). {@code gateway.security.enabled=true} (the default) requires a valid
 * HS256 token from care-auth-service on everything except the auth endpoint and health; {@code false} is the
 * local-demo mode and logs a warning. Misconfiguration fails start-up rather than the first request.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewaySecurityProperties.class)
class GatewaySecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(GatewaySecurityConfig.class);

    static final int MIN_SECRET_LENGTH = 32;

    @Bean
    @ConditionalOnProperty(name = "gateway.security.enabled", havingValue = "true", matchIfMissing = true)
    ReactiveJwtDecoder jwtDecoder(GatewaySecurityProperties properties) {
        String secret = properties.jwtSecret();
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalArgumentException(
                    "JWT_SECRET is required when gateway.security.enabled=true (or set it to false for a local demo)");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("JWT_SECRET must be at least " + MIN_SECRET_LENGTH + " characters");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    @ConditionalOnProperty(name = "gateway.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityWebFilterChain securedChain(ServerHttpSecurity http, ReactiveJwtDecoder decoder,
                                        ObjectMapper objectMapper) {
        ProblemAuthenticationEntryPoint entryPoint = new ProblemAuthenticationEntryPoint(objectMapper);
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)   // stateless bearer-token API: no cookies, no CSRF
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/v1/auth/**", "/actuator/health", "/actuator/health/**").permitAll()
                        .anyExchange().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.jwtDecoder(decoder)))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "gateway.security.enabled", havingValue = "false")
    SecurityWebFilterChain openChain(ServerHttpSecurity http) {
        LOG.warn("JWT validation is DISABLED (gateway.security.enabled=false). Use this for local demos only.");
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                .build();
    }
}
