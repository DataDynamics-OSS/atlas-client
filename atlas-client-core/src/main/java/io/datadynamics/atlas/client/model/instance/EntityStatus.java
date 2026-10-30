package io.datadynamics.atlas.client.model.instance;

/**
 * Lifecycle status of an entity. {@code DELETED} is a soft delete; {@code PURGED} entities are gone.
 */
public enum EntityStatus {
    ACTIVE,
    DELETED,
    PURGED
}
