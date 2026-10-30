package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;
import io.datadynamics.atlas.client.model.instance.AtlasRelatedObjectId;
import io.datadynamics.atlas.client.model.instance.EntityStatus;
import io.datadynamics.atlas.client.model.search.SearchParameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Finds metadata problems Atlas does not prevent: processes without lineage edges, lineage
 * pointing at deleted datasets, and several active entities sharing one qualifiedName.
 */
public final class ConsistencyChecker {

    public enum Kind {
        /** A process with neither inputs nor outputs: it contributes nothing to lineage. */
        ORPHAN_PROCESS,
        /** A process with inputs but no outputs, or the other way around. */
        INCOMPLETE_PROCESS,
        /** A process whose current lineage edge points at a deleted entity. */
        DELETED_REFERENCE,
        /** Several active entities of one type with the same qualifiedName. */
        DUPLICATE
    }

    public record Finding(Kind kind, String typeName, String qualifiedName, String guid, String message) {

        @Override
        public String toString() {
            return String.format("%-18s %s:%s (%s) %s", kind, typeName, qualifiedName, guid, message);
        }
    }

    static final int BULK_FETCH_SIZE = 100;

    private final AtlasClient client;

    public ConsistencyChecker(AtlasClient client) {
        this.client = client;
    }

    /** Checks every active entity of {@code processType} and its sub-types. */
    public List<Finding> checkProcesses(String processType) {
        List<AtlasEntityHeader> processes = activeEntities(processType, true);
        List<Finding> findings = new ArrayList<>(duplicates(processes));
        List<String> guids = processes.stream().map(AtlasEntityHeader::getGuid).collect(Collectors.toList());
        for (int i = 0; i < guids.size(); i += BULK_FETCH_SIZE) {
            AtlasEntitiesWithExtInfo bulk = client.entities()
                    .getBulk(guids.subList(i, Math.min(i + BULK_FETCH_SIZE, guids.size())), true, false);
            if (bulk != null && bulk.getEntities() != null) {
                bulk.getEntities().forEach(process -> checkProcess(process, findings));
            }
        }
        return findings;
    }

    /** Checks that no two active entities of exactly {@code typeName} share a qualifiedName. */
    public List<Finding> checkDuplicates(String typeName) {
        return duplicates(activeEntities(typeName, false));
    }

    private void checkProcess(AtlasEntity process, List<Finding> findings) {
        String qualifiedName = String.valueOf(process.getAttribute("qualifiedName"));
        Edges inputs = edges(process, "inputs");
        Edges outputs = edges(process, "outputs");

        for (AtlasRelatedObjectId deleted : inputs.deletedTargets) {
            findings.add(new Finding(Kind.DELETED_REFERENCE, process.getTypeName(), qualifiedName, process.getGuid(),
                    "input " + describe(deleted) + " is deleted"));
        }
        for (AtlasRelatedObjectId deleted : outputs.deletedTargets) {
            findings.add(new Finding(Kind.DELETED_REFERENCE, process.getTypeName(), qualifiedName, process.getGuid(),
                    "output " + describe(deleted) + " is deleted"));
        }
        if (inputs.active == 0 && outputs.active == 0) {
            findings.add(new Finding(Kind.ORPHAN_PROCESS, process.getTypeName(), qualifiedName, process.getGuid(),
                    "has no inputs and no outputs" + inputs.note("inputs") + outputs.note("outputs")));
        } else if (inputs.active == 0) {
            findings.add(new Finding(Kind.INCOMPLETE_PROCESS, process.getTypeName(), qualifiedName, process.getGuid(),
                    "has no inputs" + inputs.note("inputs")));
        } else if (outputs.active == 0) {
            findings.add(new Finding(Kind.INCOMPLETE_PROCESS, process.getTypeName(), qualifiedName, process.getGuid(),
                    "has no outputs" + outputs.note("outputs")));
        }
    }

    /** Edges of one side of a process. */
    private static final class Edges {
        int active;
        int removed;
        final List<AtlasRelatedObjectId> deletedTargets = new ArrayList<>();

        String note(String side) {
            return removed == 0 ? "" : " (" + removed + " former " + side + " were deleted)";
        }
    }

    /**
     * Classifies the edges in {@code relationshipAttributes}: active edges to active entities count,
     * active edges to deleted entities are broken, and deleted edges are history (they remain when a
     * process is re-registered with other datasets, or when a dataset is deleted).
     */
    private static Edges edges(AtlasEntity process, String side) {
        Edges edges = new Edges();
        Map<String, Object> relationships = process.getRelationshipAttributes();
        Object value = relationships == null ? null : relationships.get(side);
        if (value == null) {
            Object attribute = process.getAttribute(side);
            edges.active = attribute instanceof Collection ? ((Collection<?>) attribute).size() : 0;
            return edges;
        }
        for (Object item : value instanceof Collection ? (Collection<?>) value : List.of(value)) {
            AtlasRelatedObjectId related = AtlasJson.convert(item, AtlasRelatedObjectId.class);
            boolean edgeActive = related.getRelationshipStatus() != EntityStatus.DELETED;
            boolean targetActive = related.getEntityStatus() != EntityStatus.DELETED;
            if (edgeActive && targetActive) {
                edges.active++;
            } else if (edgeActive) {
                edges.deletedTargets.add(related);
            } else if (!targetActive) {
                edges.removed++;
            }
        }
        return edges;
    }

    private static String describe(AtlasRelatedObjectId related) {
        String name = related.getDisplayText() != null ? related.getDisplayText() : related.getGuid();
        return related.getTypeName() + ":" + name;
    }

    private List<AtlasEntityHeader> activeEntities(String typeName, boolean includeSubTypes) {
        SearchParameters parameters = SearchParameters.forType(typeName);
        parameters.setIncludeSubTypes(includeSubTypes);
        parameters.setExcludeDeletedEntities(true);
        return client.search().basicStream(parameters).collect(Collectors.toList());
    }

    private static List<Finding> duplicates(List<AtlasEntityHeader> entities) {
        Map<String, List<AtlasEntityHeader>> byName = new LinkedHashMap<>();
        for (AtlasEntityHeader entity : entities) {
            if (entity.qualifiedName() != null) {
                byName.computeIfAbsent(entity.getTypeName() + "\u0000" + entity.qualifiedName(), k -> new ArrayList<>())
                        .add(entity);
            }
        }
        List<Finding> findings = new ArrayList<>();
        byName.values().stream().filter(group -> group.size() > 1).forEach(group -> {
            AtlasEntityHeader first = group.get(0);
            String others = group.stream().skip(1).map(AtlasEntityHeader::getGuid).collect(Collectors.joining(", "));
            findings.add(new Finding(Kind.DUPLICATE, first.getTypeName(), first.qualifiedName(), first.getGuid(),
                    group.size() + " active entities share this qualifiedName; others: " + others));
        });
        return findings;
    }
}
