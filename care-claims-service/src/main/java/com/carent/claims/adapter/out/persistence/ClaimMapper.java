package com.carent.claims.adapter.out.persistence;

import com.carent.claims.domain.model.Claim;
import com.carent.claims.domain.model.ClaimReference;

/** Translates between the domain aggregate and the JPA entity. */
final class ClaimMapper {

    private ClaimMapper() {
    }

    static ClaimJpaEntity toEntity(Claim claim) {
        ClaimJpaEntity entity = new ClaimJpaEntity();
        entity.setId(claim.id());
        entity.setClaimReference(claim.claimReference().value());
        entity.setMemberId(claim.memberId());
        entity.setProviderId(claim.providerId());
        entity.setServiceDate(claim.serviceDate());
        entity.setBilledAmount(claim.billedAmount());
        entity.setBenefitCategory(claim.benefitCategory());
        entity.setAdjudicatedAmount(claim.adjudicatedAmount());
        entity.setPatientCoPay(claim.patientCoPay());
        entity.setStatus(claim.status());
        entity.setDenialReason(claim.denialReason());
        entity.setCreatedAt(claim.createdAt());
        entity.setUpdatedAt(claim.updatedAt());
        return entity;
    }

    static Claim toDomain(ClaimJpaEntity entity) {
        return Claim.reconstitute(entity.getId(), ClaimReference.of(entity.getClaimReference()),
                entity.getMemberId(), entity.getProviderId(), entity.getServiceDate(), entity.getBilledAmount(),
                entity.getBenefitCategory(), entity.getStatus(), entity.getAdjudicatedAmount(),
                entity.getPatientCoPay(), entity.getDenialReason(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
