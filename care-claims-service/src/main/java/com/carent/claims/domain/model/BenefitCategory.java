package com.carent.claims.domain.model;

import java.math.BigDecimal;

/** Benefit categories and their plan coverage rates (specs/02-domain-model-spec.md section 4). */
public enum BenefitCategory {
    IN_NETWORK_PRIMARY_CARE("0.80"),
    OUT_OF_NETWORK_SPECIALIST("0.50"),
    PREVENTIVE_SCREENING("1.00"),
    EXCLUDED_COSMETIC("0.00"),
    GENERAL_MEDICAL("0.70");

    private final BigDecimal coverageRate;

    BenefitCategory(String coverageRate) {
        this.coverageRate = new BigDecimal(coverageRate);
    }

    /** Plan-allowed share of the billed amount, 0.00 to 1.00, scale 2. */
    public BigDecimal coverageRate() {
        return coverageRate;
    }
}
