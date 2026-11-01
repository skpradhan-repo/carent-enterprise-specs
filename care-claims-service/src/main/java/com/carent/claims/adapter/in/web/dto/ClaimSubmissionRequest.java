package com.carent.claims.adapter.in.web.dto;

import com.carent.claims.domain.model.BenefitCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request body of {@code POST /api/v1/claims}; fields, required-ness, patterns and lengths mirror
 * {@code ClaimSubmissionRequest} in specs/openapi.yaml (verified by OpenApiContractTest).
 * Money is a string to preserve precision.
 */
public record ClaimSubmissionRequest(
        @NotBlank @Size(min = 5, message = "must be at least 5 characters") String memberId,
        @NotBlank @Size(min = 5, message = "must be at least 5 characters") String providerId,
        @NotNull LocalDate serviceDate,
        @NotNull
        @Pattern(regexp = "^\\d+(\\.\\d{1,2})?$", message = "must be a decimal string with at most 2 decimal places")
        @Digits(integer = 10, fraction = 2, message = "must have at most 10 integer digits")
        String billedAmount,
        @NotNull BenefitCategory benefitCategory) {
}
