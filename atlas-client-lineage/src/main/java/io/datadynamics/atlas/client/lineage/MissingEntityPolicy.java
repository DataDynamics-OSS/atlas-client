package io.datadynamics.atlas.client.lineage;

/**
 * What to do when an input or output of a process does not exist in Atlas yet.
 */
public enum MissingEntityPolicy {

    /** Check every referenced entity first and fail with a list of the missing ones (default). */
    FAIL,

    /**
     * Create missing entities with {@code qualifiedName}, {@code name} and the attributes given in
     * {@link EntityRef#attributes()}. Types with other mandatory attributes (e.g. {@code hive_table}
     * needs {@code db}) must supply them there.
     */
    CREATE,

    /** Send the request without checking; Atlas rejects it if a reference cannot be resolved. */
    NO_CHECK
}
