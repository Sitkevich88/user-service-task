package de.tonline.usersservicetask.client.common;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.function.Supplier;

/** Translates Spring's RestClient exceptions into our own small exception model. */
public final class UpstreamCalls {

    private UpstreamCalls() {
    }

    public static <T> T execute(String what, Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpServerErrorException e) {
            throw new UpstreamException(what + " returned " + e.getStatusCode(), e, false, true);
        } catch (HttpClientErrorException e) {
            throw new UpstreamException(what + " returned " + e.getStatusCode(), e, false, false);
        } catch (ResourceAccessException e) {
            boolean timeout = hasCause(e, HttpTimeoutException.class);
            throw new UpstreamException(what + (timeout ? " timed out" : " unreachable"), e, timeout, true);
        } catch (RestClientException e) {
            // e.g. undecodable body: upstream is misbehaving, retrying is pointless
            throw new UpstreamException(what + " returned an invalid response", e, false, false);
        }
    }

    private static boolean hasCause(Throwable t, Class<? extends IOException> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }
}
