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
 * 통합 테스트의 공통 부모 클래스입니다.
 * <p>
 * 다음 세 가지를 제공합니다.
 * <ul>
 *   <li>공유 Atlas 서버({@link AtlasTestServer#shared()})에 접속하는 클라이언트</li>
 *   <li>여러 테스트가 같은 서버를 써도 이름이 겹치지 않도록 하는 고유 접두어</li>
 *   <li>각 테스트 클래스가 만든 엔티티와 타입을 가능한 범위에서 정리하는 기능(실패해도 테스트는 실패시키지 않음)</li>
 * </ul>
 */
abstract class AtlasIT {

    private static final Logger LOG = LoggerFactory.getLogger(AtlasIT.class);

    /** 테스트 클래스마다 새로 만드는 Atlas 클라이언트입니다. */
    static AtlasClient atlas;

    /** 테스트 클래스 실행마다 고유한 값입니다. 테스트가 만드는 이름은 모두 이 값으로 시작합니다. */
    static String prefix;

    /** {@link #cleanup()}에서 삭제할 타입 이름입니다. */
    private static final List<String> createdTypes = new ArrayList<>();
    /** {@link #cleanup()}에서 삭제하고 purge할 엔티티 guid입니다. */
    private static final List<String> createdGuids = new ArrayList<>();

    /**
     * 공유 Atlas 서버에 접속하는 클라이언트를 만들고 고유 접두어를 정합니다.
     * <p>
     * 테스트 서버는 느릴 수 있으므로 요청 타임아웃을 2분으로 넉넉히 두고, 일시적인 실패에 대비해 2초 간격부터
     * 최대 3번까지 시도합니다. 접두어는 {@code it} + UUID 앞 8자리 + {@code _}입니다.
     */
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

    /**
     * 이번 실행에서만 쓰는 고유한 이름을 만듭니다. 예: {@code it1a2b3c4d_table}
     *
     * @param suffix 접두어 뒤에 붙일 이름
     * @return 접두어가 붙은 이름
     */
    static String name(String suffix) {
        return prefix + suffix;
    }

    /** 테스트 클래스가 끝난 뒤 삭제할 타입으로 등록합니다. 삭제는 등록한 순서와 상관없이 한 번의 요청으로 합니다. */
    static void cleanupTypes(String... typeNames) {
        createdTypes.addAll(List.of(typeNames));
    }

    /** 테스트 클래스가 끝난 뒤 삭제하고 purge할 엔티티로 등록합니다. */
    static void cleanupEntity(String guid) {
        createdGuids.add(guid);
    }

    /**
     * 등록된 엔티티와 타입을 정리합니다. 실패하면 경고 로그만 남기고 넘어갑니다.
     * <p>
     * 엔티티는 먼저 soft delete한 다음 purge로 완전히 지웁니다.
     */
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
        // Atlas는 인스턴스가 남아 있는(soft delete된 것 포함) 타입의 삭제를 거부합니다. 그래서 위에서 우리 타입의
        // 엔티티를 먼저 모두 purge합니다.
        // 등록은 했지만 서버에 없는 타입(예: 만들기 전에 테스트가 실패한 경우)은 건너뛰고, 있는 타입만 삭제합니다.
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

    /**
     * {@code done}이 값을 받아들이거나 제한 시간이 지날 때까지 1초 간격으로 값을 다시 가져옵니다.
     * Atlas가 비동기로 처리하는 결과(예: Kafka 알림, 인덱스 반영)를 기다릴 때 씁니다.
     * 인터럽트되면 인터럽트 상태를 복원하고 바로 멈춥니다.
     *
     * @param supplier 값을 가져오는 함수
     * @param done     기다림을 끝낼 조건
     * @param timeout  최대 대기 시간
     * @return 마지막으로 가져온 값. 시간이 지나 끝난 경우 조건을 만족하지 않을 수 있으므로 호출한 쪽에서 검증합니다
     */
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

    /** 타입 정의 묶음에 들어 있는 모든 타입의 이름을 돌려줍니다. */
    static List<String> names(AtlasTypesDef typesDef) {
        return typesDef.stream().map(AtlasBaseTypeDef::getName).collect(Collectors.toList());
    }
}
