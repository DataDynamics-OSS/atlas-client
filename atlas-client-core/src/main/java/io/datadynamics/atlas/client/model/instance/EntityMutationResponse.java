package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Result of an entity create/update/delete call.
 */
public class EntityMutationResponse extends AtlasModel {

    public enum EntityOperation {
        CREATE,
        UPDATE,
        PARTIAL_UPDATE,
        DELETE,
        PURGE
    }

    private Map<EntityOperation, List<AtlasEntityHeader>> mutatedEntities;
    private Map<String, String> guidAssignments;

    public Map<EntityOperation, List<AtlasEntityHeader>> getMutatedEntities() {
        return mutatedEntities;
    }

    public void setMutatedEntities(Map<EntityOperation, List<AtlasEntityHeader>> mutatedEntities) {
        this.mutatedEntities = mutatedEntities;
    }

    /** Placeholder (negative) guid to the guid assigned by Atlas. */
    public Map<String, String> getGuidAssignments() {
        return guidAssignments;
    }

    public void setGuidAssignments(Map<String, String> guidAssignments) {
        this.guidAssignments = guidAssignments;
    }

    public List<AtlasEntityHeader> get(EntityOperation operation) {
        if (mutatedEntities == null) {
            return Collections.emptyList();
        }
        return mutatedEntities.getOrDefault(operation, Collections.emptyList());
    }

    public List<AtlasEntityHeader> created() {
        return get(EntityOperation.CREATE);
    }

    /** Fully and partially updated entities. */
    public List<AtlasEntityHeader> updated() {
        List<AtlasEntityHeader> partial = get(EntityOperation.PARTIAL_UPDATE);
        if (partial.isEmpty()) {
            return get(EntityOperation.UPDATE);
        }
        return Stream.concat(get(EntityOperation.UPDATE).stream(), partial.stream())
                .collect(Collectors.toList());
    }

    public List<AtlasEntityHeader> deleted() {
        return get(EntityOperation.DELETE);
    }

    /** Number of entities affected per operation; handy for logging. */
    public Map<EntityOperation, Integer> summary() {
        Map<EntityOperation, Integer> summary = new EnumMap<>(EntityOperation.class);
        if (mutatedEntities != null) {
            mutatedEntities.forEach((op, headers) -> summary.put(op, headers == null ? 0 : headers.size()));
        }
        return summary;
    }

    /**
     * The guid of the first created or updated entity of the given type with the given
     * {@code qualifiedName}. Useful right after an upsert of a single entity.
     */
    public Optional<String> findGuid(String typeName, String qualifiedName) {
        if (mutatedEntities == null) {
            return Optional.empty();
        }
        return mutatedEntities.values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(h -> typeName.equals(h.getTypeName()) && qualifiedName.equals(h.qualifiedName()))
                .map(AtlasEntityHeader::getGuid)
                .findFirst();
    }

    /** Guid of the first mutated entity, regardless of operation. */
    public Optional<String> firstGuid() {
        if (mutatedEntities == null) {
            return Optional.empty();
        }
        return mutatedEntities.values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(AtlasEntityHeader::getGuid)
                .findFirst();
    }
}
