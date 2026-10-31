package io.datadynamics.atlas.client.kafka.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.datadynamics.atlas.client.model.AtlasModel;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;

/**
 * A message for the {@code ATLAS_HOOK} topic: the same payloads as the REST API, applied by
 * Atlas asynchronously. Atlas hooks (Hive, Spark, ...) use these messages to report metadata.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = EntityCreateRequest.class, name = "ENTITY_CREATE_V2"),
        @JsonSubTypes.Type(value = EntityPartialUpdateRequest.class, name = "ENTITY_PARTIAL_UPDATE_V2"),
        @JsonSubTypes.Type(value = EntityFullUpdateRequest.class, name = "ENTITY_FULL_UPDATE_V2"),
        @JsonSubTypes.Type(value = EntityDeleteRequest.class, name = "ENTITY_DELETE_V2")})
@JsonIgnoreProperties(value = "type", allowGetters = true)
public abstract class HookMessage extends AtlasModel {

    private final HookMessageType type;
    private String user;

    protected HookMessage(HookMessageType type) {
        this.type = type;
    }

    public HookMessageType getType() {
        return type;
    }

    /** User Atlas records as the author of the change. */
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Checks what Atlas would otherwise reject while consuming the message, where the failure
     * only shows up in the Atlas server log.
     *
     * @throws IllegalArgumentException if the message cannot be processed by Atlas
     */
    public abstract void validate();

    /** Atlas resolves partial updates and deletes by type name and unique attributes, not by guid. */
    static void requireUniqueAttributes(AtlasObjectId id, String what) {
        if (id == null || id.getTypeName() == null || id.getUniqueAttributes() == null
                || id.getUniqueAttributes().values().stream().allMatch(HookMessage::isBlank)) {
            throw new IllegalArgumentException(what + " needs a typeName and uniqueAttributes "
                    + "(Atlas looks entities of hook messages up by unique attributes): " + id);
        }
    }

    private static boolean isBlank(Object value) {
        return value == null || value.toString().isBlank();
    }
}
