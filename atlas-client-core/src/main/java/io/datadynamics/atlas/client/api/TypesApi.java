package io.datadynamics.atlas.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.datadynamics.atlas.client.exception.AtlasNotFoundException;
import io.datadynamics.atlas.client.http.AtlasTransport;
import io.datadynamics.atlas.client.http.QueryParams;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasBusinessMetadataDef;
import io.datadynamics.atlas.client.model.typedef.AtlasClassificationDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEnumDef;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef;
import io.datadynamics.atlas.client.model.typedef.AtlasStructDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypeDefHeader;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.model.typedef.TypeCategory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.datadynamics.atlas.client.http.AtlasTransport.DELETE;
import static io.datadynamics.atlas.client.http.AtlasTransport.GET;
import static io.datadynamics.atlas.client.http.AtlasTransport.POST;
import static io.datadynamics.atlas.client.http.AtlasTransport.PUT;

/**
 * {@code /api/atlas/v2/types}: read, create, update and delete type definitions.
 * <p>
 * Besides the raw calls, {@link #createOrUpdate(AtlasTypesDef)} removes the need to know whether
 * a type already exists, which Atlas' separate create (fails if it exists) and update (fails if
 * it does not) calls otherwise require.
 */
public final class TypesApi {

    private static final String BASE = "/api/atlas/v2/types";

    private static final Map<Class<? extends AtlasBaseTypeDef>, String> DEF_PATHS = Map.of(
            AtlasEnumDef.class, "enumdef",
            AtlasStructDef.class, "structdef",
            AtlasClassificationDef.class, "classificationdef",
            AtlasEntityDef.class, "entitydef",
            AtlasRelationshipDef.class, "relationshipdef",
            AtlasBusinessMetadataDef.class, "businessmetadatadef");

    private final AtlasTransport transport;

    public TypesApi(AtlasTransport transport) {
        this.transport = transport;
    }

    /** All type definitions known to Atlas (including built-in ones). */
    public AtlasTypesDef getAll() {
        return transport.call(GET, BASE + "/typedefs", null, null, AtlasTypesDef.class);
    }

    /** All type definitions of one category. */
    public AtlasTypesDef getAll(TypeCategory category) {
        QueryParams query = QueryParams.create().add("type", filterValue(category));
        return transport.call(GET, BASE + "/typedefs", query, null, AtlasTypesDef.class);
    }

    public List<AtlasTypeDefHeader> getHeaders() {
        return transport.call(GET, BASE + "/typedefs/headers", null, null,
                new TypeReference<List<AtlasTypeDefHeader>>() {
                });
    }

    /** Names of all types; a cheap way to check the existence of many types at once. */
    public Set<String> getTypeNames() {
        return getHeaders().stream().map(AtlasTypeDefHeader::getName).collect(Collectors.toSet());
    }

    /**
     * Any type definition by name, deserialized to the class matching its category.
     */
    public Optional<AtlasBaseTypeDef> findByName(String name) {
        return ApiSupport.findOptional(() -> {
            JsonNode node = transport.call(GET, BASE + "/typedef/name/" + QueryParams.pathSegment(name),
                    null, null, JsonNode.class);
            return node == null ? null : AtlasJson.convert(node, classFor(node.path("category").asText()));
        });
    }

    public boolean exists(String name) {
        return findByName(name).isPresent();
    }

    /**
     * Type definition by name and expected kind.
     *
     * @throws AtlasNotFoundException if no such type exists
     */
    public <T extends AtlasBaseTypeDef> T get(String name, Class<T> defClass) {
        String segment = DEF_PATHS.get(defClass);
        if (segment == null) {
            throw new IllegalArgumentException("Unsupported type definition class: " + defClass.getName());
        }
        return transport.call(GET, BASE + "/" + segment + "/name/" + QueryParams.pathSegment(name),
                null, null, defClass);
    }

