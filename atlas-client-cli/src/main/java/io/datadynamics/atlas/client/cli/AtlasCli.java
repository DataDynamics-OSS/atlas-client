package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.exception.AtlasClientException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Entry point of the {@code atlas} command line tool.
 * <pre>
 * atlas types plan ./types --url http://atlas:21000 -u admin
 * atlas types apply ./types --yes
 * atlas entities import tables.csv --type hive_table
 * atlas lineage impact hive_table raw.sales@prod
 * atlas check --duplicates hive_table
 * atlas events tail --bootstrap-servers kafka:9092
 * </pre>
 * Connection settings come from options, then {@code ATLAS_*} environment variables, then
 * {@code ~/.atlas/client.properties} (see {@link ConnectionOptions}).
 */
@Command(name = "atlas", mixinStandardHelpOptions = true, versionProvider = AtlasCli.VersionProvider.class,
        description = "Command line tools for Apache Atlas.",
        subcommands = {StatusCommand.class, TypesCommand.class, EntitiesCommand.class, LineageCommand.class,
                CheckCommand.class, EventsCommand.class, HelpCommand.class})
public final class AtlasCli implements Runnable {

    /** Exit code when the command ran but found problems: plan errors, consistency issues, a declined prompt. */
    public static final int EXIT_FAILURE = 1;
    /** Exit code for invalid command lines. */
    public static final int EXIT_USAGE = 2;

    private final Map<String, String> env;
    private final BufferedReader in;

    @Spec
    CommandSpec spec;

    public AtlasCli() {
        this(System.getenv(), System.in);
    }

    AtlasCli(Map<String, String> env, InputStream in) {
        this.env = env;
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    public static void main(String[] args) {
        System.exit(commandLine(new AtlasCli()).execute(args));
    }

    /** The command line {@link #main(String[])} runs, for embedding the tool; redirect output with {@code setOut}/{@code setErr}. */
    public static CommandLine commandLine(AtlasCli cli) {
        CommandLine commandLine = new CommandLine(cli);
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.setExecutionExceptionHandler((error, cl, parseResult) -> {
            PrintWriter err = cl.getErr();
            if (error instanceof AtlasClientException || error instanceof IllegalArgumentException
                    || error instanceof IllegalStateException || error instanceof UncheckedIOException) {
                err.println("Error: " + error.getMessage());
            } else {
                err.println("Error: " + error);
            }
            if (cli.env.containsKey("ATLAS_CLI_DEBUG")) {
                error.printStackTrace(err);
            }
            err.flush();
            return EXIT_FAILURE;
        });
        return commandLine;
    }

    @Override
    public void run() {
        throw new CommandLine.ParameterException(spec.commandLine(), "Missing command");
    }

    Map<String, String> env() {
        return env;
    }

    /** One line from standard input, or {@code null} at end of input. */
    String readLine() {
        try {
            return in.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            String version = AtlasCli.class.getPackage().getImplementationVersion();
            return new String[]{"atlas-client-cli " + (version == null ? "(development)" : version)};
        }
    }
}
