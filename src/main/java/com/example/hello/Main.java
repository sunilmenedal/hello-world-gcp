package com.example.hello;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = createServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        server.start();
        System.out.println("Hi World server listening on port " + server.getAddress().getPort());
    }

    public static HttpServer createServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int status;
            String body;
            if (!exchange.getRequestMethod().equals("GET")) {
                status = 405;
                body = "Method not allowed\n";
                exchange.getResponseHeaders().set("Allow", "GET");
            } else if (path.equals("/")) {
                status = 200;
                body = "Hi World!\n";
            } else if (path.equals("/health")) {
                status = 200;
                body = "OK\n";
            } else {
                status = 404;
                body = "Not found\n";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            } finally {
                exchange.close();
            }
        });
        // Virtual threads allow concurrent requests without application dependencies.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        return server;
    }
}
