package com.carent.claims.domain.service;

import static com.carent.claims.domain.ClaimFixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.carent.claims.domain.model.ClaimReference;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ClaimReferenceGeneratorTest {

    @Test
    void uses_clock_date_and_zero_padded_six_digit_sequence() {
        Random fixed = new Random() {
            @Override
            public int nextInt(int bound) {
                return 42;
            }
        };

        ClaimReference reference = new ClaimReferenceGenerator(CLOCK, fixed).next();

        assertThat(reference.value()).isEqualTo("CLM-20261031-000042");
    }

    @Test
    void generated_references_always_match_the_contract_format() {
        ClaimReferenceGenerator generator = new ClaimReferenceGenerator(CLOCK, new Random(7));

        for (int i = 0; i < 200; i++) {
            assertThat(generator.next().value()).matches("^CLM-20261031-\\d{6}$");
        }
    }
}
