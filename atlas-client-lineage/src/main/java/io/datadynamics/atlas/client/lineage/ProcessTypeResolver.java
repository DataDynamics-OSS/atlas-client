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
 * Tells whether an entity type is a process (has {@code Process} among its super types),
 * fetching type definitions on demand and caching the answer.
 */
public final class ProcessTypeResolver implements Predicate<String> {

    public static final String PROCESS = "Process";

    private final AtlasClient client;
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

    public ProcessTypeResolver(AtlasClient client) {
        this.client = client;
    }

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
        cache.put(typeName, result);
        return result;
    }

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
