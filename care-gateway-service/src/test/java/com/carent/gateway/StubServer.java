package com.carent.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** A tiny loopback HTTP server standing in for a downstream service; records every request it receives. */
final class StubServer implements AutoCloseable {

    record Received(String method, String path, String authorization, String body) {
    }

    private final String name;
    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();

    private StubServer(String name, HttpServer server) {
        this.name = name;
        this.server = server;
    }

    static StubServer start(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            StubServer stub = new StubServer(name, server);
            server.createContext("/", stub::handle);
            server.start();
            return stub;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String uri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<Received> received() {
        return received;
    }

    /** The next {@code count} requests are answered with 503 before the stub behaves normally again. */
    void failNext(int count) {
        failuresLeft.set(count);
    }

    void reset() {
        received.clear();
        failuresLeft.set(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                exchange.getRequestHeaders().getFirst("Authorization"), body));

        int status;
        String json;
        if (failuresLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            status = 503;
            json = "{\"stub\":\"" + name + "\",\"failing\":true}";
        } else {
            status = "POST".equals(exchange.getRequestMethod()) ? 201 : 200;
            json = "{\"stub\":\"" + name + "\",\"path\":\"" + exchange.getRequestURI().getRawPath() + "\"}";
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
