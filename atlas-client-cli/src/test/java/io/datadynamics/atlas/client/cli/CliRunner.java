package io.datadynamics.atlas.client.cli;

import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Runs the CLI in-process with a given environment and standard input. */
final class CliRunner {

    record Result(int exitCode, String out, String err) {
    }

    private final Map<String, String> env = new HashMap<>();
    private String stdin = "";

    CliRunner(String atlasUrl) {
        env.put("ATLAS_URL", atlasUrl);
        // Never pick up the developer's ~/.atlas/client.properties.
        env.put("ATLAS_CLIENT_CONFIG", "/nonexistent/atlas-client.properties");
    }

    CliRunner env(String name, String value) {
        env.put(name, value);
        return this;
    }

    CliRunner stdin(String stdin) {
        this.stdin = stdin;
        return this;
    }

    Result run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine commandLine = AtlasCli.commandLine(new AtlasCli(env,
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8))));
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        int exitCode = commandLine.execute(args);
        return new Result(exitCode, out.toString(), err.toString());
    }
}
