package com.carent.auth.user;

import java.util.List;

/** A successfully authenticated user; carries no credential. */
public record AuthenticatedUser(String username, List<String> roles) {
}
