package com.carent.claims.domain.model;

import com.carent.claims.domain.exception.IllegalClaimStateException;
import com.carent.claims.domain.exception.InvalidClaimException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Claim aggregate root (specs/02-domain-model-spec.md section 3). Pure Java: invariants and the
 * state machine live here; coverage maths lives in {@code AdjudicationEngine}.
 */
public class Claim {

    private static final Set<ClaimStatus> ADJUDICATION_OUTCOMES =
            Set.of(ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, ClaimStatus.DENIED);

    private final Long id;
    private final ClaimReference claimReference;
    private final String memberId;
    private final String providerId;
    private final LocalDate serviceDate;
    private final BigDecimal billedAmount;
    private final BenefitCategory benefitCategory;
    private final Instant createdAt;

    private ClaimStatus status;
    private BigDecimal adjudicatedAmount;
    private BigDecimal patientCoPay;
    private DenialReason denialReason;
    private Instant updatedAt;

    private Claim(Long id, ClaimReference claimReference, String memberId, String providerId,
                  LocalDate serviceDate, BigDecimal billedAmount, BenefitCategory benefitCategory,
                  ClaimStatus status, BigDecimal adjudicatedAmount, BigDecimal patientCoPay,
                  DenialReason denialReason, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.claimReference = claimReference;
        this.memberId = memberId;
        this.providerId = providerId;
        this.serviceDate = serviceDate;
        this.billedAmount = billedAmount;
        this.benefitCategory = benefitCategory;
        this.status = status;
        this.adjudicatedAmount = adjudicatedAmount;
        this.patientCoPay = patientCoPay;
        this.denialReason = denialReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Rebuilds a claim from storage. Deliberately skips submission rules: a stored claim may legitimately
     * have an old service date or a terminal status, and was validated when it was first created.
     */
    public static Claim reconstitute(Long id, ClaimReference claimReference, String memberId, String providerId,
                                     LocalDate serviceDate, BigDecimal billedAmount,
                                     BenefitCategory benefitCategory, ClaimStatus status,
                                     BigDecimal adjudicatedAmount, BigDecimal patientCoPay,
                                     DenialReason denialReason, Instant createdAt, Instant updatedAt) {
        return new Claim(id, claimReference, memberId, providerId, serviceDate, billedAmount, benefitCategory,
                status, adjudicatedAmount, patientCoPay, denialReason, createdAt, updatedAt);
    }

    /** Creates a new claim in {@code PENDING}. */
    public static Claim submit(ClaimReference claimReference, String memberId, String providerId,
                               LocalDate serviceDate, BigDecimal billedAmount, BenefitCategory benefitCategory,
                               Clock clock) {
        if (claimReference == null) {
            throw new InvalidClaimException("claimReference is required");
        }
        requireText(memberId, "memberId");
        requireText(providerId, "providerId");
        if (benefitCategory == null) {
            throw new InvalidClaimException("benefitCategory is required");
        }
        if (serviceDate == null) {
            throw new InvalidClaimException("serviceDate is required");
        }
        if (serviceDate.isAfter(LocalDate.now(clock))) {
            throw new InvalidClaimException("serviceDate must not be in the future");
        }
        BigDecimal billed = Money.normalize(billedAmount);
        if (billed.signum() <= 0) {
            throw new InvalidClaimException("billedAmount must be greater than zero");
        }
        Instant now = clock.instant();
        return new Claim(null, claimReference, memberId, providerId, serviceDate, billed, benefitCategory,
                ClaimStatus.PENDING, null, null, null, now, now);
    }

    /**
     * Applies an adjudication outcome. Allowed from {@code PENDING} or {@code UNDER_REVIEW} to
     * {@code UNDER_REVIEW}, {@code APPROVED} or {@code DENIED}.
     */
    public void applyAdjudication(AdjudicationResult result, Clock clock) {
        Objects.requireNonNull(result, "result");
        boolean adjudicable = status == ClaimStatus.PENDING || status == ClaimStatus.UNDER_REVIEW;
        if (!adjudicable || !ADJUDICATION_OUTCOMES.contains(result.status())) {
            throw new IllegalClaimStateException(
                    "Cannot move claim " + claimReference + " from " + status + " to " + result.status());
        }
        BigDecimal adjudicated = null;
        BigDecimal coPay = null;
        if (result.status() != ClaimStatus.UNDER_REVIEW) {
            adjudicated = Money.normalize(result.adjudicatedAmount());
            coPay = Money.normalize(result.patientCoPay());
            if (adjudicated.add(coPay).compareTo(billedAmount) != 0) {
                throw new InvalidClaimException("adjudicatedAmount + patientCoPay must equal billedAmount");
            }
            if (result.status() == ClaimStatus.DENIED && result.denialReason() == null) {
                throw new InvalidClaimException("a denied claim requires a denialReason");
            }
        }
        this.status = result.status();
        this.adjudicatedAmount = adjudicated;
        this.patientCoPay = coPay;
        this.denialReason = result.denialReason();
        this.updatedAt = clock.instant();
    }

    /**
     * Reverses an {@code APPROVED} claim (rule R9).
     *
     * @return the previously adjudicated amount, to be taken off the member accumulator
     */
    public BigDecimal reverse(Clock clock) {
        if (status != ClaimStatus.APPROVED) {
            throw new IllegalClaimStateException(
                    "Only an APPROVED claim can be reversed; claim " + claimReference + " is " + status);
        }
        this.status = ClaimStatus.REVERSED;
        this.updatedAt = clock.instant();
        return adjudicatedAmount;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidClaimException(field + " is required");
        }
    }

    /** Technical primary key; {@code null} until the claim has been persisted. */
    public Long id() {
        return id;
    }

    public ClaimReference claimReference() {
        return claimReference;
    }

    public String memberId() {
        return memberId;
    }

    public String providerId() {
        return providerId;
    }

    public LocalDate serviceDate() {
        return serviceDate;
    }

    public BigDecimal billedAmount() {
        return billedAmount;
    }

    public BenefitCategory benefitCategory() {
        return benefitCategory;
    }

    public ClaimStatus status() {
        return status;
    }

    public BigDecimal adjudicatedAmount() {
        return adjudicatedAmount;
    }

    public BigDecimal patientCoPay() {
        return patientCoPay;
    }

    public DenialReason denialReason() {
        return denialReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
