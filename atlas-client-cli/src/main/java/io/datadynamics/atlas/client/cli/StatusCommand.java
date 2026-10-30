package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.api.AdminApi.AtlasVersion;
import picocli.CommandLine.Command;

@Command(name = "status", description = "Shows the Atlas version and server status (ACTIVE, PASSIVE, ...).")
final class StatusCommand extends ClientCommand {

    @Override
    public Integer call() {
        AtlasVersion version = client().admin().version();
        String status = client().admin().status();
        out().printf("Atlas %s (%s)%n", version.getVersion(), status);
        return "ACTIVE".equals(status) ? 0 : AtlasCli.EXIT_FAILURE;
    }
}
