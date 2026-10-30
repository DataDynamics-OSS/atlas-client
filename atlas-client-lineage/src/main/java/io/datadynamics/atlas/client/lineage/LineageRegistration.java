package io.datadynamics.atlas.client.lineage;

import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Result of registering lineage.
 *
 * @param guids           guids of the process, column lineage and created entities, keyed by {@link EntityRef#key()}
 * @param createdEntities entities created because they were missing ({@link MissingEntityPolicy#CREATE})
 * @param response        the raw Atlas response
 */
public record LineageRegistration(Map<String, String> guids, List<EntityRef> createdEntities,
                                  EntityMutationResponse response) {

    public LineageRegistration {
        guids = Map.copyOf(guids);
        createdEntities = List.copyOf(createdEntities);
    }

    public Optional<String> guidOf(EntityRef ref) {
        return Optional.ofNullable(guids.get(ref.key()));
    }

    public Optional<String> guidOf(String typeName, String qualifiedName) {
        return guidOf(EntityRef.of(typeName, qualifiedName));
    }
}
