package de.tonline.usersservicetask;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Tiny controllable stand-in for jsonplaceholder: lets tests script delays and failures. */
final class FakeUpstream implements AutoCloseable {

    /** Scripted reply: status, body and artificial delay. */
    record Reply(int status, String body, long delayMs) {
        static Reply ok(String body) {
            return new Reply(200, body, 0);
        }

        static Reply ok(String body, long delayMs) {
            return new Reply(200, body, delayMs);
        }

        static Reply status(int status) {
            return new Reply(status, "", 0);
        }
    }

    static final String USER_JSON = """
            {"id":1,"name":"Leanne Graham","username":"Bret","email":"Sincere@april.biz",
             "address":{"street":"Kulas Light","suite":"Apt. 556","city":"Gwenborough","zipcode":"92998-3874",
                        "geo":{"lat":"-37.3159","lng":"81.1496"}},
             "phone":"1-770-736-8031 x56442","website":"hildegard.org",
             "company":{"name":"Romaguera-Crona","catchPhrase":"Multi-layered client-server neural-net",
                        "bs":"harness real-time e-markets"},
             "somethingNew":"unknown upstream fields must not break us"}""";
    static final String POSTS_JSON = """
            [{"userId":1,"id":1,"title":"sunt aut facere","body":"quia et suscipit\\nsuscipit recusandae"},
             {"userId":1,"id":2,"title":"qui est esse","body":"est rerum tempore vitae"}]""";

    private final HttpServer server;
    final AtomicInteger userCalls = new AtomicInteger();
    final AtomicInteger postCalls = new AtomicInteger();
    /** Called with the 1-based call number so a test can fail the first call and succeed the next. */
    volatile Function<Integer, Reply> users = n -> Reply.ok(USER_JSON);
    volatile Function<Integer, Reply> posts = n -> Reply.ok(POSTS_JSON);

    FakeUpstream() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/users/", ex -> handle(ex, userCalls, () -> users));
        server.createContext("/posts", ex -> handle(ex, postCalls, () -> posts));
        server.start();
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    void reset() {
        userCalls.set(0);
        postCalls.set(0);
        users = n -> Reply.ok(USER_JSON);
        posts = n -> Reply.ok(POSTS_JSON);
    }

    private static void handle(HttpExchange ex, AtomicInteger counter,
                               java.util.function.Supplier<Function<Integer, Reply>> script) {
        Reply reply = script.get().apply(counter.incrementAndGet());
        try {
            if (reply.delayMs() > 0) {
                Thread.sleep(reply.delayMs());
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                ex.getResponseBody().write(bytes);
            }
        } catch (InterruptedException | IOException ignored) {
            // client gave up (timeout): nothing to do
        } finally {
            ex.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
