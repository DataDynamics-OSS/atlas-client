package io.datadynamics.atlas.client.lineage;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 엔티티 타입이 프로세스인지(상위 타입 중에 {@code Process}가 있는지) 판별합니다.
 * 타입 정의는 필요할 때 Atlas에서 조회하고, 답은 캐시합니다.
 * <p>
 * 캐시는 {@link ConcurrentHashMap}이므로 여러 스레드에서 함께 써도 됩니다. 다만 동시에 같은 타입을 처음
 * 물으면 타입 정의를 중복으로 조회할 수 있습니다. 캐시는 비우지 않으므로, 실행 중에 타입 정의가 바뀌면
 * 새 인스턴스를 만들어야 반영됩니다.
 */
public final class ProcessTypeResolver implements Predicate<String> {

    /** Atlas의 모든 프로세스 타입이 상속하는 기본 타입 이름입니다. */
    public static final String PROCESS = "Process";

    private final AtlasClient client;
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

    /**
     * 판별기를 만듭니다.
     *
     * @param client 타입 정의를 조회할 Atlas 클라이언트
     */
    public ProcessTypeResolver(AtlasClient client) {
        this.client = client;
    }

    /**
     * 타입이 프로세스인지 판별합니다. 처음 묻는 타입이면 타입 정의를 조회하며 상위 타입을 재귀적으로 따라갑니다.
     *
     * @param typeName 엔티티 타입 이름
     * @return {@code Process}이거나 그것을 상속하면 {@code true}. {@code null}이거나 알 수 없는 타입이면 {@code false}
     */
    @Override
    public boolean test(String typeName) {
        if (typeName == null) {
            return false;
        }
        Boolean cached = cache.get(typeName);
        if (cached != null) {
            return cached;
        }
        boolean result = isProcess(typeName, new HashSet<>());
        // 이 질의의 결과만 캐시합니다. 재귀 중에 거친 상위 타입의 결과는 캐시하지 않습니다.
        cache.put(typeName, result);
        return result;
    }

    /**
     * 상위 타입을 깊이 우선으로 따라가며 {@code Process}에 닿는지 확인합니다.
     * {@code visited}는 같은 타입을 두 번 조사하지 않게 하고, 상속 관계에 순환이 있어도 무한 재귀에 빠지지
     * 않게 합니다.
     */
    private boolean isProcess(String typeName, Set<String> visited) {
        if (PROCESS.equals(typeName)) {
            return true;
        }
        Boolean cached = cache.get(typeName);
        if (cached != null) {
            return cached;
        }
        if (!visited.add(typeName)) {
            return false;
        }
        Optional<AtlasEntityDef> def = client.types().findEntityDef(typeName);
        if (def.isEmpty() || def.get().getSuperTypes() == null) {
            return false;
        }
        for (String superType : def.get().getSuperTypes()) {
            if (isProcess(superType, visited)) {
                return true;
            }
        }
        return false;
    }
}
