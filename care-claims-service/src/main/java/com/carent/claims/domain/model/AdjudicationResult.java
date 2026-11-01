package com.carent.claims.domain.model;

import java.math.BigDecimal;

/**
 * Outcome of adjudication, applied to a {@link Claim} via {@link Claim#applyAdjudication}.
 *
 * @param adjudicatedAmount plan payment; {@code null} while the claim is only {@code UNDER_REVIEW}
 * @param patientCoPay      member responsibility; {@code null} while {@code UNDER_REVIEW}
 * @param accumulatorDelta  change to the member's YTD adjudicated total (scale 2, zero unless approved)
 */
public record AdjudicationResult(
        ClaimStatus status,
        BigDecimal adjudicatedAmount,
        BigDecimal patientCoPay,
        DenialReason denialReason,
        BigDecimal accumulatorDelta) {
}
