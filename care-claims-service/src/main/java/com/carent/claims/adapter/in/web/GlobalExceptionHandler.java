package com.carent.claims.adapter.in.web;

import com.carent.claims.domain.exception.ClaimNotFoundException;
import com.carent.claims.domain.exception.IllegalClaimStateException;
import com.carent.claims.domain.exception.InvalidClaimException;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The only place domain exceptions become HTTP (specs/07). Every 4xx/5xx is an RFC 7807 ProblemDetail.
 * Framework errors (405, 415, ...) come from {@link ResponseEntityExceptionHandler}, already ProblemDetail.
 * Responses never echo rejected input values or internal exception text.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String VALIDATION_FAILED = "Validation Failed";

    // ---- bean validation and unreadable bodies ---------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(error -> error.getField()))
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return handleExceptionInternal(ex, problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, detail, request),
                headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return handleExceptionInternal(ex,
                problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, unreadableBodyDetail(ex), request),
                headers, HttpStatus.BAD_REQUEST, request);
    }

    private static String unreadableBodyDetail(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof JsonMappingException mapping) {
            String field = mapping.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(name -> name != null)
                    .collect(Collectors.joining("."));
            if (!field.isEmpty()) {
                return field + " is invalid or has an unsupported value";
            }
        }
        return "Request body is missing or malformed";
    }

    // ---- domain exceptions -----------------------------------------------------------------

    @ExceptionHandler(InvalidClaimException.class)
    ResponseEntity<ProblemDetail> handleInvalidClaim(InvalidClaimException ex, HttpServletRequest request) {
        return respond(problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, ex.getMessage(), request));
    }

    @ExceptionHandler(ClaimNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ClaimNotFoundException ex, HttpServletRequest request) {
        return respond(problem(HttpStatus.NOT_FOUND, "Claim Not Found", ex.getMessage(), request));
    }

    @ExceptionHandler(IllegalClaimStateException.class)
    ResponseEntity<ProblemDetail> handleIllegalState(IllegalClaimStateException ex, HttpServletRequest request) {
        return respond(problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request));
    }

    // ---- everything else -------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        LOG.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred", request));
    }

    // ---- helpers ---------------------------------------------------------------------------

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail,
                                         HttpServletRequest request) {
        return problem(status, title, detail, request.getRequestURI());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, WebRequest request) {
        return problem(status, title, detail, ((ServletWebRequest) request).getRequest().getRequestURI());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(path));
        return problem;
    }
}
