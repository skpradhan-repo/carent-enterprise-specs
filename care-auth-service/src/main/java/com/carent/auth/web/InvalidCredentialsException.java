package com.carent.auth.web;

/** Thrown for any failed login; deliberately carries no detail about why. */
class InvalidCredentialsException extends RuntimeException {

    InvalidCredentialsException() {
        super("Invalid username or password", null, false, false);
    }
}
