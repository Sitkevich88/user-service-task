package de.tonline.usersservicetask.client;

/** An upstream call failed. {@code timeout} distinguishes "too slow" (504) from "broken" (502). */
public class UpstreamException extends RuntimeException {

    private final boolean timeout;
    private final boolean retryable;

    public UpstreamException(String message, Throwable cause, boolean timeout, boolean retryable) {
        super(message, cause);
        this.timeout = timeout;
        this.retryable = retryable;
    }

    public boolean isTimeout() {
        return timeout;
    }

    /** True for 5xx and IO problems, false for 4xx (retrying would not help). */
    public boolean isRetryable() {
        return retryable;
    }
}
