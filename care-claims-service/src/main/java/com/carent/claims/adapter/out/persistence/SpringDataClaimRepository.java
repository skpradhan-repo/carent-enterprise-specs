package com.carent.claims.adapter.out.persistence;

import com.carent.claims.domain.model.ClaimStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataClaimRepository extends JpaRepository<ClaimJpaEntity, Long> {

    Optional<ClaimJpaEntity> findByClaimReference(String claimReference);

    boolean existsByClaimReference(String claimReference);

    @Query("""
            select count(c) > 0 from ClaimJpaEntity c
            where c.memberId = :memberId
              and c.providerId = :providerId
              and c.serviceDate = :serviceDate
              and c.billedAmount = :billedAmount
              and c.createdAt >= :createdSince
              and c.status in :statuses
            """)
    boolean existsRecentDuplicate(@Param("memberId") String memberId,
                                  @Param("providerId") String providerId,
                                  @Param("serviceDate") LocalDate serviceDate,
                                  @Param("billedAmount") BigDecimal billedAmount,
                                  @Param("createdSince") Instant createdSince,
                                  @Param("statuses") Collection<ClaimStatus> statuses);
}
