package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.cli.ConsistencyChecker.Finding;
import io.datadynamics.atlas.client.lineage.ProcessTypeResolver;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;

@Command(name = "check", description = {
        "Finds metadata problems: processes without inputs or outputs, lineage pointing at deleted entities, "
                + "and duplicate qualifiedNames.",
        "Exits with 1 if anything was found."})
final class CheckCommand extends ClientCommand {

    @Option(names = "--process-type", defaultValue = ProcessTypeResolver.PROCESS,
            description = "Process type to check, with its sub-types (default: ${DEFAULT-VALUE})")
    String processType;

    @Option(names = "--skip-processes", description = "Do not check processes")
    boolean skipProcesses;

    @Option(names = "--duplicates", paramLabel = "TYPE",
            description = "Also look for duplicate qualifiedNames among entities of this type (repeatable)")
    List<String> duplicateTypes = new ArrayList<>();

    @Override
    public Integer call() {
        ConsistencyChecker checker = new ConsistencyChecker(client());
        List<Finding> findings = new ArrayList<>();
        if (!skipProcesses) {
            findings.addAll(checker.checkProcesses(processType));
        }
        for (String type : duplicateTypes) {
            findings.addAll(checker.checkDuplicates(type));
        }
        findings.forEach(out()::println);
        out().println(findings.isEmpty() ? "No problems found." : findings.size() + " problems found.");
        return findings.isEmpty() ? 0 : AtlasCli.EXIT_FAILURE;
    }
}
