package com.carent.claims.application.port.in;

import com.carent.claims.domain.model.BenefitCategory;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Input to {@link SubmitClaimUseCase}. Values are validated by the application service. */
public record SubmitClaimCommand(
        String memberId,
        String providerId,
        LocalDate serviceDate,
        BigDecimal billedAmount,
        BenefitCategory benefitCategory) {
}
