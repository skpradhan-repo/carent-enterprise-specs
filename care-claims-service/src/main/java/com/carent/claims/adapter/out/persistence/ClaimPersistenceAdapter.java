package com.carent.claims.adapter.out.persistence;

import com.carent.claims.application.port.out.ClaimRepository;
import com.carent.claims.domain.model.Claim;
import com.carent.claims.domain.model.ClaimReference;
import com.carent.claims.domain.model.ClaimStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Implements the {@link ClaimRepository} outbound port with Spring Data JPA. */
@Component
public class ClaimPersistenceAdapter implements ClaimRepository {

    /** Statuses that make an earlier identical claim a duplicate: denied/reversed claims never paid. */
    private static final List<ClaimStatus> LIVE_STATUSES =
            List.of(ClaimStatus.PENDING, ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED);

    private final SpringDataClaimRepository repository;

    public ClaimPersistenceAdapter(SpringDataClaimRepository repository) {
        this.repository = repository;
    }

    @Override
    public Claim save(Claim claim) {
        // saveAndFlush so constraint violations (e.g. duplicate reference) surface here, not at commit
        return ClaimMapper.toDomain(repository.saveAndFlush(ClaimMapper.toEntity(claim)));
    }

    @Override
    public Optional<Claim> findByClaimReference(ClaimReference claimReference) {
        return repository.findByClaimReference(claimReference.value()).map(ClaimMapper::toDomain);
    }

    @Override
    public boolean existsByClaimReference(ClaimReference claimReference) {
        return repository.existsByClaimReference(claimReference.value());
    }

    @Override
    public boolean existsRecentDuplicate(String memberId, String providerId, LocalDate serviceDate,
                                         BigDecimal billedAmount, Instant createdSince) {
        return repository.existsRecentDuplicate(memberId, providerId, serviceDate, billedAmount, createdSince,
                LIVE_STATUSES);
    }
}
