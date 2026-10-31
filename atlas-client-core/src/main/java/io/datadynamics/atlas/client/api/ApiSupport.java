package io.datadynamics.atlas.client.api;

import io.datadynamics.atlas.client.exception.AtlasNotFoundException;

import java.util.Optional;
import java.util.function.Supplier;

/** API 클래스들이 함께 쓰는 보조 메서드를 모아 둔 유틸리티 클래스입니다. */
final class ApiSupport {

    // 정적 메서드만 있으므로 인스턴스를 만들지 못하게 막습니다.
    private ApiSupport() {
    }

    /**
     * 조회를 실행하고, "찾을 수 없음"(HTTP 404, {@link AtlasNotFoundException})을 {@link Optional#empty()}로 바꿉니다.
     * 조회 결과가 {@code null}인 경우에도 빈 {@link Optional}을 돌려줍니다. 그 밖의 예외는 그대로 전달됩니다.
     */
    static <T> Optional<T> findOptional(Supplier<T> call) {
        try {
            return Optional.ofNullable(call.get());
        } catch (AtlasNotFoundException e) {
            return Optional.empty();
        }
    }
}
