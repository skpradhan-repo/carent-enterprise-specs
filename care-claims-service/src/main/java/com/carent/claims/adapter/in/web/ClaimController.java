package com.carent.claims.adapter.in.web;

import com.carent.claims.adapter.in.web.dto.ClaimResponse;
import com.carent.claims.adapter.in.web.dto.ClaimSubmissionRequest;
import com.carent.claims.application.port.in.GetClaimUseCase;
import com.carent.claims.application.port.in.SubmitClaimUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST adapter for specs/openapi.yaml. Talks to the application only through inbound ports. */
@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {

    private final SubmitClaimUseCase submitClaim;
    private final GetClaimUseCase getClaim;

    public ClaimController(SubmitClaimUseCase submitClaim, GetClaimUseCase getClaim) {
        this.submitClaim = submitClaim;
        this.getClaim = getClaim;
    }

    @PostMapping
    public ResponseEntity<ClaimResponse> submitClaim(@Valid @RequestBody ClaimSubmissionRequest request) {
        ClaimResponse body = ClaimWebMapper.toResponse(submitClaim.submit(ClaimWebMapper.toCommand(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{claimReference}")
    public ClaimResponse getClaimByReference(@PathVariable String claimReference) {
        return ClaimWebMapper.toResponse(getClaim.getByReference(claimReference));
    }
}
