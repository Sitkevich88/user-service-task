package de.tonline.usersservicetask.client;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Logs every outbound call (one line per attempt, so retries are visible): method, URI, status, duration. */
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger("upstream.calls");

    private final String upstream;

    public LoggingInterceptor(String upstream) {
        this.upstream = upstream;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long start = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            int status = response.getStatusCode().value();
            if (status >= 500) {
                log.warn("[{}] {} {} -> {} in {} ms", upstream, request.getMethod(), request.getURI(), status, ms(start));
            } else {
                log.info("[{}] {} {} -> {} in {} ms", upstream, request.getMethod(), request.getURI(), status, ms(start));
            }
            return response;
        } catch (IOException | RuntimeException e) {
            log.warn("[{}] {} {} failed after {} ms: {}", upstream, request.getMethod(), request.getURI(),
                    ms(start), e.toString());
            throw e;
        }
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
