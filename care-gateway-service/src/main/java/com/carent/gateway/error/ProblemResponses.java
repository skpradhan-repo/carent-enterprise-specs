package com.carent.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Writes RFC 7807 bodies (specs/07) for responses the gateway itself produces. */
public final class ProblemResponses {

    private ProblemResponses() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, ObjectMapper objectMapper, HttpStatusCode status,
                                   String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(instance(exchange));

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(problem);
        } catch (JsonProcessingException e) {
            bytes = ("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status.value() + "}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private static URI instance(ServerWebExchange exchange) {
        try {
            return URI.create(exchange.getRequest().getPath().value());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
