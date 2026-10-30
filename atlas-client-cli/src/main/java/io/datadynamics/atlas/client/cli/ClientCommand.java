package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.AtlasClient;
import picocli.CommandLine.Mixin;

/** A command that talks to Atlas over REST. */
abstract class ClientCommand extends CliCommand {

    @Mixin
    ConnectionOptions connection;

    private AtlasClient client;

    protected AtlasClient client() {
        if (client == null) {
            client = connection.newClient(root().env());
        }
        return client;
    }
}
