package com.carent.claims.domain.model;

import com.carent.claims.domain.exception.InvalidClaimException;
import java.util.regex.Pattern;

/** Business identifier, format {@code CLM-YYYYMMDD-XXXXXX} (specs/02, matches the OpenAPI path pattern). */
public record ClaimReference(String value) {

    private static final Pattern FORMAT = Pattern.compile("^CLM-\\d{8}-\\d{6}$");

    public ClaimReference {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidClaimException("claimReference must match CLM-YYYYMMDD-XXXXXX");
        }
    }

    public static ClaimReference of(String value) {
        return new ClaimReference(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
