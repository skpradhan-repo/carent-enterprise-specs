package com.carent.claims.adapter.in.web;

import com.carent.claims.adapter.in.web.dto.ClaimResponse;
import com.carent.claims.adapter.in.web.dto.ClaimSubmissionRequest;
import com.carent.claims.application.port.in.SubmitClaimCommand;
import com.carent.claims.domain.model.Claim;
import java.math.BigDecimal;

/** DTO to command / domain to DTO translation. No business rules here. */
final class ClaimWebMapper {

    private ClaimWebMapper() {
    }

    static SubmitClaimCommand toCommand(ClaimSubmissionRequest request) {
        // The request pattern guarantees a plain decimal string, so this cannot throw NumberFormatException.
        return new SubmitClaimCommand(request.memberId(), request.providerId(), request.serviceDate(),
                new BigDecimal(request.billedAmount()), request.benefitCategory());
    }

    static ClaimResponse toResponse(Claim claim) {
        return new ClaimResponse(
                claim.claimReference().value(),
                claim.memberId(),
                claim.providerId(),
                claim.serviceDate(),
                money(claim.billedAmount()),
                claim.benefitCategory(),
                money(claim.adjudicatedAmount()),
                money(claim.patientCoPay()),
                claim.status(),
                claim.denialReason() == null ? null : claim.denialReason().name());
    }

    private static String money(BigDecimal amount) {
        return amount == null ? null : amount.toPlainString();
    }
}
