package de.tonline.usersservicetask.client;

import java.lang.reflect.Method;

import org.springframework.resilience.retry.MethodRetryPredicate;

import de.tonline.usersservicetask.client.common.UpstreamException;

/** Retry only failures that may succeed on a second try: 5xx and IO errors, but not 4xx or timeouts. */
public class TransientFailurePredicate implements MethodRetryPredicate {

    @Override
    public boolean shouldRetry(Method method, Throwable throwable) {
        return throwable instanceof UpstreamException e && e.isRetryable() && !e.isTimeout();
    }
}
