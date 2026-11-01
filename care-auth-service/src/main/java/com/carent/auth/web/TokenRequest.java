package com.carent.auth.web;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/v1/auth/token}. {@code toString()} hides the password. */
public record TokenRequest(@NotBlank String username, @NotBlank String password) {

    @Override
    public String toString() {
        return "TokenRequest[username=" + username + "]";
    }
}
