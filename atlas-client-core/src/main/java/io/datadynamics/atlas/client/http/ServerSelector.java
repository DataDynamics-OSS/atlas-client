package io.datadynamics.atlas.client.http;

import io.datadynamics.atlas.client.exception.AtlasConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Picks the Atlas server to talk to.
 * <p>
 * With a single URL it is always used. With several (Atlas HA), each server is probed through
 * {@code /api/atlas/admin/status} and the one reporting {@code ACTIVE} is cached until a request
 * against it fails, at which point the next request probes again.
 */
final class ServerSelector {

    static final String ACTIVE = "ACTIVE";

    private static final Logger LOG = LoggerFactory.getLogger(ServerSelector.class);

    private final List<URI> servers;
    private final Function<URI, String> statusProbe;
    private volatile URI active;

    /**
     * @param statusProbe returns the server status (e.g. {@code ACTIVE}, {@code PASSIVE}) or throws when unreachable
     */
    ServerSelector(List<URI> servers, Function<URI, String> statusProbe) {
        this.servers = List.copyOf(servers);
        this.statusProbe = statusProbe;
    }

    boolean isHighAvailability() {
        return servers.size() > 1;
    }

    URI current() {
        if (!isHighAvailability()) {
            return servers.get(0);
        }
        URI cached = active;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (active == null) {
                active = detectActive();
            }
            return active;
        }
    }

    /** Forgets the cached active server if it is the one that just failed. */
    void markFailed(URI server) {
        if (isHighAvailability() && Objects.equals(active, server)) {
            LOG.info("Atlas server {} failed; active server will be re-detected", server);
            active = null;
        }
    }

    private URI detectActive() {
        Map<URI, String> states = new LinkedHashMap<>();
        for (URI server : servers) {
            String status;
            try {
                status = statusProbe.apply(server);
            } catch (RuntimeException e) {
                status = "UNREACHABLE (" + e.getMessage() + ")";
            }
            states.put(server, status);
            if (ACTIVE.equalsIgnoreCase(status)) {
                LOG.info("Using active Atlas server {}", server);
                return server;
            }
        }
        throw new AtlasConnectionException("No active Atlas server found: " + states);
    }
}
