package com.carent.claims.domain.model;

import com.carent.claims.domain.exception.InvalidClaimException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money rules in one place: scale 2, {@link RoundingMode#HALF_EVEN}, never double/float.
 * Amounts are plain {@link BigDecimal}s; this class is the only place that normalises or rounds them.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    /** Normalises caller input to scale 2. Rejects values carrying more than two significant decimals. */
    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            throw new InvalidClaimException("amount is required");
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new InvalidClaimException("amount must have at most 2 decimal places");
        }
        return value.setScale(SCALE, ROUNDING);
    }

    /** Rounds a computed value to scale 2 using HALF_EVEN (banker's rounding). */
    public static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    /** {@code amount x rate}, rounded once at the end. */
    public static BigDecimal multiply(BigDecimal amount, BigDecimal rate) {
        return round(amount.multiply(rate));
    }
}
