package com.carent.claims.domain.model;

import static com.carent.claims.domain.ClaimFixtures.CLOCK;
import static com.carent.claims.domain.ClaimFixtures.REFERENCE;
import static com.carent.claims.domain.ClaimFixtures.TODAY;
import static com.carent.claims.domain.ClaimFixtures.pending;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carent.claims.domain.exception.IllegalClaimStateException;
import com.carent.claims.domain.exception.InvalidClaimException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ClaimTest {

    private static final Clock LATER = Clock.fixed(Instant.parse("2026-10-31T12:30:00Z"), ZoneOffset.UTC);

    private static AdjudicationResult approved(String adjudicated, String coPay) {
        return new AdjudicationResult(ClaimStatus.APPROVED, new BigDecimal(adjudicated), new BigDecimal(coPay),
                null, new BigDecimal(adjudicated));
    }

    // ---- creation / invariants -------------------------------------------------------------

    @Test
    void submit_creates_pending_claim_with_normalised_amount_and_timestamps() {
        Claim claim = pending(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150");

        assertThat(claim.claimReference()).isEqualTo(REFERENCE);
        assertThat(claim.status()).isEqualTo(ClaimStatus.PENDING);
        assertThat(claim.billedAmount()).hasToString("150.00");
        assertThat(claim.adjudicatedAmount()).isNull();
        assertThat(claim.patientCoPay()).isNull();
        assertThat(claim.denialReason()).isNull();
        assertThat(claim.createdAt()).isEqualTo(CLOCK.instant());
        assertThat(claim.updatedAt()).isEqualTo(CLOCK.instant());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1.00", "-0.01"})
    void submit_rejects_non_positive_billed_amount(String amount) {
        assertThatThrownBy(() -> pending(BenefitCategory.GENERAL_MEDICAL, amount))
                .isInstanceOf(InvalidClaimException.class);
    }

    @Test
    void submit_rejects_billed_amount_with_more_than_two_decimals() {
        assertThatThrownBy(() -> pending(BenefitCategory.GENERAL_MEDICAL, "10.005"))
                .isInstanceOf(InvalidClaimException.class);
    }

    @Test
    void submit_accepts_service_date_of_today() {
        assertThat(pending(BenefitCategory.GENERAL_MEDICAL, "10.00", TODAY).serviceDate()).isEqualTo(TODAY);
    }

    @Test
    void submit_rejects_future_service_date_E9() {
        assertThatThrownBy(() -> pending(BenefitCategory.GENERAL_MEDICAL, "10.00", TODAY.plusDays(1)))
                .isInstanceOf(InvalidClaimException.class);
    }

    @Test
    void submit_rejects_missing_service_date_and_category() {
        assertThatThrownBy(() -> pending(BenefitCategory.GENERAL_MEDICAL, "10.00", null))
                .isInstanceOf(InvalidClaimException.class);
        assertThatThrownBy(() -> pending(null, "10.00")).isInstanceOf(InvalidClaimException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void submit_rejects_blank_member_and_provider(String blank) {
        assertThatThrownBy(() -> Claim.submit(REFERENCE, blank, "PRV-44102", TODAY, new BigDecimal("10.00"),
                BenefitCategory.GENERAL_MEDICAL, CLOCK)).isInstanceOf(InvalidClaimException.class);
        assertThatThrownBy(() -> Claim.submit(REFERENCE, "MEM-99201", blank, TODAY, new BigDecimal("10.00"),
                BenefitCategory.GENERAL_MEDICAL, CLOCK)).isInstanceOf(InvalidClaimException.class);
    }

    // ---- applying an adjudication result ---------------------------------------------------

    @Test
    void approved_result_sets_amounts_status_and_bumps_updated_at() {
        Claim claim = pending(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150.00");

        claim.applyAdjudication(approved("120.00", "30.00"), LATER);

        assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.adjudicatedAmount()).hasToString("120.00");
        assertThat(claim.patientCoPay()).hasToString("30.00");
        assertThat(claim.createdAt()).isEqualTo(CLOCK.instant());
        assertThat(claim.updatedAt()).isEqualTo(LATER.instant());
    }

    @Test
    void denied_result_records_reason() {
        Claim claim = pending(BenefitCategory.EXCLUDED_COSMETIC, "500.00");

        claim.applyAdjudication(new AdjudicationResult(ClaimStatus.DENIED, new BigDecimal("0.00"),
                new BigDecimal("500.00"), DenialReason.EXCLUDED_SERVICE, new BigDecimal("0.00")), LATER);

        assertThat(claim.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(claim.denialReason()).isEqualTo(DenialReason.EXCLUDED_SERVICE);
    }

    @Test
    void under_review_result_leaves_amounts_unset() {
        Claim claim = pending(BenefitCategory.GENERAL_MEDICAL, "50.00");

        claim.applyAdjudication(new AdjudicationResult(ClaimStatus.UNDER_REVIEW, null, null, null,
                new BigDecimal("0.00")), LATER);

        assertThat(claim.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(claim.adjudicatedAmount()).isNull();
        assertThat(claim.patientCoPay()).isNull();
    }

    @Test
    void invariant_adjudicated_plus_copay_must_equal_billed() {
        Claim claim = pending(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150.00");

        assertThatThrownBy(() -> claim.applyAdjudication(approved("120.00", "31.00"), LATER))
                .isInstanceOf(InvalidClaimException.class);
        assertThat(claim.status()).isEqualTo(ClaimStatus.PENDING);
    }

    // ---- state machine (specs/02 section 5) ------------------------------------------------

    @Test
    void under_review_claim_can_be_approved() {
        Claim claim = pending(BenefitCategory.GENERAL_MEDICAL, "10.00");
        claim.applyAdjudication(new AdjudicationResult(ClaimStatus.UNDER_REVIEW, null, null, null,
                new BigDecimal("0.00")), LATER);

        claim.applyAdjudication(approved("7.00", "3.00"), LATER);

        assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void pending_claim_cannot_be_applied_a_pending_result() {
        Claim claim = pending(BenefitCategory.GENERAL_MEDICAL, "10.00");

        assertThatThrownBy(() -> claim.applyAdjudication(new AdjudicationResult(ClaimStatus.PENDING, null, null,
                null, new BigDecimal("0.00")), LATER)).isInstanceOf(IllegalClaimStateException.class);
    }

    @Test
    void approved_claim_cannot_be_adjudicated_again() {
        Claim claim = pending(BenefitCategory.GENERAL_MEDICAL, "10.00");
        claim.applyAdjudication(approved("7.00", "3.00"), LATER);

        assertThatThrownBy(() -> claim.applyAdjudication(approved("7.00", "3.00"), LATER))
                .isInstanceOf(IllegalClaimStateException.class);
    }

    @Test
    void denied_claim_is_terminal() {
        Claim claim = pending(BenefitCategory.EXCLUDED_COSMETIC, "10.00");
        claim.applyAdjudication(new AdjudicationResult(ClaimStatus.DENIED, new BigDecimal("0.00"),
                new BigDecimal("10.00"), DenialReason.EXCLUDED_SERVICE, new BigDecimal("0.00")), LATER);

        assertThatThrownBy(() -> claim.applyAdjudication(approved("7.00", "3.00"), LATER))
                .isInstanceOf(IllegalClaimStateException.class);
        assertThatThrownBy(() -> claim.reverse(LATER)).isInstanceOf(IllegalClaimStateException.class);
    }

    // ---- reversal (R9) ---------------------------------------------------------------------

    @Test
    void approved_claim_can_be_reversed_and_returns_previous_adjudicated_amount() {
        Claim claim = pending(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150.00");
        claim.applyAdjudication(approved("120.00", "30.00"), LATER);

        BigDecimal previous = claim.reverse(LATER);

        assertThat(previous).hasToString("120.00");
        assertThat(claim.status()).isEqualTo(ClaimStatus.REVERSED);
    }

    @Test
    void pending_claim_cannot_be_reversed() {
        Claim claim = pending(BenefitCategory.GENERAL_MEDICAL, "10.00");

        assertThatThrownBy(() -> claim.reverse(LATER)).isInstanceOf(IllegalClaimStateException.class);
    }

    // ---- reconstitution from storage -------------------------------------------------------

    @Test
    void reconstitute_restores_every_field_without_re_running_submission_rules() {
        Instant created = Instant.parse("2024-01-05T08:00:00Z");
        Instant updated = Instant.parse("2024-01-05T08:00:01Z");

        // service date years in the past and status DENIED: would be refused by submit()/apply...
        Claim claim = Claim.reconstitute(42L, REFERENCE, "MEM-99201", "PRV-44102", TODAY.minusYears(2),
                new BigDecimal("100.00"), BenefitCategory.GENERAL_MEDICAL, ClaimStatus.DENIED,
                new BigDecimal("0.00"), new BigDecimal("100.00"), DenialReason.TIMELY_FILING_EXCEEDED,
                created, updated);

        assertThat(claim.id()).isEqualTo(42L);
        assertThat(claim.claimReference()).isEqualTo(REFERENCE);
        assertThat(claim.serviceDate()).isEqualTo(TODAY.minusYears(2));
        assertThat(claim.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(claim.adjudicatedAmount()).hasToString("0.00");
        assertThat(claim.patientCoPay()).hasToString("100.00");
        assertThat(claim.denialReason()).isEqualTo(DenialReason.TIMELY_FILING_EXCEEDED);
        assertThat(claim.createdAt()).isEqualTo(created);
        assertThat(claim.updatedAt()).isEqualTo(updated);
    }

    @Test
    void new_claim_has_no_id_until_persisted() {
        assertThat(pending(BenefitCategory.GENERAL_MEDICAL, "10.00").id()).isNull();
    }

    @Test
    void reversed_claim_cannot_return_to_approved_or_be_reversed_again() {
        Claim claim = pending(BenefitCategory.GENERAL_MEDICAL, "10.00");
        claim.applyAdjudication(approved("7.00", "3.00"), LATER);
        claim.reverse(LATER);

        assertThatThrownBy(() -> claim.applyAdjudication(approved("7.00", "3.00"), LATER))
                .isInstanceOf(IllegalClaimStateException.class);
        assertThatThrownBy(() -> claim.reverse(LATER)).isInstanceOf(IllegalClaimStateException.class);
    }
}
