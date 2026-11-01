package com.carent.claims.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carent.claims.domain.exception.InvalidClaimException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClaimReferenceTest {

    @Test
    void accepts_spec_example() {
        ClaimReference reference = ClaimReference.of("CLM-20261031-891023");

        assertThat(reference.value()).isEqualTo("CLM-20261031-891023");
        assertThat(reference).hasToString("CLM-20261031-891023");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "CLM-2026-1", "clm-20261031-891023", "CLM-20261031-89102", "CLM-20261031-8910234",
            "XXX-20261031-891023", " CLM-20261031-891023"})
    void rejects_malformed_values(String value) {
        assertThatThrownBy(() -> ClaimReference.of(value)).isInstanceOf(InvalidClaimException.class);
    }

    @Test
    void equality_is_by_value() {
        assertThat(ClaimReference.of("CLM-20261031-891023")).isEqualTo(ClaimReference.of("CLM-20261031-891023"));
    }
}
