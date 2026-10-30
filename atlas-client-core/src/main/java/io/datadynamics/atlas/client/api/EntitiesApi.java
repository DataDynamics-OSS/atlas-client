package io.datadynamics.atlas.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.datadynamics.atlas.client.http.AtlasTransport;
import io.datadynamics.atlas.client.http.QueryParams;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.instance.AtlasClassification;
import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;
import io.datadynamics.atlas.client.model.instance.AtlasEntityWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;
import io.datadynamics.atlas.client.model.instance.ClassificationAssociateRequest;
import io.datadynamics.atlas.client.model.instance.EntityAuditEvent;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.datadynamics.atlas.client.http.AtlasTransport.DELETE;
import static io.datadynamics.atlas.client.http.AtlasTransport.GET;
import static io.datadynamics.atlas.client.http.AtlasTransport.POST;
import static io.datadynamics.atlas.client.http.AtlasTransport.PUT;

/**
 * {@code /api/atlas/v2/entity}: entity CRUD, classifications, labels, business metadata and audits.
 * <p>
 * Every lookup is available by guid and by {@code typeName + qualifiedName}, so callers rarely
 * need to resolve guids themselves. {@code find*} methods return {@link Optional#empty()} for
 * missing entities; {@code get*} methods throw {@link io.datadynamics.atlas.client.exception.AtlasNotFoundException}.
 */
public final class EntitiesApi {

    private static final String BASE = "/api/atlas/v2/entity";

    private final AtlasTransport transport;

    public EntitiesApi(AtlasTransport transport) {
        this.transport = transport;
    }

    // ---------------------------------------------------------------- read

    public AtlasEntityWithExtInfo get(String guid) {
        return get(guid, false, false);
    }

    /**
     * @param minExtInfo          return only minimal information about referred entities
     * @param ignoreRelationships skip {@code relationshipAttributes}, which is much faster for entities
     *                            with many relationships (e.g. tables with thousands of columns)
     */
    public AtlasEntityWithExtInfo get(String guid, boolean minExtInfo, boolean ignoreRelationships) {
        QueryParams query = QueryParams.create()
                .add("minExtInfo", minExtInfo)
                .add("ignoreRelationships", ignoreRelationships);
        return transport.call(GET, guidPath(guid), query, null, AtlasEntityWithExtInfo.class);
    }

    public Optional<AtlasEntityWithExtInfo> find(String guid) {
        return ApiSupport.findOptional(() -> get(guid));
    }

    public AtlasEntityHeader getHeader(String guid) {
        return transport.call(GET, guidPath(guid) + "/header", null, null, AtlasEntityHeader.class);
    }

    public AtlasEntityWithExtInfo getByUniqueAttributes(String typeName, Map<String, ?> uniqueAttributes) {
        return getByUniqueAttributes(typeName, uniqueAttributes, false, false);
    }

    public AtlasEntityWithExtInfo getByUniqueAttributes(String typeName, Map<String, ?> uniqueAttributes,
                                                        boolean minExtInfo, boolean ignoreRelationships) {
        QueryParams query = QueryParams.create()
                .uniqueAttributes(uniqueAttributes)
                .add("minExtInfo", minExtInfo)
                .add("ignoreRelationships", ignoreRelationships);
        return transport.call(GET, uniqueAttributePath(typeName), query, null, AtlasEntityWithExtInfo.class);
    }

    public AtlasEntityWithExtInfo getByQualifiedName(String typeName, String qualifiedName) {
        return getByUniqueAttributes(typeName, Map.of(AtlasObjectId.QUALIFIED_NAME, qualifiedName));
    }

    public Optional<AtlasEntityWithExtInfo> findByQualifiedName(String typeName, String qualifiedName) {
        return ApiSupport.findOptional(() -> getByQualifiedName(typeName, qualifiedName));
    }

    /** Resolves the guid of an entity by {@code qualifiedName}, without fetching relationships. */
    public Optional<String> findGuid(String typeName, String qualifiedName) {
        return ApiSupport.findOptional(() -> getByUniqueAttributes(typeName,
                        Map.of(AtlasObjectId.QUALIFIED_NAME, qualifiedName), true, true))
                .map(e -> e.getEntity().getGuid());
    }

    /** Resolves a reference (by guid or by unique attributes) to the entity it points to. */
    public AtlasEntityWithExtInfo get(AtlasObjectId objectId) {
        if (objectId.getGuid() != null) {
            return get(objectId.getGuid());
        }
        return getByUniqueAttributes(objectId.getTypeName(), objectId.getUniqueAttributes());
    }

