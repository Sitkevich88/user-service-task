package de.tonline.usersservicetask.client;

import java.util.function.Predicate;

import de.tonline.usersservicetask.client.common.UpstreamException;

/**
 * Which failures count for the circuit breaker: those that say something about upstream health,
 * i.e. 5xx, IO errors and timeouts. Not 4xx. Referenced from application.yaml.
 */
public class CircuitBreakerFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable t) {
        return t instanceof UpstreamException e && (e.isTimeout() || e.isRetryable());
    }
}
