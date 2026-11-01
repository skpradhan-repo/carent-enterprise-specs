package com.carent.claims.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carent.claims.domain.exception.InvalidClaimException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({"150,150.00", "150.5,150.50", "12.3400,12.34", "0,0.00", "100E0,100.00"})
    void normalize_sets_scale_two(String input, String expected) {
        assertThat(Money.normalize(new BigDecimal(input))).hasToString(expected);
    }

    @ParameterizedTest
    @CsvSource({"12.345", "12.3450001", "0.001"})
    void normalize_rejects_more_than_two_decimals(String input) {
        assertThatThrownBy(() -> Money.normalize(new BigDecimal(input)))
                .isInstanceOf(InvalidClaimException.class);
    }

    @Test
    void normalize_rejects_null() {
        assertThatThrownBy(() -> Money.normalize(null)).isInstanceOf(InvalidClaimException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "2.525,2.52",  // tie, preceding digit even -> stays (HALF_UP would give 2.53)
            "2.535,2.54",  // tie, preceding digit odd  -> rounds up
            "2.5251,2.53", // above tie
            "2.5249,2.52"  // below tie
    })
    void round_uses_half_even(String input, String expected) {
        assertThat(Money.round(new BigDecimal(input))).hasToString(expected);
    }

    @Test
    void multiply_applies_rate_with_half_even_and_scale_two() {
        assertThat(Money.multiply(new BigDecimal("41.25"), new BigDecimal("0.80"))).hasToString("33.00");
    }

    @Test
    void zero_has_scale_two() {
        assertThat(Money.ZERO).hasToString("0.00");
    }
}
