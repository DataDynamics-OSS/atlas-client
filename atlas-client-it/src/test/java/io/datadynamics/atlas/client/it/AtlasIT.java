package io.datadynamics.atlas.client.it;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.http.RetryPolicy;
import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.test.AtlasTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Base of the integration tests: a client for the shared Atlas server, a unique prefix so tests
 * do not collide on a shared server, and best-effort cleanup of what each test class created.
 */
abstract class AtlasIT {

    private static final Logger LOG = LoggerFactory.getLogger(AtlasIT.class);

    static AtlasClient atlas;

    /** Unique per test class run: names created by the test start with it. */
    static String prefix;

    private static final List<String> createdTypes = new ArrayList<>();
    private static final List<String> createdGuids = new ArrayList<>();

    @BeforeAll
    static void connect() {
        AtlasTestServer server = AtlasTestServer.shared();
        atlas = AtlasClient.builder()
                .serverUrl(server.url())
                .basicAuth(server.username(), server.password())
                .requestTimeout(Duration.ofMinutes(2))
                .retryPolicy(RetryPolicy.builder().maxAttempts(3).initialBackoff(Duration.ofSeconds(2)).build())
                .build();
        prefix = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT) + "_";
    }

    /** Name unique to this run, e.g. {@code it1a2b3c4d_table}. */
    static String name(String suffix) {
        return prefix + suffix;
    }

    static void cleanupTypes(String... typeNames) {
        createdTypes.addAll(List.of(typeNames));
    }

    static void cleanupEntity(String guid) {
        createdGuids.add(guid);
    }

    @AfterAll
    static void cleanup() {
        if (!createdGuids.isEmpty()) {
            try {
                atlas.entities().delete(createdGuids);
                atlas.admin().purge(createdGuids);
            } catch (RuntimeException e) {
                LOG.warn("Entity cleanup failed: {}", e.getMessage());
            }
            createdGuids.clear();
        }
        // Atlas refuses to delete types that still have (even soft-deleted) instances; purge
        // everything of our types first.
        if (!createdTypes.isEmpty()) {
            try {
                AtlasTypesDef toDelete = new AtlasTypesDef();
                List<String> existing = atlas.types().getTypeNames().stream()
                        .filter(createdTypes::contains).collect(Collectors.toList());
                for (String typeName : existing) {
                    atlas.types().findByName(typeName).ifPresent(toDelete::add);
                }
                if (!toDelete.isEmpty()) {
                    atlas.types().delete(toDelete);
                }
            } catch (RuntimeException e) {
                LOG.warn("Type cleanup failed (left {}): {}", createdTypes, e.getMessage());
            }
            createdTypes.clear();
        }
    }

    /** Polls until {@code done} accepts the value or the timeout expires; returns the last value. */
    static <T> T awaitValue(Supplier<T> supplier, Predicate<T> done, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        T value = supplier.get();
        while (!done.test(value) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            value = supplier.get();
        }
        return value;
    }

    static List<String> names(AtlasTypesDef typesDef) {
        return typesDef.stream().map(AtlasBaseTypeDef::getName).collect(Collectors.toList());
    }
}
