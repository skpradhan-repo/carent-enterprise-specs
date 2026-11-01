package com.carent.claims.domain.service;

import static com.carent.claims.domain.ClaimFixtures.CLOCK;
import static com.carent.claims.domain.ClaimFixtures.TODAY;
import static com.carent.claims.domain.ClaimFixtures.pending;
import static org.assertj.core.api.Assertions.assertThat;

import com.carent.claims.domain.model.AdjudicationResult;
import com.carent.claims.domain.model.BenefitCategory;
import com.carent.claims.domain.model.Claim;
import com.carent.claims.domain.model.ClaimStatus;
import com.carent.claims.domain.model.DenialReason;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Test oracle: specs/03-claims-adjudication-spec.md. Every money assertion compares strings so that
 * both value and scale are checked; no double/float anywhere.
 */
class AdjudicationEngineTest {

    private final AdjudicationEngine engine = new AdjudicationEngine(CLOCK);

    private AdjudicationResult adjudicate(Claim claim) {
        return engine.adjudicate(claim, false);
    }

    private static void assertMoney(Claim claim, String adjudicated, String coPay) {
        assertThat(claim.adjudicatedAmount()).hasToString(adjudicated);
        assertThat(claim.patientCoPay()).hasToString(coPay);
    }

    // ---- spec examples ---------------------------------------------------------------------

    @Test
    void E1_in_network_primary_care() {
        Claim claim = pending(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150.00");

        adjudicate(claim);

        assertMoney(claim, "120.00", "30.00");
        assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.denialReason()).isNull();
    }

