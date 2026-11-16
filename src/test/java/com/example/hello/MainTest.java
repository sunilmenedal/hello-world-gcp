package com.example.hello;

import org.junit.jupiter.api.*;
import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    private HttpServer server;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach void start() throws Exception {
        server = Main.createServer(0);
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.getAddress().getPort() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void greeting() throws Exception {
        var response = get("/");
        assertEquals(200, response.statusCode());
        assertEquals("Hi World!\n", response.body());
    }
    @Test void health() throws Exception {
        assertEquals("OK\n", get("/health").body());
    }
    @Test void unknownPath() throws Exception {
        assertEquals(404, get("/missing").statusCode());
    }
}
