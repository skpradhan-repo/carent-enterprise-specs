package com.carent.claims.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carent.claims.domain.model.BenefitCategory;
import com.carent.claims.domain.model.Claim;
import com.carent.claims.domain.model.ClaimReference;
import com.carent.claims.domain.model.ClaimStatus;
import com.carent.claims.domain.model.DenialReason;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

/** Runs the real Flyway migration against H2 in PostgreSQL mode; Hibernate only validates the schema. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import(ClaimPersistenceAdapter.class)
class ClaimPersistenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 10, 25);

    private final ClaimPersistenceAdapter adapter;

    ClaimPersistenceAdapterTest(ClaimPersistenceAdapter adapter) {
        this.adapter = adapter;
    }

    private static Claim claim(String reference, String member, String provider, LocalDate serviceDate,
                               String billed, ClaimStatus status, Instant createdAt) {
        boolean decided = status == ClaimStatus.APPROVED;
        return Claim.reconstitute(null, ClaimReference.of(reference), member, provider, serviceDate,
                new BigDecimal(billed), BenefitCategory.IN_NETWORK_PRIMARY_CARE, status,
                decided ? new BigDecimal("120.00") : null, decided ? new BigDecimal("30.00") : null, null,
                createdAt, createdAt);
    }

    private static Claim approved(String reference, Instant createdAt) {
        return claim(reference, "MEM-99201", "PRV-44102", SERVICE_DATE, "150.00", ClaimStatus.APPROVED, createdAt);
    }

    // ---- round trip ------------------------------------------------------------------------

    @Test
    void save_assigns_an_id_and_round_trips_every_field_with_scale_two_money() {
        Claim saved = adapter.save(approved("CLM-20261031-891023", NOW));

        assertThat(saved.id()).isNotNull();

        Claim loaded = adapter.findByClaimReference(ClaimReference.of("CLM-20261031-891023")).orElseThrow();
        assertThat(loaded.id()).isEqualTo(saved.id());
        assertThat(loaded.memberId()).isEqualTo("MEM-99201");
        assertThat(loaded.providerId()).isEqualTo("PRV-44102");
        assertThat(loaded.serviceDate()).isEqualTo(SERVICE_DATE);
        assertThat(loaded.billedAmount()).hasToString("150.00");
        assertThat(loaded.benefitCategory()).isEqualTo(BenefitCategory.IN_NETWORK_PRIMARY_CARE);
        assertThat(loaded.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(loaded.adjudicatedAmount()).hasToString("120.00");
        assertThat(loaded.patientCoPay()).hasToString("30.00");
        assertThat(loaded.denialReason()).isNull();
        assertThat(loaded.createdAt()).isEqualTo(NOW);
        assertThat(loaded.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void denial_reason_and_zero_amounts_round_trip() {
        Claim denied = Claim.reconstitute(null, ClaimReference.of("CLM-20261031-000001"), "MEM-99201",
                "PRV-44102", SERVICE_DATE, new BigDecimal("500.00"), BenefitCategory.EXCLUDED_COSMETIC,
                ClaimStatus.DENIED, new BigDecimal("0.00"), new BigDecimal("500.00"),
                DenialReason.EXCLUDED_SERVICE, NOW, NOW);
        adapter.save(denied);

        Claim loaded = adapter.findByClaimReference(ClaimReference.of("CLM-20261031-000001")).orElseThrow();

        assertThat(loaded.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(loaded.denialReason()).isEqualTo(DenialReason.EXCLUDED_SERVICE);
        assertThat(loaded.adjudicatedAmount()).hasToString("0.00");
        assertThat(loaded.patientCoPay()).hasToString("500.00");
    }

    @Test
    void under_review_claim_round_trips_with_null_amounts() {
        adapter.save(claim("CLM-20261031-000002", "MEM-99201", "PRV-44102", SERVICE_DATE, "150.00",
                ClaimStatus.UNDER_REVIEW, NOW));

        Claim loaded = adapter.findByClaimReference(ClaimReference.of("CLM-20261031-000002")).orElseThrow();

        assertThat(loaded.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(loaded.adjudicatedAmount()).isNull();
        assertThat(loaded.patientCoPay()).isNull();
    }

    @Test
    void saving_a_claim_that_already_has_an_id_updates_the_row() {
        Claim saved = adapter.save(claim("CLM-20261031-000003", "MEM-99201", "PRV-44102", SERVICE_DATE,
                "150.00", ClaimStatus.PENDING, NOW));

        Claim reloaded = Claim.reconstitute(saved.id(), saved.claimReference(), saved.memberId(),
                saved.providerId(), saved.serviceDate(), saved.billedAmount(), saved.benefitCategory(),
                ClaimStatus.APPROVED, new BigDecimal("120.00"), new BigDecimal("30.00"), null,
                saved.createdAt(), NOW.plusSeconds(5));
        adapter.save(reloaded);

        Claim loaded = adapter.findByClaimReference(ClaimReference.of("CLM-20261031-000003")).orElseThrow();
        assertThat(loaded.id()).isEqualTo(saved.id());
        assertThat(loaded.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(loaded.updatedAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void unknown_reference_is_empty() {
        assertThat(adapter.findByClaimReference(ClaimReference.of("CLM-20261031-999999"))).isEmpty();
    }

    // ---- reference uniqueness --------------------------------------------------------------

    @Test
    void existsByClaimReference_reflects_stored_claims() {
        adapter.save(approved("CLM-20261031-891023", NOW));

        assertThat(adapter.existsByClaimReference(ClaimReference.of("CLM-20261031-891023"))).isTrue();
        assertThat(adapter.existsByClaimReference(ClaimReference.of("CLM-20261031-891024"))).isFalse();
    }

    @Test
    void database_enforces_unique_claim_reference() {
        adapter.save(approved("CLM-20261031-891023", NOW));

        assertThatThrownBy(() -> adapter.save(approved("CLM-20261031-891023", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- R6 duplicate query ----------------------------------------------------------------

    private boolean duplicate(String member, String provider, LocalDate date, String billed, Instant since) {
        return adapter.existsRecentDuplicate(member, provider, date, new BigDecimal(billed), since);
    }

    @Test
    void duplicate_found_when_all_four_fields_match_inside_the_window() {
        adapter.save(approved("CLM-20261031-100001", NOW.minusSeconds(3600)));

        assertThat(duplicate("MEM-99201", "PRV-44102", SERVICE_DATE, "150.00", NOW.minusSeconds(86_400)))
                .isTrue();
    }

    @Test
    void duplicate_window_is_inclusive_of_the_boundary_instant_and_excludes_older_claims() {
        Instant since = NOW.minusSeconds(86_400);
        adapter.save(approved("CLM-20261031-100002", since));
        assertThat(duplicate("MEM-99201", "PRV-44102", SERVICE_DATE, "150.00", since)).isTrue();

        assertThat(duplicate("MEM-99201", "PRV-44102", SERVICE_DATE, "150.00", since.plusMillis(1))).isFalse();
    }

    @Test
    void any_differing_field_means_no_duplicate() {
        adapter.save(approved("CLM-20261031-100003", NOW.minusSeconds(60)));
        Instant since = NOW.minusSeconds(86_400);

        assertThat(duplicate("MEM-00000", "PRV-44102", SERVICE_DATE, "150.00", since)).isFalse();
        assertThat(duplicate("MEM-99201", "PRV-00000", SERVICE_DATE, "150.00", since)).isFalse();
        assertThat(duplicate("MEM-99201", "PRV-44102", SERVICE_DATE.plusDays(1), "150.00", since)).isFalse();
        assertThat(duplicate("MEM-99201", "PRV-44102", SERVICE_DATE, "150.01", since)).isFalse();
    }

    @Test
    void pending_and_under_review_count_as_duplicates() {
        adapter.save(claim("CLM-20261031-100004", "MEM-1", "PRV-1", SERVICE_DATE, "10.00",
                ClaimStatus.PENDING, NOW.minusSeconds(60)));
        adapter.save(claim("CLM-20261031-100005", "MEM-2", "PRV-1", SERVICE_DATE, "10.00",
                ClaimStatus.UNDER_REVIEW, NOW.minusSeconds(60)));
        Instant since = NOW.minusSeconds(86_400);

        assertThat(duplicate("MEM-1", "PRV-1", SERVICE_DATE, "10.00", since)).isTrue();
        assertThat(duplicate("MEM-2", "PRV-1", SERVICE_DATE, "10.00", since)).isTrue();
    }

    @Test
    void denied_and_reversed_claims_do_not_count_as_duplicates() {
        adapter.save(claim("CLM-20261031-100006", "MEM-3", "PRV-1", SERVICE_DATE, "10.00",
                ClaimStatus.DENIED, NOW.minusSeconds(60)));
        adapter.save(claim("CLM-20261031-100007", "MEM-4", "PRV-1", SERVICE_DATE, "10.00",
                ClaimStatus.REVERSED, NOW.minusSeconds(60)));
        Instant since = NOW.minusSeconds(86_400);

        assertThat(duplicate("MEM-3", "PRV-1", SERVICE_DATE, "10.00", since)).isFalse();
        assertThat(duplicate("MEM-4", "PRV-1", SERVICE_DATE, "10.00", since)).isFalse();
    }
}
