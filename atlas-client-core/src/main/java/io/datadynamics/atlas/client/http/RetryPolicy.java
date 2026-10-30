package io.datadynamics.atlas.client.http;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Retry policy with exponential backoff and jitter.
 * <p>
 * Requests that never reached the server (connection refused, connect timeout) and HTTP 503
 * (Atlas passive/starting up) are always safe to retry. Other failures (read timeouts, 502, 504)
 * are retried only for idempotent methods unless {@link Builder#retryNonIdempotent(boolean)}
 * is enabled, because a {@code POST} may already have been applied.
 */
public final class RetryPolicy {

    private static final RetryPolicy NONE = builder().maxAttempts(1).build();
    private static final RetryPolicy DEFAULTS = builder().build();

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final double multiplier;
    private final Set<Integer> retryableStatusCodes;
    private final boolean retryNonIdempotent;

    private RetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.initialBackoff = builder.initialBackoff;
        this.maxBackoff = builder.maxBackoff;
        this.multiplier = builder.multiplier;
        this.retryableStatusCodes = Set.copyOf(builder.retryableStatusCodes);
        this.retryNonIdempotent = builder.retryNonIdempotent;
    }

    /** 3 attempts, 500ms initial backoff doubling up to 10s, retrying 502/503/504. */
    public static RetryPolicy defaults() {
        return DEFAULTS;
    }

    public static RetryPolicy none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public boolean isRetryableStatus(int statusCode) {
        return retryableStatusCodes.contains(statusCode);
    }

    public boolean isRetryNonIdempotent() {
        return retryNonIdempotent;
    }

    /**
     * @param attempt the attempt that just failed, starting at 1
     */
    public Duration backoff(int attempt) {
        double millis = initialBackoff.toMillis() * Math.pow(multiplier, Math.max(0, attempt - 1));
        long capped = (long) Math.min(millis, maxBackoff.toMillis());
        if (capped <= 0) {
            return Duration.ZERO;
        }
        // Full jitter between 50% and 100% of the computed delay.
        long jittered = capped / 2 + ThreadLocalRandom.current().nextLong(capped / 2 + 1);
        return Duration.ofMillis(jittered);
    }

    public static final class Builder {
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(500);
        private Duration maxBackoff = Duration.ofSeconds(10);
        private double multiplier = 2.0;
        private Set<Integer> retryableStatusCodes = Set.of(502, 503, 504);
        private boolean retryNonIdempotent;

        private Builder() {
        }

        /** Total number of attempts including the first one; {@code 1} disables retries. */
        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = Objects.requireNonNull(initialBackoff);
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = Objects.requireNonNull(maxBackoff);
            return this;
        }

        public Builder multiplier(double multiplier) {
            if (multiplier < 1.0) {
                throw new IllegalArgumentException("multiplier must be >= 1.0");
            }
            this.multiplier = multiplier;
            return this;
        }

        public Builder retryableStatusCodes(Set<Integer> retryableStatusCodes) {
            this.retryableStatusCodes = Objects.requireNonNull(retryableStatusCodes);
            return this;
        }

        public Builder retryNonIdempotent(boolean retryNonIdempotent) {
            this.retryNonIdempotent = retryNonIdempotent;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
