package io.datadynamics.atlas.client.typedef.plan;

import java.util.List;

/**
 * Names of the types touched by {@link TypeDefManager#apply(TypePlan)}.
 */
public record TypeApplyResult(List<String> created, List<String> updated, List<String> deleted,
                              List<String> unchanged) {

    public TypeApplyResult {
        created = List.copyOf(created);
        updated = List.copyOf(updated);
        deleted = List.copyOf(deleted);
        unchanged = List.copyOf(unchanged);
    }

    public boolean hasChanges() {
        return !created.isEmpty() || !updated.isEmpty() || !deleted.isEmpty();
    }

    @Override
    public String toString() {
        return "created=" + created + ", updated=" + updated + ", deleted=" + deleted + ", unchanged=" + unchanged.size();
    }
}
