package com.carent.claims.adapter.in.web.dto;

import com.carent.claims.domain.model.BenefitCategory;
import com.carent.claims.domain.model.ClaimStatus;
import java.time.LocalDate;

/**
 * Response body of both claim endpoints; mirrors {@code ClaimResponse} in specs/openapi.yaml.
 * Money fields are plain decimal strings with scale 2; unset values serialise as {@code null}.
 */
public record ClaimResponse(
        String claimReference,
        String memberId,
        String providerId,
        LocalDate serviceDate,
        String billedAmount,
        BenefitCategory benefitCategory,
        String adjudicatedAmount,
        String patientCoPay,
        ClaimStatus status,
        String denialReason) {
}