    @Test
    void E2_out_of_network_specialist() {
        Claim claim = pending(BenefitCategory.OUT_OF_NETWORK_SPECIALIST, "300.00");

        adjudicate(claim);

        assertMoney(claim, "150.00", "150.00");
        assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void E3_preventive_is_fully_covered() {
        Claim claim = pending(BenefitCategory.PREVENTIVE_SCREENING, "200.00");

        adjudicate(claim);

        assertMoney(claim, "200.00", "0.00");
        assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void E4_excluded_cosmetic_is_denied_with_full_member_responsibility() {
        Claim claim = pending(BenefitCategory.EXCLUDED_COSMETIC, "500.00");

        adjudicate(claim);

        assertMoney(claim, "0.00", "500.00");
        assertThat(claim.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(claim.denialReason()).isEqualTo(DenialReason.EXCLUDED_SERVICE);
    }

    // E5 (4.99 -> HTTP 400) and E9 (future date -> 400) are boundary rules; E9 domain half is in ClaimTest.
    // E5 is enforced by Bean Validation in the web adapter (Slice 3), per R1 "rejected at the API boundary".

    @Test
    void E6_exactly_minimum_general_medical() {
        Claim claim = pending(BenefitCategory.GENERAL_MEDICAL, "5.00");

        adjudicate(claim);

        assertMoney(claim, "3.50", "1.50");
        assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void E7_timely_filing_exceeded_400_days() {
        Claim claim = pending(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "100.00", TODAY.minusDays(400));

        adjudicate(claim);

        assertMoney(claim, "0.00", "100.00");
        assertThat(claim.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(claim.denialReason()).isEqualTo(DenialReason.TIMELY_FILING_EXCEEDED);
    }

    @Test
    void E8_explicit_case_41_25_at_80_percent() {
        Claim claim = pending(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "41.25");

        adjudicate(claim);

        assertMoney(claim, "33.00", "8.25");
    }

    // ---- additions NOT in the spec: HALF_EVEN tie-breaking (spec E8 first row is an unfinished placeholder)

    @ParameterizedTest(name = "{0} billed {1} -> adjudicated {2}, co-pay {3}")
    @CsvSource({
            // exact tie, preceding digit even: HALF_EVEN keeps 6.12 (HALF_UP would give 6.13)
            "OUT_OF_NETWORK_SPECIALIST,12.25,6.12,6.13",
            // exact tie, preceding digit odd: rounds up to the even neighbour 6.18
            "OUT_OF_NETWORK_SPECIALIST,12.35,6.18,6.17",
            // 10.05 * 0.70 = 7.035 -> 7.04 (odd 3 rounds up)
            "GENERAL_MEDICAL,10.05,7.04,3.01",
            // 10.15 * 0.70 = 7.105 -> 7.10 (even 0 stays)
            "GENERAL_MEDICAL,10.15,7.10,3.05"
    })
    void rounding_uses_half_even_on_exact_ties(BenefitCategory category, String billed, String adjudicated,
                                               String coPay) {
        Claim claim = pending(category, billed);

        adjudicate(claim);

        assertMoney(claim, adjudicated, coPay);
    }

    @ParameterizedTest
    @EnumSource(BenefitCategory.class)
    void adjudicated_plus_copay_always_equals_billed(BenefitCategory category) {
        for (String billed : new String[] {"5.00", "5.01", "12.25", "41.25", "99.99", "1234567.89"}) {
            Claim claim = pending(category, billed);

            adjudicate(claim);

            assertThat(claim.adjudicatedAmount().add(claim.patientCoPay())).isEqualByComparingTo(billed);
            assertThat(claim.adjudicatedAmount().scale()).isEqualTo(2);
            assertThat(claim.patientCoPay().scale()).isEqualTo(2);
        }
    }

    // ---- timely filing edges (R2): "more than 365 days before today" -----------------------

    @Test
    void service_exactly_365_days_ago_is_still_timely() {
        Claim claim = pending(BenefitCategory.GENERAL_MEDICAL, "100.00", TODAY.minusDays(365));

        adjudicate(claim);

        assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void service_366_days_ago_is_denied() {
        Claim claim = pending(BenefitCategory.GENERAL_MEDICAL, "100.00", TODAY.minusDays(366));

        adjudicate(claim);

        assertThat(claim.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(claim.denialReason()).isEqualTo(DenialReason.TIMELY_FILING_EXCEEDED);
    }

    // ---- duplicate soft-check (R6) and rule precedence ---------------------------------------

    @Test
    void R6_suspected_duplicate_goes_under_review_without_amounts() {
        Claim claim = pending(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150.00");

        AdjudicationResult result = engine.adjudicate(claim, true);

        assertThat(claim.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(claim.adjudicatedAmount()).isNull();
        assertThat(claim.patientCoPay()).isNull();
        assertThat(result.accumulatorDelta()).hasToString("0.00");
    }

    @Test
    void timely_filing_denial_takes_precedence_over_duplicate() {
        Claim claim = pending(BenefitCategory.GENERAL_MEDICAL, "100.00", TODAY.minusDays(400));

        engine.adjudicate(claim, true);

        assertThat(claim.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(claim.denialReason()).isEqualTo(DenialReason.TIMELY_FILING_EXCEEDED);
    }

    @Test
    void excluded_denial_takes_precedence_over_duplicate() {
        Claim claim = pending(BenefitCategory.EXCLUDED_COSMETIC, "100.00");

        engine.adjudicate(claim, true);

        assertThat(claim.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(claim.denialReason()).isEqualTo(DenialReason.EXCLUDED_SERVICE);
    }

    @Test
    void timely_filing_takes_precedence_over_excluded_service() {
        Claim claim = pending(BenefitCategory.EXCLUDED_COSMETIC, "100.00", TODAY.minusDays(400));

        adjudicate(claim);

        assertThat(claim.denialReason()).isEqualTo(DenialReason.TIMELY_FILING_EXCEEDED);
        assertMoney(claim, "0.00", "100.00");
    }

    // ---- accumulator delta (specs/02: "optional accumulator delta") --------------------------

    @Test
    void accumulator_delta_is_adjudicated_amount_when_approved() {
        Claim claim = pending(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150.00");

        AdjudicationResult result = adjudicate(claim);

        assertThat(result.accumulatorDelta()).hasToString("120.00");
    }

    @Test
    void accumulator_delta_is_zero_when_denied() {
        Claim claim = pending(BenefitCategory.EXCLUDED_COSMETIC, "500.00");

        AdjudicationResult result = adjudicate(claim);

        assertThat(result.accumulatorDelta()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.accumulatorDelta().scale()).isEqualTo(2);
    }
}
