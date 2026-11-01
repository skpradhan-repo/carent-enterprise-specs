package com.carent.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Gateway JWT settings (specs/07). Validation is ON unless explicitly disabled, so forgetting the flag can
 * never leave the gateway open. The secret stays out of {@code toString()}.
 */
@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(@DefaultValue("true") boolean enabled,
                                        String jwtSecret,
                                        @DefaultValue("care-auth-service") String issuer) {

    @Override
    public String toString() {
        return "GatewaySecurityProperties[enabled=" + enabled + ", issuer=" + issuer + "]";
    }
}
