package com.carent.claims.domain.service;

import com.carent.claims.domain.model.AdjudicationResult;
import com.carent.claims.domain.model.Claim;
import com.carent.claims.domain.model.ClaimStatus;
import com.carent.claims.domain.model.DenialReason;
import com.carent.claims.domain.model.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Pure domain service implementing specs/03-claims-adjudication-spec.md.
 *
 * <p>Rule precedence: timely filing (R2) &gt; excluded service (R5) &gt; suspected duplicate (R6) &gt;
 * coverage by category (R3, R4, R7). Denials win over duplicate review because a denied claim never pays.
 */
public class AdjudicationEngine {

    static final int TIMELY_FILING_DAYS = 365;

    private final Clock clock;

    public AdjudicationEngine(Clock clock) {
        this.clock = clock;
    }

    /**
     * Adjudicates and applies the outcome to the claim.
     *
     * @param suspectedDuplicate result of the duplicate look-up (rule R6), supplied by the application layer
     */
    public AdjudicationResult adjudicate(Claim claim, boolean suspectedDuplicate) {
        AdjudicationResult result = decide(claim, suspectedDuplicate);
        claim.applyAdjudication(result, clock);
        return result;
    }

    private AdjudicationResult decide(Claim claim, boolean suspectedDuplicate) {
        BigDecimal billed = claim.billedAmount();
        if (claim.serviceDate().isBefore(LocalDate.now(clock).minusDays(TIMELY_FILING_DAYS))) {
            return denied(billed, DenialReason.TIMELY_FILING_EXCEEDED);
        }
        BigDecimal rate = claim.benefitCategory().coverageRate();
        if (rate.signum() == 0) {
            return denied(billed, DenialReason.EXCLUDED_SERVICE);
        }
        if (suspectedDuplicate) {
            return new AdjudicationResult(ClaimStatus.UNDER_REVIEW, null, null, null, Money.ZERO);
        }
        BigDecimal adjudicated = Money.multiply(billed, rate);
        BigDecimal coPay = Money.round(billed.subtract(adjudicated));
        return new AdjudicationResult(ClaimStatus.APPROVED, adjudicated, coPay, null, adjudicated);
    }

    private static AdjudicationResult denied(BigDecimal billed, DenialReason reason) {
        return new AdjudicationResult(ClaimStatus.DENIED, Money.ZERO, billed, reason, Money.ZERO);
    }
}