    public AtlasEntitiesWithExtInfo getBulk(Collection<String> guids) {
        return getBulk(guids, false, false);
    }

    public AtlasEntitiesWithExtInfo getBulk(Collection<String> guids, boolean minExtInfo, boolean ignoreRelationships) {
        QueryParams query = QueryParams.create()
                .addAll("guid", guids)
                .add("minExtInfo", minExtInfo)
                .add("ignoreRelationships", ignoreRelationships);
        return transport.call(GET, BASE + "/bulk", query, null, AtlasEntitiesWithExtInfo.class);
    }

    /**
     * Several entities of one type by {@code qualifiedName}. Atlas 2.3 skips the ones that do not
     * exist instead of failing the call; older versions have not been verified.
     */
    public AtlasEntitiesWithExtInfo getBulkByQualifiedNames(String typeName, Collection<String> qualifiedNames) {
        QueryParams query = QueryParams.create();
        int i = 0;
        for (String qualifiedName : qualifiedNames) {
            query.add("attr_" + i++ + ":" + AtlasObjectId.QUALIFIED_NAME, qualifiedName);
        }
        return transport.call(GET, BASE + "/bulk/uniqueAttribute/type/" + QueryParams.pathSegment(typeName),
                query, null, AtlasEntitiesWithExtInfo.class);
    }

    // ---------------------------------------------------------------- create / update

    /**
     * Creates the entity, or updates it if an entity with the same unique attributes
     * ({@code qualifiedName}) exists. Attributes not present are left unchanged.
     */
    public EntityMutationResponse createOrUpdate(AtlasEntity entity) {
        return createOrUpdate(new AtlasEntityWithExtInfo(entity));
    }

    public EntityMutationResponse createOrUpdate(AtlasEntityWithExtInfo entity) {
        return transport.call(POST, BASE, null, entity, EntityMutationResponse.class);
    }

    /**
     * Creates or updates several entities in one transaction. Entities may reference each other
     * through placeholder guids ({@link AtlasEntity#assignPlaceholderGuid()}) or qualified names.
     */
    public EntityMutationResponse createOrUpdate(Collection<AtlasEntity> entities) {
        return createOrUpdate(new AtlasEntitiesWithExtInfo(entities));
    }

    public EntityMutationResponse createOrUpdate(AtlasEntitiesWithExtInfo entities) {
        return transport.call(POST, BASE + "/bulk", null, entities, EntityMutationResponse.class);
    }

    /**
     * Updates only the attributes present in {@code entity} of the entity identified by unique attributes.
     */
    public EntityMutationResponse partialUpdateByUniqueAttributes(String typeName, Map<String, ?> uniqueAttributes,
                                                                  AtlasEntity entity) {
        QueryParams query = QueryParams.create().uniqueAttributes(uniqueAttributes);
        return transport.call(PUT, uniqueAttributePath(typeName), query, new AtlasEntityWithExtInfo(entity),
                EntityMutationResponse.class);
    }

    public EntityMutationResponse partialUpdateByQualifiedName(String typeName, String qualifiedName, AtlasEntity entity) {
        return partialUpdateByUniqueAttributes(typeName, Map.of(AtlasObjectId.QUALIFIED_NAME, qualifiedName), entity);
    }

    /** Sets a single attribute of the entity. The value is sent as JSON (strings, numbers, maps, references...). */
    public EntityMutationResponse updateAttribute(String guid, String attributeName, Object value) {
        QueryParams query = QueryParams.create().add("name", attributeName);
        return transport.call(PUT, guidPath(guid), query, value, EntityMutationResponse.class);
    }

    // ---------------------------------------------------------------- delete

    /** Deletes the entity (soft delete by default Atlas configuration; see {@link AdminApi#purge}). */
    public EntityMutationResponse delete(String guid) {
        return transport.call(DELETE, guidPath(guid), null, null, EntityMutationResponse.class);
    }

    public EntityMutationResponse deleteByQualifiedName(String typeName, String qualifiedName) {
        QueryParams query = QueryParams.create().uniqueAttributes(Map.of(AtlasObjectId.QUALIFIED_NAME, qualifiedName));
        return transport.call(DELETE, uniqueAttributePath(typeName), query, null, EntityMutationResponse.class);
    }

    public EntityMutationResponse delete(Collection<String> guids) {
        QueryParams query = QueryParams.create().addAll("guid", guids);
        return transport.call(DELETE, BASE + "/bulk", query, null, EntityMutationResponse.class);
    }

    // ---------------------------------------------------------------- classifications

