package com.carent.claims.application.port.out;

import com.carent.claims.domain.model.Claim;
import com.carent.claims.domain.model.ClaimReference;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** Outbound port for claim storage. Implemented by the persistence adapter. */
public interface ClaimRepository {

    /** Inserts (no id) or updates (id present) and returns the stored claim, with its id assigned. */
    Claim save(Claim claim);

    Optional<Claim> findByClaimReference(ClaimReference claimReference);

    boolean existsByClaimReference(ClaimReference claimReference);

    /**
     * Rule R6: is there a live claim (PENDING, UNDER_REVIEW or APPROVED) with the same member, provider,
     * service date and billed amount that was created at or after {@code createdSince}?
     */
    boolean existsRecentDuplicate(String memberId, String providerId, LocalDate serviceDate,
                                  BigDecimal billedAmount, Instant createdSince);
}