    public <T extends AtlasBaseTypeDef> Optional<T> find(String name, Class<T> defClass) {
        return ApiSupport.findOptional(() -> get(name, defClass));
    }

    public Optional<AtlasEntityDef> findEntityDef(String name) {
        return find(name, AtlasEntityDef.class);
    }

    public Optional<AtlasClassificationDef> findClassificationDef(String name) {
        return find(name, AtlasClassificationDef.class);
    }

    public Optional<AtlasRelationshipDef> findRelationshipDef(String name) {
        return find(name, AtlasRelationshipDef.class);
    }

    public Optional<AtlasEnumDef> findEnumDef(String name) {
        return find(name, AtlasEnumDef.class);
    }

    public Optional<AtlasStructDef> findStructDef(String name) {
        return find(name, AtlasStructDef.class);
    }

    public Optional<AtlasBusinessMetadataDef> findBusinessMetadataDef(String name) {
        return find(name, AtlasBusinessMetadataDef.class);
    }

    /** Creates new types. Fails with {@link io.datadynamics.atlas.client.exception.AtlasConflictException} if any already exists. */
    public AtlasTypesDef create(AtlasTypesDef typesDef) {
        return transport.call(POST, BASE + "/typedefs", null, typesDef, AtlasTypesDef.class);
    }

    /** Updates existing types. Fails with {@link AtlasNotFoundException} if any does not exist. */
    public AtlasTypesDef update(AtlasTypesDef typesDef) {
        return transport.call(PUT, BASE + "/typedefs", null, typesDef, AtlasTypesDef.class);
    }

    /**
     * Creates the types that do not exist yet and updates the others.
     * New types are created first so that updated types may reference them.
     *
     * @return the definitions as stored by Atlas
     */
    public AtlasTypesDef createOrUpdate(AtlasTypesDef typesDef) {
        Set<String> existing = getTypeNames();
        AtlasTypesDef toCreate = new AtlasTypesDef();
        AtlasTypesDef toUpdate = new AtlasTypesDef();
        typesDef.stream().forEach(def -> (existing.contains(def.getName()) ? toUpdate : toCreate).add(def));

        AtlasTypesDef result = new AtlasTypesDef();
        if (!toCreate.isEmpty()) {
            AtlasTypesDef created = create(toCreate);
            if (created != null) {
                created.stream().forEach(result::add);
            }
        }
        if (!toUpdate.isEmpty()) {
            AtlasTypesDef updated = update(toUpdate);
            if (updated != null) {
                updated.stream().forEach(result::add);
            }
        }
        return result;
    }

    /** Deletes the given types. Atlas refuses if instances or sub-types still exist. */
    public void delete(AtlasTypesDef typesDef) {
        transport.call(DELETE, BASE + "/typedefs", null, typesDef, Void.class);
    }

    public void deleteByName(String name) {
        transport.call(DELETE, BASE + "/typedef/name/" + QueryParams.pathSegment(name), null, null, Void.class);
    }

    private static String filterValue(TypeCategory category) {
        switch (category) {
            case ENUM:
                return "enum";
            case STRUCT:
                return "struct";
            case CLASSIFICATION:
                return "classification";
            case ENTITY:
                return "entity";
            case RELATIONSHIP:
                return "relationship";
            case BUSINESS_METADATA:
                return "business_metadata";
            default:
                throw new IllegalArgumentException("Cannot list types of category " + category);
        }
    }

    private static Class<? extends AtlasBaseTypeDef> classFor(String category) {
        switch (category) {
            case "ENUM":
                return AtlasEnumDef.class;
            case "CLASSIFICATION":
                return AtlasClassificationDef.class;
            case "ENTITY":
                return AtlasEntityDef.class;
            case "RELATIONSHIP":
                return AtlasRelationshipDef.class;
            case "BUSINESS_METADATA":
                return AtlasBusinessMetadataDef.class;
            case "STRUCT":
                return AtlasStructDef.class;
            default:
                throw new IllegalStateException("Unexpected type category: " + category);
        }
    }
}
