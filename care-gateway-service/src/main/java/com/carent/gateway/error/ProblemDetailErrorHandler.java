package com.carent.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

/**
 * Turns every gateway-level failure into an RFC 7807 body. Responses carry a fixed, generic detail: never a
 * host, port, exception text or stack trace. Replaces Boot's default error handler (order -1).
 */
@Component
@Order(-2)
class ProblemDetailErrorHandler implements WebExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailErrorHandler.class);

    private final ObjectMapper objectMapper;

    ProblemDetailErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        String path = exchange.getRequest().getPath().value();

        if (ex instanceof ResponseStatusException statusException) {
            HttpStatusCode status = statusException.getStatusCode();
            LOG.warn("Gateway answered {} for {} {}", status.value(), exchange.getRequest().getMethod(), path);
            return respond(exchange, status, titleOf(status), detailOf(status));
        }
        if (isTimeout(ex)) {
            LOG.warn("Upstream timed out for {} {}", exchange.getRequest().getMethod(), path);
            return respond(exchange, HttpStatus.GATEWAY_TIMEOUT, "Gateway Timeout",
                    "The upstream service did not respond in time");
        }
        if (isConnectionFailure(ex)) {
            LOG.warn("Upstream unreachable for {} {}: {}", exchange.getRequest().getMethod(), path,
                    rootOf(ex).getClass().getSimpleName());
            return respond(exchange, HttpStatus.BAD_GATEWAY, "Bad Gateway", "The upstream service is unavailable");
        }
        LOG.error("Unhandled gateway error for {} {}", exchange.getRequest().getMethod(), path, ex);
        return respond(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred");
    }

    private Mono<Void> respond(ServerWebExchange exchange, HttpStatusCode status, String title, String detail) {
        return ProblemResponses.write(exchange, objectMapper, status, title, detail);
    }

    private static String titleOf(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.getReasonPhrase() : "Error";
    }

    private static String detailOf(HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return "No route matches the request path";
        }
        if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            return "The upstream service is unavailable";
        }
        if (status.value() == HttpStatus.GATEWAY_TIMEOUT.value()) {
            return "The upstream service did not respond in time";
        }
        return status.is5xxServerError() ? "The request could not be processed"
                : "The request was rejected";
    }

    private static boolean isTimeout(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof TimeoutException || t instanceof ReadTimeoutException
                    || t instanceof ConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConnectionFailure(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ConnectException || t instanceof UnknownHostException
                    || t instanceof PrematureCloseException || t instanceof ClosedChannelException) {
                return true;
            }
        }
        return false;
    }

    private static Throwable rootOf(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}
