package com.carent.auth.web;

/** Response of {@code POST /api/v1/auth/token}. {@code toString()} hides the token. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    @Override
    public String toString() {
        return "TokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
