package io.datadynamics.atlas.client.http;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 지수 백오프(exponential backoff)와 지터(jitter)를 쓰는 재시도 정책입니다.
 * <p>
 * 서버에 닿지도 못한 요청(연결 거부, 연결 타임아웃)과 HTTP 503(Atlas가 PASSIVE이거나 시작 중)은 언제
 * 재시도해도 안전합니다. 그 밖의 실패(읽기 타임아웃, 502, 504)는 멱등(idempotent) 메서드일 때만
 * 재시도합니다. {@code POST}는 서버에 이미 적용되었을 수도 있기 때문입니다.
 * {@link Builder#retryNonIdempotent(boolean)}를 켜면 멱등이 아닌 메서드도 재시도합니다.
 * <p>
 * 만든 뒤에는 바뀌지 않는(immutable) 객체이므로 여러 스레드에서 함께 써도 됩니다.
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

    /**
     * 기본 정책을 돌려줍니다. 최대 3번 시도하고, 첫 대기는 500ms에서 시작해 두 배씩 늘려 최대 10초까지 기다리며,
     * 502/503/504를 재시도합니다. 멱등이 아닌 메서드는 위 설명의 안전한 경우에만 재시도합니다.
     */
    public static RetryPolicy defaults() {
        return DEFAULTS;
    }

    /** 재시도하지 않는 정책(최대 시도 1번)을 돌려줍니다. */
    public static RetryPolicy none() {
        return NONE;
    }

    /** 기본값으로 채워진 새 빌더를 돌려줍니다. 필요한 값만 바꿔서 {@link Builder#build()}를 호출합니다. */
    public static Builder builder() {
        return new Builder();
    }

    /** 첫 시도를 포함한 최대 시도 횟수를 돌려줍니다. */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * 이 HTTP 상태 코드가 재시도 대상 목록에 있는지 확인합니다. 실제로 재시도할지는 메서드의 멱등성도 함께
     * 따져 {@link AtlasTransport}가 정합니다.
     *
     * @param statusCode HTTP 상태 코드
     * @return 재시도 대상이면 {@code true}
     */
    public boolean isRetryableStatus(int statusCode) {
        return retryableStatusCodes.contains(statusCode);
    }

    /** 멱등이 아닌 메서드({@code POST} 등)도 재시도할지 돌려줍니다. */
    public boolean isRetryNonIdempotent() {
        return retryNonIdempotent;
    }

    /**
     * 다음 시도 전에 기다릴 시간을 계산합니다.
     * <p>
     * {@code 초기 대기 × 배수^(attempt - 1)}을 최대 대기 시간으로 자른 뒤, 그 값의 50%~100% 사이에서 무작위로
     * 고릅니다. 여러 클라이언트가 동시에 재시도해 서버에 몰리는 것을 막기 위해서입니다.
     *
     * @param attempt 방금 실패한 시도 번호(1부터 시작)
     * @return 기다릴 시간, 계산 결과가 0 이하이면 {@link Duration#ZERO}
     */
    public Duration backoff(int attempt) {
        double millis = initialBackoff.toMillis() * Math.pow(multiplier, Math.max(0, attempt - 1));
        long capped = (long) Math.min(millis, maxBackoff.toMillis());
        if (capped <= 0) {
            return Duration.ZERO;
        }
        // 계산한 대기 시간의 50%~100% 사이에서 무작위로 고르는 지터입니다.
        long jittered = capped / 2 + ThreadLocalRandom.current().nextLong(capped / 2 + 1);
        return Duration.ofMillis(jittered);
    }

    /**
     * {@link RetryPolicy}를 만드는 빌더입니다. 기본값은 {@link RetryPolicy#defaults()}와 같습니다.
     * 스레드 안전하지 않으므로 한 스레드에서만 씁니다.
     */
    public static final class Builder {
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(500);
        private Duration maxBackoff = Duration.ofSeconds(10);
        private double multiplier = 2.0;
        private Set<Integer> retryableStatusCodes = Set.of(502, 503, 504);
        private boolean retryNonIdempotent;

        private Builder() {
        }

        /**
         * 첫 시도를 포함한 전체 시도 횟수를 정합니다. {@code 1}이면 재시도하지 않습니다.
         *
         * @param maxAttempts 전체 시도 횟수(1 이상)
         * @return 이 빌더
         * @throws IllegalArgumentException 1보다 작을 때
         */
        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * 첫 재시도 전에 기다릴 시간을 정합니다(지터 적용 전 값).
         *
         * @param initialBackoff 첫 대기 시간
         * @return 이 빌더
         * @throws NullPointerException {@code null}일 때
         */
        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = Objects.requireNonNull(initialBackoff);
            return this;
        }

        /**
         * 대기 시간의 상한을 정합니다(지터 적용 전 값).
         *
         * @param maxBackoff 최대 대기 시간
         * @return 이 빌더
         * @throws NullPointerException {@code null}일 때
         */
        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = Objects.requireNonNull(maxBackoff);
            return this;
        }

        /**
         * 재시도할 때마다 대기 시간에 곱할 배수를 정합니다. {@code 1.0}이면 매번 같은 시간을 기다립니다.
         *
         * @param multiplier 배수(1.0 이상)
         * @return 이 빌더
         * @throws IllegalArgumentException 1.0보다 작을 때
         */
        public Builder multiplier(double multiplier) {
            if (multiplier < 1.0) {
                throw new IllegalArgumentException("multiplier must be >= 1.0");
            }
            this.multiplier = multiplier;
            return this;
        }

        /**
         * 재시도할 HTTP 상태 코드 집합을 정합니다. 기본값은 502, 503, 504입니다. 집합은 {@link #build()} 때 복사됩니다.
         *
         * @param retryableStatusCodes 재시도할 상태 코드들
         * @return 이 빌더
         * @throws NullPointerException {@code null}일 때
         */
        public Builder retryableStatusCodes(Set<Integer> retryableStatusCodes) {
            this.retryableStatusCodes = Objects.requireNonNull(retryableStatusCodes);
            return this;
        }

        /**
         * 멱등이 아닌 메서드({@code POST} 등)도 재시도할지 정합니다. 기본값은 {@code false}입니다.
         * 켜면 서버에 이미 적용된 요청이 한 번 더 적용될 수 있으므로, 요청이 중복되어도 괜찮을 때만 켭니다.
         *
         * @param retryNonIdempotent 재시도하려면 {@code true}
         * @return 이 빌더
         */
        public Builder retryNonIdempotent(boolean retryNonIdempotent) {
            this.retryNonIdempotent = retryNonIdempotent;
            return this;
        }

        /** 지금까지 설정한 값으로 정책을 만듭니다. */
        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
