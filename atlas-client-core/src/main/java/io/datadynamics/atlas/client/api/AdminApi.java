package io.datadynamics.atlas.client.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.datadynamics.atlas.client.http.AtlasTransport;
import io.datadynamics.atlas.client.model.AtlasModel;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;

import java.util.Collection;
import java.util.LinkedHashSet;

import static io.datadynamics.atlas.client.http.AtlasTransport.GET;
import static io.datadynamics.atlas.client.http.AtlasTransport.PUT;

/**
 * {@code /api/atlas/admin}: server version, HA status and purge of soft-deleted entities.
 */
public final class AdminApi {

    private static final String BASE = "/api/atlas/admin";

    private final AtlasTransport transport;

    public AdminApi(AtlasTransport transport) {
        this.transport = transport;
    }

    public AtlasVersion version() {
        return transport.call(GET, BASE + "/version", null, null, AtlasVersion.class);
    }

    /** HA status of the server currently in use, e.g. {@code ACTIVE} or {@code PASSIVE}. */
    public String status() {
        AtlasStatus status = transport.call(GET, BASE + "/status", null, null, AtlasStatus.class);
        return status == null ? null : status.status;
    }

    /**
     * Permanently removes entities that are already soft-deleted ({@code DELETED} status).
     * Requires the admin purge privilege.
     */
    public EntityMutationResponse purge(Collection<String> guids) {
        return transport.call(PUT, BASE + "/purge", null, new LinkedHashSet<>(guids), EntityMutationResponse.class);
    }

    public static class AtlasVersion extends AtlasModel {

        @JsonProperty("Version")
        private String version;
        @JsonProperty("Name")
        private String name;
        @JsonProperty("Description")
        private String description;

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    static class AtlasStatus extends AtlasModel {
        @JsonProperty("Status")
        String status;
    }
}
