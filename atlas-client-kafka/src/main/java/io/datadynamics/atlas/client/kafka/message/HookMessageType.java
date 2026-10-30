package io.datadynamics.atlas.client.kafka.message;

/** Types of the V2 hook messages Atlas consumes from the {@code ATLAS_HOOK} topic. */
public enum HookMessageType {
    ENTITY_CREATE_V2,
    ENTITY_PARTIAL_UPDATE_V2,
    ENTITY_FULL_UPDATE_V2,
    ENTITY_DELETE_V2
}