    public List<AtlasClassification> getClassifications(String guid) {
        JsonNode node = transport.call(GET, guidPath(guid) + "/classifications", null, null, JsonNode.class);
        if (node == null || !node.has("list")) {
            return Collections.emptyList();
        }
        return AtlasJson.mapper().convertValue(node.get("list"), new TypeReference<List<AtlasClassification>>() {
        });
    }

    public void addClassifications(String guid, List<AtlasClassification> classifications) {
        transport.call(POST, guidPath(guid) + "/classifications", null, classifications, Void.class);
    }

    public void addClassification(String guid, AtlasClassification classification) {
        addClassifications(guid, List.of(classification));
    }

    public void addClassificationsByQualifiedName(String typeName, String qualifiedName,
                                                  List<AtlasClassification> classifications) {
        QueryParams query = QueryParams.create().uniqueAttributes(Map.of(AtlasObjectId.QUALIFIED_NAME, qualifiedName));
        transport.call(POST, uniqueAttributePath(typeName) + "/classifications", query, classifications, Void.class);
    }

    /** Attaches one classification to many entities in a single call. */
    public void addClassificationToEntities(AtlasClassification classification, Collection<String> guids) {
        transport.call(POST, BASE + "/bulk/classification", null,
                new ClassificationAssociateRequest(classification, guids), Void.class);
    }

    /** Updates attributes, propagation or validity of classifications already attached to the entity. */
    public void updateClassifications(String guid, List<AtlasClassification> classifications) {
        transport.call(PUT, guidPath(guid) + "/classifications", null, classifications, Void.class);
    }

    public void removeClassification(String guid, String classificationName) {
        transport.call(DELETE, guidPath(guid) + "/classification/" + QueryParams.pathSegment(classificationName),
                null, null, Void.class);
    }

    public void removeClassificationByQualifiedName(String typeName, String qualifiedName, String classificationName) {
        QueryParams query = QueryParams.create().uniqueAttributes(Map.of(AtlasObjectId.QUALIFIED_NAME, qualifiedName));
        transport.call(DELETE, uniqueAttributePath(typeName) + "/classification/"
                + QueryParams.pathSegment(classificationName), query, null, Void.class);
    }

    // ---------------------------------------------------------------- labels

    /** Replaces all labels of the entity. */
    public void setLabels(String guid, Set<String> labels) {
        transport.call(POST, guidPath(guid) + "/labels", null, new LinkedHashSet<>(labels), Void.class);
    }

    public void addLabels(String guid, Set<String> labels) {
        transport.call(PUT, guidPath(guid) + "/labels", null, new LinkedHashSet<>(labels), Void.class);
    }

    public void removeLabels(String guid, Set<String> labels) {
        transport.call(DELETE, guidPath(guid) + "/labels", null, new LinkedHashSet<>(labels), Void.class);
    }

    // ---------------------------------------------------------------- business metadata

    /**
     * Sets business metadata attributes: {@code {businessMetadataName: {attributeName: value}}}.
     *
     * @param overwrite {@code true} replaces all business metadata of the entity, {@code false} merges
     */
    public void setBusinessMetadata(String guid, Map<String, Map<String, Object>> businessMetadata, boolean overwrite) {
        QueryParams query = QueryParams.create().add("isOverwrite", overwrite);
        transport.call(POST, guidPath(guid) + "/businessmetadata", query, businessMetadata, Void.class);
    }

    public void removeBusinessMetadata(String guid, Map<String, Map<String, Object>> businessMetadata) {
        transport.call(DELETE, guidPath(guid) + "/businessmetadata", null, businessMetadata, Void.class);
    }

    // ---------------------------------------------------------------- audit

    /**
     * Audit events of the entity, most recent first.
     *
     * @param startKey {@link EntityAuditEvent#getEventKey()} of the last event of the previous page, or {@code null}
     * @param count    maximum number of events to return
     */
    public List<EntityAuditEvent> getAuditEvents(String guid, String startKey, int count) {
        QueryParams query = QueryParams.create().add("startKey", startKey).add("count", count);
        List<EntityAuditEvent> events = transport.call(GET, BASE + "/" + QueryParams.pathSegment(guid) + "/audit",
                query, null, new TypeReference<List<EntityAuditEvent>>() {
                });
        return events == null ? new ArrayList<>() : events;
    }

    // ---------------------------------------------------------------- paths

    private static String guidPath(String guid) {
        return BASE + "/guid/" + QueryParams.pathSegment(guid);
    }

    private static String uniqueAttributePath(String typeName) {
        return BASE + "/uniqueAttribute/type/" + QueryParams.pathSegment(typeName);
    }
}
