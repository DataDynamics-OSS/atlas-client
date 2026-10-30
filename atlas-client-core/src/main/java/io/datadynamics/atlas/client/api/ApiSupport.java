package io.datadynamics.atlas.client.api;

import io.datadynamics.atlas.client.exception.AtlasNotFoundException;

import java.util.Optional;
import java.util.function.Supplier;

final class ApiSupport {

    private ApiSupport() {
    }

    /** Runs a lookup, mapping "not found" to {@link Optional#empty()}. */
    static <T> Optional<T> findOptional(Supplier<T> call) {
        try {
            return Optional.ofNullable(call.get());
        } catch (AtlasNotFoundException e) {
            return Optional.empty();
        }
    }
}
