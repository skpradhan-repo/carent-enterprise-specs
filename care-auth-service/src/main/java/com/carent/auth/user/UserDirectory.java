package com.carent.auth.user;

import com.carent.auth.config.AuthProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Demo user store built from configuration. Configured passwords are hashed once at start-up and only
 * the hashes are kept; clear text is never stored. An unknown user still costs one hash comparison so
 * response time does not reveal which usernames exist.
 */
public class UserDirectory {

    static final Set<String> KNOWN_ROLES = Set.of("ROLE_MEMBER", "ROLE_CLAIMS_ADJUDICATOR", "ROLE_ADMIN");

    private record Entry(String passwordHash, List<String> roles) {
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private final PasswordEncoder encoder;
    private final String unknownUserHash;

    public UserDirectory(List<AuthProperties.User> users, PasswordEncoder encoder) {
        this.encoder = encoder;
        this.unknownUserHash = encoder.encode("not-a-real-account");
        for (AuthProperties.User user : users) {
            validate(user);
            if (entries.containsKey(user.username())) {
                throw new IllegalArgumentException("Duplicate username in auth configuration");
            }
            entries.put(user.username(), new Entry(encoder.encode(user.password()), user.roles()));
        }
    }

    private static void validate(AuthProperties.User user) {
        if (user.username() == null || user.username().isBlank()) {
            throw new IllegalArgumentException("Every configured user needs a username");
        }
        // an unset env var arrives as the literal "${NAME}"; refuse it rather than hash it into a real password
        if (user.password() == null || user.password().isEmpty() || user.password().startsWith("${")) {
            throw new IllegalArgumentException("User " + user.username() + " has no password configured");
        }
        if (user.roles().isEmpty()) {
            throw new IllegalArgumentException("User " + user.username() + " needs at least one role");
        }
        for (String role : user.roles()) {
            if (!KNOWN_ROLES.contains(role)) {
                throw new IllegalArgumentException("Unknown role " + role + " for user " + user.username());
            }
        }
    }

    public Optional<AuthenticatedUser> authenticate(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }
        Entry entry = entries.get(username);
        if (entry == null) {
            encoder.matches(password, unknownUserHash);
            return Optional.empty();
        }
        if (!encoder.matches(password, entry.passwordHash())) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedUser(username, entry.roles()));
    }

    @Override
    public String toString() {
        return "UserDirectory[users=" + entries.size() + "]";
    }
}
