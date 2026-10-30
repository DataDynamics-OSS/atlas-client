package io.datadynamics.atlas.client.lineage;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.lineage.graph.Impact;
import io.datadynamics.atlas.client.lineage.graph.LineageGraph;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo.LineageDirection;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo.LineageRelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Registers and queries lineage.
 * <pre>{@code
 * AtlasLineage lineage = new AtlasLineage(atlas);
 *
 * lineage.process("etl_job", "daily_sales_agg@prod")
 *     .inputs(EntityRef.of("hive_table", "raw.sales@prod"))
 *     .outputs(EntityRef.of("hive_table", "mart.daily_sales@prod"))
 *     .register();
 *
 * LineageGraph graph = lineage.graph(EntityRef.of("hive_table", "mart.daily_sales@prod"));
 * System.out.println(graph.toMermaid());
 *
 * lineage.impact(EntityRef.of("hive_table", "raw.sales@prod"), 5)
 *     .forEach(i -> System.out.println(i.dataset() + " at distance " + i.distance()));
 * }</pre>
 * Column-level lineage is registered as entities of {@link #columnLineageType(String, String)}
 * ({@code hive_column_lineage} by default, the only column lineage type Atlas ships), linked
 * to their process through the parent attribute ({@code query} by default).
 */
public final class AtlasLineage {

    public static final String DEFAULT_COLUMN_LINEAGE_TYPE = "hive_column_lineage";
    public static final String DEFAULT_COLUMN_LINEAGE_PARENT_ATTRIBUTE = "query";
    public static final int DEFAULT_DEPTH = 3;
    static final int BULK_FETCH_SIZE = 100;

    private static final Logger LOG = LoggerFactory.getLogger(AtlasLineage.class);

    private final AtlasClient client;
    private final Predicate<String> processTypes;
    private MissingEntityPolicy missingEntityPolicy = MissingEntityPolicy.FAIL;
    private String columnLineageType = DEFAULT_COLUMN_LINEAGE_TYPE;
    private String columnLineageParentAttribute = DEFAULT_COLUMN_LINEAGE_PARENT_ATTRIBUTE;
    private boolean includeDeletedRelationships;
    private BinaryOperator<String> columnLineageQualifiedName = (processQualifiedName, outputColumnQualifiedName) ->
            processQualifiedName + ":" + outputColumnQualifiedName;

    public AtlasLineage(AtlasClient client) {
        this(client, new ProcessTypeResolver(client));
    }

    /**
     * @param processTypes tells which entity types are processes; by default resolved from type definitions
     */
    public AtlasLineage(AtlasClient client, Predicate<String> processTypes) {
        this.client = Objects.requireNonNull(client, "client");
        this.processTypes = Objects.requireNonNull(processTypes, "processTypes");
    }

    // ---------------------------------------------------------------- configuration

    public AtlasLineage missingEntities(MissingEntityPolicy policy) {
        this.missingEntityPolicy = Objects.requireNonNull(policy, "policy");
        return this;
    }

    /**
     * @param typeName        entity type of column lineage processes (must extend {@code Process})
     * @param parentAttribute attribute referencing the owning process, or {@code null} for none
     */
    public AtlasLineage columnLineageType(String typeName, String parentAttribute) {
        this.columnLineageType = Objects.requireNonNull(typeName, "typeName");
        this.columnLineageParentAttribute = parentAttribute;
        return this;
    }

    /** How column lineage entities are named, from the process and output column qualified names. */
    public AtlasLineage columnLineageQualifiedName(BinaryOperator<String> naming) {
        this.columnLineageQualifiedName = Objects.requireNonNull(naming, "naming");
        return this;
    }

    /**
     * Whether graphs keep edges whose relationship is soft-deleted (default {@code false}).
     * <p>
     * When a process is registered again with other inputs or outputs, Atlas soft-deletes the old
     * relationships but its lineage API still returns them. By default they are removed, so the
     * graph shows the current lineage; enable this to see historical edges too. Filtering costs
     * one bulk entity call per {@value #BULK_FETCH_SIZE} processes in the graph.
     */
    public AtlasLineage includeDeletedRelationships(boolean include) {
        this.includeDeletedRelationships = include;
        return this;
    }

    // ---------------------------------------------------------------- registration

    public ProcessBuilder process(String typeName, String qualifiedName) {
        return new ProcessBuilder(this, typeName, qualifiedName);
    }

    /**
     * Registers several processes in one Atlas transaction: either all succeed or none.
     *
     * @throws LineageException if referenced entities are missing and the policy is {@link MissingEntityPolicy#FAIL}
     */
    public LineageRegistration register(List<ProcessBuilder> processes) {
        Payload payload = payload(processes);
        LOG.debug("Registering lineage: {} processes, {} entities in total", processes.size(), payload.entities().size());
        EntityMutationResponse response = client.entities().createOrUpdate(payload.entities());
        return new LineageRegistration(resolveGuids(payload.placeholders(), response), payload.missing(), response);
    }

    /**
     * The entities {@link #register(List)} would send, for sending them another way, e.g. through
     * the {@code ATLAS_HOOK} topic with {@code AtlasHookPublisher.createOrUpdate(...)}.
     * The missing entity policy is applied as for {@code register}; use
     * {@link MissingEntityPolicy#NO_CHECK} to build the payload without calling Atlas.
     *
     * @throws LineageException if referenced entities are missing and the policy is {@link MissingEntityPolicy#FAIL}
     */
    public AtlasEntitiesWithExtInfo toEntities(List<ProcessBuilder> processes) {
        return new AtlasEntitiesWithExtInfo(payload(processes).entities());
    }

    private record Payload(List<AtlasEntity> entities, Map<String, AtlasEntity> placeholders, List<EntityRef> missing) {
    }

    private Payload payload(List<ProcessBuilder> processes) {
        processes.forEach(ProcessBuilder::validate);

        Map<String, EntityRef> referenced = new LinkedHashMap<>();
        for (ProcessBuilder process : processes) {
            process.inputs().forEach(r -> referenced.putIfAbsent(r.key(), r));
            process.outputs().forEach(r -> referenced.putIfAbsent(r.key(), r));
            for (ProcessBuilder.ColumnLineage column : process.columnLineages()) {
                referenced.putIfAbsent(column.output().key(), column.output());
                column.inputs().forEach(r -> referenced.putIfAbsent(r.key(), r));
            }
        }

        List<EntityRef> missing = missingEntityPolicy == MissingEntityPolicy.NO_CHECK ? List.of()
                : referenced.values().stream()
                .filter(r -> client.entities().findGuid(r.typeName(), r.qualifiedName()).isEmpty())
                .collect(Collectors.toList());
        if (!missing.isEmpty() && missingEntityPolicy == MissingEntityPolicy.FAIL) {
            throw new LineageException("Lineage references entities that do not exist in Atlas: " + missing
                    + " (create them first, or use MissingEntityPolicy.CREATE)", missing);
        }

        List<AtlasEntity> entities = new ArrayList<>();
        Map<String, AtlasEntity> placeholders = new LinkedHashMap<>();
        for (EntityRef ref : missing) {
            AtlasEntity stub = newEntity(ref.typeName(), ref.qualifiedName(), ref.defaultName(), ref.attributes());
            placeholders.put(ref.key(), stub);
            entities.add(stub);
        }

        for (ProcessBuilder process : processes) {
            EntityRef processRef = process.process();
            AtlasEntity processEntity = newEntity(processRef.typeName(), processRef.qualifiedName(),
                    processRef.defaultName(), process.attributes());
            processEntity.setAttribute("inputs", references(process.inputs(), placeholders));
            processEntity.setAttribute("outputs", references(process.outputs(), placeholders));
            placeholders.put(processRef.key(), processEntity);
            entities.add(processEntity);

            for (ProcessBuilder.ColumnLineage column : process.columnLineages()) {
                String qualifiedName = columnLineageQualifiedName.apply(processRef.qualifiedName(),
                        column.output().qualifiedName());
                AtlasEntity columnEntity = newEntity(columnLineageType, qualifiedName, qualifiedName, column.attributes());
                columnEntity.setAttribute("inputs", references(column.inputs(), placeholders));
                columnEntity.setAttribute("outputs", references(List.of(column.output()), placeholders));
                if (columnLineageParentAttribute != null) {
                    columnEntity.setAttribute(columnLineageParentAttribute, processEntity.toObjectId());
                }
                placeholders.put(EntityRef.of(columnLineageType, qualifiedName).key(), columnEntity);
                entities.add(columnEntity);
            }
        }
        return new Payload(entities, placeholders, missing);
    }

    /** Deletes the process entity, which removes its lineage edges. */
    public EntityMutationResponse unregister(EntityRef process) {
        return client.entities().deleteByQualifiedName(process.typeName(), process.qualifiedName());
    }

    private static AtlasEntity newEntity(String typeName, String qualifiedName, String defaultName,
                                         Map<String, Object> attributes) {
        AtlasEntity entity = new AtlasEntity(typeName);
        entity.setAttribute(AtlasObjectId.QUALIFIED_NAME, qualifiedName);
        entity.setAttribute("name", defaultName);
        attributes.forEach(entity::setAttribute);
        return entity.assignPlaceholderGuid();
    }

    private static List<AtlasObjectId> references(List<EntityRef> refs, Map<String, AtlasEntity> placeholders) {
        return refs.stream()
                .map(r -> placeholders.containsKey(r.key()) ? placeholders.get(r.key()).toObjectId() : r.toObjectId())
                .collect(Collectors.toList());
    }

    private static Map<String, String> resolveGuids(Map<String, AtlasEntity> placeholders, EntityMutationResponse response) {
        Map<String, String> assignments = response == null || response.getGuidAssignments() == null
                ? Map.of() : response.getGuidAssignments();
        Map<String, String> guids = new LinkedHashMap<>();
        placeholders.forEach((key, entity) -> {
            String guid = assignments.get(entity.getGuid());
            if (guid == null && response != null) {
                guid = response.findGuid(entity.getTypeName(), String.valueOf(entity.getAttribute(AtlasObjectId.QUALIFIED_NAME)))
                        .orElse(null);
            }
            if (guid != null) {
                guids.put(key, guid);
            }
        });
        return guids;
    }

    // ---------------------------------------------------------------- queries

    public LineageGraph graph(EntityRef entity) {
        return graph(entity, LineageDirection.BOTH, DEFAULT_DEPTH);
    }

    /**
     * @param depth number of process hops to follow in each direction
     */
    public LineageGraph graph(EntityRef entity, LineageDirection direction, int depth) {
        AtlasLineageInfo info = client.lineage().getByQualifiedName(entity.typeName(), entity.qualifiedName(),
                direction, depth);
        AtlasEntityHeader fallback = new AtlasEntityHeader();
        fallback.setTypeName(entity.typeName());
        fallback.setAttribute(AtlasObjectId.QUALIFIED_NAME, entity.qualifiedName());
        return LineageGraph.from(withoutDeletedRelationships(info), processTypes, fallback);
    }

    public LineageGraph graph(String guid, LineageDirection direction, int depth) {
        return LineageGraph.from(withoutDeletedRelationships(client.lineage().get(guid, direction, depth)), processTypes);
    }

    /**
     * Removes relations whose relationship is soft-deleted, then entities no longer connected to
     * anything (except the base entity). Relationship status is read from the processes'
     * {@code relationshipAttributes}, since the lineage response does not carry it.
     */
    private AtlasLineageInfo withoutDeletedRelationships(AtlasLineageInfo info) {
        if (includeDeletedRelationships || info.getRelations().isEmpty()) {
            return info;
        }
        List<String> processGuids = info.getGuidEntityMap().values().stream()
                .filter(h -> processTypes.test(h.getTypeName()))
                .map(AtlasEntityHeader::getGuid)
                .collect(Collectors.toList());
        Set<String> deleted = new HashSet<>();
        for (int i = 0; i < processGuids.size(); i += BULK_FETCH_SIZE) {
            List<String> chunk = processGuids.subList(i, Math.min(i + BULK_FETCH_SIZE, processGuids.size()));
            AtlasEntitiesWithExtInfo processes = client.entities().getBulk(chunk, true, false);
            if (processes != null && processes.getEntities() != null) {
                processes.getEntities().forEach(p -> collectDeletedRelationships(p.getRelationshipAttributes(), deleted));
            }
        }
        if (deleted.isEmpty()) {
            return info;
        }

        Set<LineageRelation> relations = info.getRelations().stream()
                .filter(r -> !deleted.contains(r.getRelationshipId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> connected = new HashSet<>();
        relations.forEach(r -> {
            connected.add(r.getFromEntityId());
            connected.add(r.getToEntityId());
        });
        connected.add(info.getBaseEntityGuid());
        Map<String, AtlasEntityHeader> entities = new LinkedHashMap<>();
        info.getGuidEntityMap().forEach((guid, header) -> {
            if (connected.contains(guid)) {
                entities.put(guid, header);
            }
        });
        LOG.debug("Removed {} soft-deleted lineage relations", info.getRelations().size() - relations.size());

        AtlasLineageInfo filtered = AtlasJson.deepCopy(info);
        filtered.setRelations(relations);
        filtered.setGuidEntityMap(entities);
        return filtered;
    }

    private static void collectDeletedRelationships(Map<String, Object> relationshipAttributes, Set<String> deleted) {
        if (relationshipAttributes == null) {
            return;
        }
        for (Object value : relationshipAttributes.values()) {
            List<?> references = value instanceof List ? (List<?>) value : Collections.singletonList(value);
            for (Object reference : references) {
                if (reference instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) reference;
                    if ("DELETED".equals(map.get("relationshipStatus")) && map.get("relationshipGuid") != null) {
                        deleted.add(map.get("relationshipGuid").toString());
                    }
                }
            }
        }
    }

    /** Datasets that would be affected by a change of {@code entity}, nearest first. */
    public List<Impact> impact(EntityRef entity, int depth) {
        LineageGraph graph = graph(entity, LineageDirection.OUTPUT, depth);
        return graph.impactOf(graph.base());
    }

    /** Datasets {@code entity} is derived from, nearest first. */
    public List<Impact> sources(EntityRef entity, int depth) {
        LineageGraph graph = graph(entity, LineageDirection.INPUT, depth);
        return graph.sourcesOf(graph.base());
    }
}
