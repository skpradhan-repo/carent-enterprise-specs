package com.carent.claims.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Coverage rate table from specs/02-domain-model-spec.md section 4. */
class BenefitCategoryTest {

    @ParameterizedTest
    @CsvSource({
            "IN_NETWORK_PRIMARY_CARE,0.80",
            "OUT_OF_NETWORK_SPECIALIST,0.50",
            "PREVENTIVE_SCREENING,1.00",
            "EXCLUDED_COSMETIC,0.00",
            "GENERAL_MEDICAL,0.70"
    })
    void coverage_rate_matches_spec_table(BenefitCategory category, String expectedRate) {
        BigDecimal rate = category.coverageRate();

        assertThat(rate).isEqualByComparingTo(expectedRate);
        assertThat(rate.scale()).isEqualTo(2);
    }
}
