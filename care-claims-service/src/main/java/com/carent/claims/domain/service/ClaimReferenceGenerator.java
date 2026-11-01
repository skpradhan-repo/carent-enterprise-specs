package com.carent.claims.domain.service;

import com.carent.claims.domain.model.ClaimReference;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Generates {@code CLM-yyyyMMdd-NNNNNN} references (specs/02). Randomness is injected so the
 * application layer can supply a {@code SecureRandom}; uniqueness is ultimately enforced by the
 * unique constraint on {@code claim_reference}.
 */
public class ClaimReferenceGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final Clock clock;
    private final Random random;

    public ClaimReferenceGenerator(Clock clock, Random random) {
        this.clock = clock;
        this.random = random;
    }

    public ClaimReference next() {
        String date = LocalDate.now(clock).format(DATE);
        return ClaimReference.of("CLM-" + date + "-" + String.format("%06d", random.nextInt(1_000_000)));
    }
}
