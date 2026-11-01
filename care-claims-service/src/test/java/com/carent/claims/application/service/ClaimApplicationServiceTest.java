package com.carent.claims.application.service;

import static com.carent.claims.domain.ClaimFixtures.CLOCK;
import static com.carent.claims.domain.ClaimFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.carent.claims.application.port.in.SubmitClaimCommand;
import com.carent.claims.application.port.out.ClaimEventPublisher;
import com.carent.claims.application.port.out.ClaimRepository;
import com.carent.claims.domain.exception.ClaimNotFoundException;
import com.carent.claims.domain.exception.InvalidClaimException;
import com.carent.claims.domain.model.BenefitCategory;
import com.carent.claims.domain.model.Claim;
import com.carent.claims.domain.model.ClaimReference;
import com.carent.claims.domain.model.ClaimStatus;
import com.carent.claims.domain.model.DenialReason;
import com.carent.claims.domain.service.AdjudicationEngine;
import com.carent.claims.domain.service.ClaimReferenceGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClaimApplicationServiceTest {

    private static final LocalDate SERVICE_DATE = TODAY.minusDays(6);

    /** Hands out the given values in order, then repeats the last one. */
    private static final class SequenceRandom extends Random {
        private final int[] values;
        private int next;

        SequenceRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            return values[Math.min(next++, values.length - 1)];
        }
    }

    @Mock
    private ClaimRepository repository;

    @Mock
    private ClaimEventPublisher events;

    private ClaimApplicationService service;

    @BeforeEach
    void setUp() {
        service = serviceWith(new SequenceRandom(891023));
    }

    private ClaimApplicationService serviceWith(Random random) {
        return new ClaimApplicationService(repository, events, new AdjudicationEngine(CLOCK),
                new ClaimReferenceGenerator(CLOCK, random), CLOCK);
    }

    private void saveReturnsArgument() {
        when(repository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static SubmitClaimCommand command(BenefitCategory category, String billed, LocalDate serviceDate) {
        return new SubmitClaimCommand("MEM-99201", "PRV-44102", serviceDate, new BigDecimal(billed), category);
    }

    private Claim savedClaim() {
        ArgumentCaptor<Claim> captor = ArgumentCaptor.forClass(Claim.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    // ---- orchestration: happy path and denials ---------------------------------------------

    @Test
    void E1_submit_generates_reference_adjudicates_and_persists_once() {
        saveReturnsArgument();

        Claim result = service.submit(command(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150.00", SERVICE_DATE));

        Claim saved = savedClaim();
        assertThat(saved).isSameAs(result);
        assertThat(saved.claimReference().value()).isEqualTo("CLM-20261031-891023");
        assertThat(saved.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(saved.adjudicatedAmount()).hasToString("120.00");
        assertThat(saved.patientCoPay()).hasToString("30.00");
    }

    @Test
    void E7_timely_filing_denial_is_persisted() {
        saveReturnsArgument();

        service.submit(command(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "100.00", TODAY.minusDays(400)));

        Claim saved = savedClaim();
        assertThat(saved.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(saved.denialReason()).isEqualTo(DenialReason.TIMELY_FILING_EXCEEDED);
        assertThat(saved.patientCoPay()).hasToString("100.00");
    }

    @Test
    void E4_excluded_service_denial_is_persisted() {
        saveReturnsArgument();

        service.submit(command(BenefitCategory.EXCLUDED_COSMETIC, "500.00", SERVICE_DATE));

        assertThat(savedClaim().denialReason()).isEqualTo(DenialReason.EXCLUDED_SERVICE);
    }

    // ---- R1 minimum threshold --------------------------------------------------------------

    @Test
    void E5_below_minimum_is_rejected_with_message_and_nothing_is_touched() {
        assertThatThrownBy(() -> service.submit(command(BenefitCategory.GENERAL_MEDICAL, "4.99", SERVICE_DATE)))
                .isInstanceOf(InvalidClaimException.class)
                .hasMessage("billedAmount must be greater than or equal to 5.00");

        verifyNoInteractions(repository);
    }

    @Test
    void E6_exactly_minimum_is_accepted() {
        saveReturnsArgument();

        service.submit(command(BenefitCategory.GENERAL_MEDICAL, "5.00", SERVICE_DATE));

        Claim saved = savedClaim();
        assertThat(saved.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(saved.adjudicatedAmount()).hasToString("3.50");
        assertThat(saved.patientCoPay()).hasToString("1.50");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-10.00", "-0.01"})
    void non_positive_amounts_are_rejected_before_any_port_is_called(String amount) {
        assertThatThrownBy(() -> service.submit(command(BenefitCategory.GENERAL_MEDICAL, amount, SERVICE_DATE)))
                .isInstanceOf(InvalidClaimException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void E9_future_service_date_is_rejected_and_nothing_is_saved() {
        assertThatThrownBy(() -> service.submit(
                command(BenefitCategory.GENERAL_MEDICAL, "50.00", TODAY.plusDays(1))))
                .isInstanceOf(InvalidClaimException.class);

        verify(repository, never()).save(any());
    }

    // ---- member / provider identifier format (specs/02: validated in the application layer) -------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"MEM-1234", "mem-99201", "PRV-99201", "99201", "MEM-99 201"})
    void member_id_must_have_MEM_prefix_and_at_least_five_characters_after_it(String memberId) {
        SubmitClaimCommand command = new SubmitClaimCommand(memberId, "PRV-44102", SERVICE_DATE,
                new BigDecimal("50.00"), BenefitCategory.GENERAL_MEDICAL);

        assertThatThrownBy(() -> service.submit(command)).isInstanceOf(InvalidClaimException.class)
                .hasMessageContaining("memberId");
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"PRV-1234", "prv-44102", "MEM-44102", "44102"})
    void provider_id_must_have_PRV_prefix_and_at_least_five_characters_after_it(String providerId) {
        SubmitClaimCommand command = new SubmitClaimCommand("MEM-99201", providerId, SERVICE_DATE,
                new BigDecimal("50.00"), BenefitCategory.GENERAL_MEDICAL);

        assertThatThrownBy(() -> service.submit(command)).isInstanceOf(InvalidClaimException.class)
                .hasMessageContaining("providerId");
        verifyNoInteractions(repository);
    }

    // ---- R6 duplicate soft-check -----------------------------------------------------------

    @Test
    void R6_duplicate_lookup_uses_a_24_hour_window_ending_now_and_normalised_amount() {
        saveReturnsArgument();
        when(repository.existsRecentDuplicate("MEM-99201", "PRV-44102", SERVICE_DATE, new BigDecimal("150.00"),
                Instant.parse("2026-10-30T10:00:00Z"))).thenReturn(true);

        service.submit(command(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150", SERVICE_DATE));

        Claim saved = savedClaim();
        assertThat(saved.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(saved.adjudicatedAmount()).isNull();
        assertThat(saved.patientCoPay()).isNull();
    }

    @Test
    void R6_no_duplicate_means_normal_approval() {
        saveReturnsArgument();
        when(repository.existsRecentDuplicate(any(), any(), any(), any(), any())).thenReturn(false);

        service.submit(command(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150.00", SERVICE_DATE));

        assertThat(savedClaim().status()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void R6_denial_wins_over_duplicate_review() {
        saveReturnsArgument();
        when(repository.existsRecentDuplicate(any(), any(), any(), any(), any())).thenReturn(true);

        service.submit(command(BenefitCategory.EXCLUDED_COSMETIC, "500.00", SERVICE_DATE));

        assertThat(savedClaim().status()).isEqualTo(ClaimStatus.DENIED);
    }

    // ---- specs/04 lifecycle events -----------------------------------------------------------

    @Test
    void approved_claim_publishes_submitted_then_adjudicated_after_the_save() {
        saveReturnsArgument();

        Claim result = service.submit(command(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150.00", SERVICE_DATE));

        InOrder order = inOrder(repository, events);
        order.verify(repository).save(result);
        order.verify(events).claimSubmitted(result);
        order.verify(events).claimAdjudicated(result);
        order.verifyNoMoreInteractions();
    }

    @Test
    void denied_claim_publishes_submitted_then_adjudicated() {
        saveReturnsArgument();

        Claim result = service.submit(command(BenefitCategory.EXCLUDED_COSMETIC, "500.00", SERVICE_DATE));

        InOrder order = inOrder(events);
        order.verify(events).claimSubmitted(result);
        order.verify(events).claimAdjudicated(result);
    }

    @Test
    void under_review_claim_publishes_submitted_only() {
        saveReturnsArgument();
        when(repository.existsRecentDuplicate(any(), any(), any(), any(), any())).thenReturn(true);

        Claim result = service.submit(command(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150.00", SERVICE_DATE));

        assertThat(result.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        verify(events).claimSubmitted(result);
        verify(events, never()).claimAdjudicated(any());
    }

    @Test
    void published_claim_is_the_one_returned_by_the_repository() {
        Claim persisted = Claim.submit(ClaimReference.of("CLM-20261031-777777"), "MEM-99201", "PRV-44102",
                SERVICE_DATE, new BigDecimal("150.00"), BenefitCategory.IN_NETWORK_PRIMARY_CARE, CLOCK);
        when(repository.save(any(Claim.class))).thenReturn(persisted);

        Claim result = service.submit(command(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150.00", SERVICE_DATE));

        assertThat(result).isSameAs(persisted);
        verify(events).claimSubmitted(persisted);
    }

    @Test
    void rejected_submission_publishes_nothing() {
        assertThatThrownBy(() -> service.submit(command(BenefitCategory.GENERAL_MEDICAL, "4.99", SERVICE_DATE)))
                .isInstanceOf(InvalidClaimException.class);

        verifyNoInteractions(events);
    }

    // ---- reference generation --------------------------------------------------------------

    @Test
    void reference_collision_is_retried_with_a_fresh_reference() {
        saveReturnsArgument();
        when(repository.existsByClaimReference(any(ClaimReference.class))).thenReturn(true, false);
        service = serviceWith(new SequenceRandom(1, 2));

        service.submit(command(BenefitCategory.GENERAL_MEDICAL, "50.00", SERVICE_DATE));

        assertThat(savedClaim().claimReference().value()).isEqualTo("CLM-20261031-000002");
        verify(repository, times(2)).existsByClaimReference(any(ClaimReference.class));
    }

    @Test
    void gives_up_after_five_colliding_references() {
        when(repository.existsByClaimReference(any(ClaimReference.class))).thenReturn(true);

        assertThatThrownBy(() -> service.submit(command(BenefitCategory.GENERAL_MEDICAL, "50.00", SERVICE_DATE)))
                .isInstanceOf(IllegalStateException.class);

        verify(repository, times(5)).existsByClaimReference(any(ClaimReference.class));
        verify(repository, never()).save(any());
    }

    // ---- query -----------------------------------------------------------------------------

    @Test
    void getByReference_returns_the_stored_claim() {
        Claim stored = Claim.submit(ClaimReference.of("CLM-20261031-891023"), "MEM-99201", "PRV-44102",
                SERVICE_DATE, new BigDecimal("150.00"), BenefitCategory.IN_NETWORK_PRIMARY_CARE, CLOCK);
        when(repository.findByClaimReference(ClaimReference.of("CLM-20261031-891023")))
                .thenReturn(Optional.of(stored));

        assertThat(service.getByReference("CLM-20261031-891023")).isSameAs(stored);
    }

    @Test
    void getByReference_throws_not_found_for_unknown_reference() {
        when(repository.findByClaimReference(any(ClaimReference.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByReference("CLM-20261031-000000"))
                .isInstanceOf(ClaimNotFoundException.class)
                .hasMessageContaining("CLM-20261031-000000");
    }

    @Test
    void getByReference_rejects_malformed_reference_without_hitting_the_repository() {
        assertThatThrownBy(() -> service.getByReference("not-a-reference"))
                .isInstanceOf(InvalidClaimException.class);

        verifyNoInteractions(repository);
    }
}
