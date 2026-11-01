package com.carent.claims.adapter.out.persistence;

import com.carent.claims.domain.model.BenefitCategory;
import com.carent.claims.domain.model.ClaimStatus;
import com.carent.claims.domain.model.DenialReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model only: no business rules. Mapped to and from the domain by {@link ClaimMapper}. */
@Entity
@Table(name = "claim")
class ClaimJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_reference", nullable = false, updatable = false, length = 32)
    private String claimReference;

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "provider_id", nullable = false, length = 64)
    private String providerId;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "billed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal billedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_category", nullable = false, length = 40)
    private BenefitCategory benefitCategory;

    @Column(name = "adjudicated_amount", precision = 12, scale = 2)
    private BigDecimal adjudicatedAmount;

    @Column(name = "patient_co_pay", precision = 12, scale = 2)
    private BigDecimal patientCoPay;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClaimStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "denial_reason", length = 40)
    private DenialReason denialReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClaimJpaEntity() {
        // required by JPA
    }

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    String getClaimReference() {
        return claimReference;
    }

    void setClaimReference(String claimReference) {
        this.claimReference = claimReference;
    }

    String getMemberId() {
        return memberId;
    }

    void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    String getProviderId() {
        return providerId;
    }

    void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    LocalDate getServiceDate() {
        return serviceDate;
    }

    void setServiceDate(LocalDate serviceDate) {
        this.serviceDate = serviceDate;
    }

    BigDecimal getBilledAmount() {
        return billedAmount;
    }

    void setBilledAmount(BigDecimal billedAmount) {
        this.billedAmount = billedAmount;
    }

    BenefitCategory getBenefitCategory() {
        return benefitCategory;
    }

    void setBenefitCategory(BenefitCategory benefitCategory) {
        this.benefitCategory = benefitCategory;
    }

    BigDecimal getAdjudicatedAmount() {
        return adjudicatedAmount;
    }

    void setAdjudicatedAmount(BigDecimal adjudicatedAmount) {
        this.adjudicatedAmount = adjudicatedAmount;
    }

    BigDecimal getPatientCoPay() {
        return patientCoPay;
    }

    void setPatientCoPay(BigDecimal patientCoPay) {
        this.patientCoPay = patientCoPay;
    }

    ClaimStatus getStatus() {
        return status;
    }

    void setStatus(ClaimStatus status) {
        this.status = status;
    }

    DenialReason getDenialReason() {
        return denialReason;
    }

    void setDenialReason(DenialReason denialReason) {
        this.denialReason = denialReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
