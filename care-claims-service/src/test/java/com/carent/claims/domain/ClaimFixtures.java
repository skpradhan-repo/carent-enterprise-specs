package com.carent.claims.domain;

import com.carent.claims.domain.model.BenefitCategory;
import com.carent.claims.domain.model.Claim;
import com.carent.claims.domain.model.ClaimReference;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Shared test data for domain tests. "Today" is fixed so date rules are deterministic. */
public final class ClaimFixtures {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC);
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 31);
    public static final ClaimReference REFERENCE = ClaimReference.of("CLM-20261031-891023");

    private ClaimFixtures() {
    }

    public static Claim pending(BenefitCategory category, String billedAmount) {
        return pending(category, billedAmount, TODAY.minusDays(6));
    }

    public static Claim pending(BenefitCategory category, String billedAmount, LocalDate serviceDate) {
        return Claim.submit(REFERENCE, "MEM-99201", "PRV-44102", serviceDate,
                new BigDecimal(billedAmount), category, CLOCK);
    }
}
