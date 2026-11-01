package com.carent.claims.application.service;

import com.carent.claims.application.port.in.GetClaimUseCase;
import com.carent.claims.application.port.in.SubmitClaimCommand;
import com.carent.claims.application.port.in.SubmitClaimUseCase;
import com.carent.claims.application.port.out.ClaimEventPublisher;
import com.carent.claims.application.port.out.ClaimRepository;
import com.carent.claims.domain.exception.ClaimNotFoundException;
import com.carent.claims.domain.exception.InvalidClaimException;
import com.carent.claims.domain.model.Claim;
import com.carent.claims.domain.model.ClaimReference;
import com.carent.claims.domain.model.ClaimStatus;
import com.carent.claims.domain.model.Money;
import com.carent.claims.domain.service.AdjudicationEngine;
import com.carent.claims.domain.service.ClaimReferenceGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Orchestrates claim submission (specs/03): boundary validation, reference generation, duplicate
 * look-up, adjudication, persistence. Framework-free; wired as a bean in the config package.
 *
 * <p>Known MVP limitation: the duplicate check and the save are not one atomic step, so two identical
 * concurrent submissions could both be approved.
 */
public class ClaimApplicationService implements SubmitClaimUseCase, GetClaimUseCase {

    static final BigDecimal MINIMUM_BILLED_AMOUNT = new BigDecimal("5.00");
    static final Duration DUPLICATE_WINDOW = Duration.ofHours(24);
    static final int MAX_REFERENCE_ATTEMPTS = 5;

    // specs/02: MEM-/PRV- prefix and at least 5 characters after it
    private static final Pattern MEMBER_ID = Pattern.compile("^MEM-[A-Za-z0-9]{5,}$");
    private static final Pattern PROVIDER_ID = Pattern.compile("^PRV-[A-Za-z0-9]{5,}$");

    private final ClaimRepository repository;
    private final ClaimEventPublisher events;
    private final AdjudicationEngine adjudicationEngine;
    private final ClaimReferenceGenerator referenceGenerator;
    private final Clock clock;

    public ClaimApplicationService(ClaimRepository repository, ClaimEventPublisher events,
                                   AdjudicationEngine adjudicationEngine,
                                   ClaimReferenceGenerator referenceGenerator, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.adjudicationEngine = adjudicationEngine;
        this.referenceGenerator = referenceGenerator;
        this.clock = clock;
    }

    @Override
    public Claim submit(SubmitClaimCommand command) {
        requireFormat(command.memberId(), MEMBER_ID, "memberId", "MEM-");
        requireFormat(command.providerId(), PROVIDER_ID, "providerId", "PRV-");
        BigDecimal billed = Money.normalize(command.billedAmount());
        if (billed.compareTo(MINIMUM_BILLED_AMOUNT) < 0) {
            throw new InvalidClaimException("billedAmount must be greater than or equal to 5.00");
        }

        Claim claim = Claim.submit(nextFreeReference(), command.memberId(), command.providerId(),
                command.serviceDate(), billed, command.benefitCategory(), clock);

        Instant windowStart = clock.instant().minus(DUPLICATE_WINDOW);
        boolean suspectedDuplicate = repository.existsRecentDuplicate(claim.memberId(), claim.providerId(),
                claim.serviceDate(), claim.billedAmount(), windowStart);

        adjudicationEngine.adjudicate(claim, suspectedDuplicate);
        Claim saved = repository.save(claim);
        publishLifecycleEvents(saved);
        return saved;
    }

    /** specs/04: SUBMITTED always; ADJUDICATED only once the claim is APPROVED or DENIED. */
    private void publishLifecycleEvents(Claim saved) {
        events.claimSubmitted(saved);
        if (saved.status() == ClaimStatus.APPROVED || saved.status() == ClaimStatus.DENIED) {
            events.claimAdjudicated(saved);
        }
    }

    @Override
    public Claim getByReference(String claimReference) {
        ClaimReference reference = ClaimReference.of(claimReference);
        return repository.findByClaimReference(reference)
                .orElseThrow(() -> new ClaimNotFoundException(reference.value()));
    }

    private ClaimReference nextFreeReference() {
        for (int attempt = 0; attempt < MAX_REFERENCE_ATTEMPTS; attempt++) {
            ClaimReference candidate = referenceGenerator.next();
            if (!repository.existsByClaimReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique claim reference in " + MAX_REFERENCE_ATTEMPTS + " attempts");
    }

    private static void requireFormat(String value, Pattern pattern, String field, String prefix) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new InvalidClaimException(
                    field + " must start with " + prefix + " followed by at least 5 letters or digits");
        }
    }
}
