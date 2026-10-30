package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.typedef.file.TypeDefFiles;
import io.datadynamics.atlas.client.typedef.plan.TypeApplyResult;
import io.datadynamics.atlas.client.typedef.plan.TypeDefManager;
import io.datadynamics.atlas.client.typedef.plan.TypePlan;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Command(name = "types", description = "Plans, applies, exports and deletes type definitions.",
        subcommands = {TypesCommand.Plan.class, TypesCommand.Apply.class, TypesCommand.Export.class,
                TypesCommand.Delete.class})
final class TypesCommand {

    /** Reads and merges type definition files and directories. */
    static AtlasTypesDef read(List<Path> paths) {
        AtlasTypesDef merged = new AtlasTypesDef();
        for (Path path : paths) {
            AtlasTypesDef typesDef = Files.isDirectory(path) ? TypeDefFiles.readAll(path) : TypeDefFiles.read(path);
            typesDef.stream().forEach(merged::add);
        }
        return merged;
    }

    @Command(name = "plan", description = "Shows what 'apply' would change, without changing anything. "
            + "Exits with 1 if the plan has errors.")
    static final class Plan extends ClientCommand {

        @Parameters(arity = "1..*", paramLabel = "PATH", description = "YAML/JSON files or directories")
        List<Path> paths;

        @Override
        public Integer call() {
            TypePlan plan = new TypeDefManager(client()).plan(read(paths));
            out().print(plan.render());
            return plan.hasErrors() ? AtlasCli.EXIT_FAILURE : 0;
        }
    }

    @Command(name = "apply", description = "Creates and updates types so Atlas matches the definition files.")
    static final class Apply extends ClientCommand {

        @Parameters(arity = "1..*", paramLabel = "PATH", description = "YAML/JSON files or directories")
        List<Path> paths;

        @Option(names = {"-y", "--yes"}, description = "Apply without asking for confirmation")
        boolean yes;

        @Override
        public Integer call() {
            TypeDefManager manager = new TypeDefManager(client());
            TypePlan plan = manager.plan(read(paths));
            out().print(plan.render());
            if (plan.hasErrors()) {
                err().println("The plan has errors; nothing was applied.");
                return AtlasCli.EXIT_FAILURE;
            }
            if (plan.isEmpty()) {
                out().println("No changes.");
                return 0;
            }
            if (!yes && !confirm("Apply these changes?")) {
                out().println("Apply cancelled.");
                return AtlasCli.EXIT_FAILURE;
            }
            TypeApplyResult result = manager.apply(plan);
            out().println("Applied: " + result);
            return 0;
        }
    }

    @Command(name = "export", description = "Writes type definitions from Atlas as YAML or JSON, "
            + "ready to be edited and applied again.")
    static final class Export extends ClientCommand {

        @Parameters(arity = "0..*", paramLabel = "TYPE", description = "Type names to export")
        List<String> names = new ArrayList<>();

        @Option(names = "--service-type", description = "Export all types of this service type, e.g. hive")
        String serviceType;

        @Option(names = {"-o", "--output"}, paramLabel = "FILE",
                description = "Output file; .yaml/.yml or .json. Default: standard output")
        Path output;

        @Option(names = "--format", description = "Format for standard output: ${COMPLETION-CANDIDATES} (default: yaml)")
        Format format = Format.yaml;

        enum Format { yaml, json }

        @Override
        public Integer call() {
            if (names.isEmpty() && serviceType == null) {
                throw new ParameterException(spec.commandLine(), "Give type names or --service-type");
            }
            TypeDefManager manager = new TypeDefManager(client());
            AtlasTypesDef typesDef = new AtlasTypesDef();
            if (serviceType != null) {
                manager.exportByServiceType(serviceType).stream().forEach(typesDef::add);
            }
            if (!names.isEmpty()) {
                manager.export(names).stream().forEach(typesDef::add);
            }
            if (output == null) {
                out().print(format == Format.json ? TypeDefFiles.toJson(typesDef) : TypeDefFiles.toYaml(typesDef));
                out().flush();
            } else {
                TypeDefFiles.write(typesDef, output);
                out().printf("Exported %d types to %s%n", typesDef.stream().count(), output);
            }
            return 0;
        }
    }

    @Command(name = "delete", description = "Deletes types after checking that nothing uses them.")
    static final class Delete extends ClientCommand {

        @Parameters(arity = "1..*", paramLabel = "TYPE", description = "Type names to delete")
        List<String> names;

        @Option(names = {"-y", "--yes"}, description = "Delete without asking for confirmation")
        boolean yes;

        @Override
        public Integer call() {
            TypeDefManager manager = new TypeDefManager(client());
            TypePlan plan = manager.planDelete(names);
            out().print(plan.render());
            if (plan.hasErrors()) {
                err().println("The types are still in use; nothing was deleted.");
                return AtlasCli.EXIT_FAILURE;
            }
            if (plan.isEmpty()) {
                out().println("Nothing to delete.");
                return 0;
            }
            if (!yes && !confirm("Delete these types?")) {
                out().println("Delete cancelled.");
                return AtlasCli.EXIT_FAILURE;
            }
            out().println("Deleted: " + manager.delete(names));
            return 0;
        }
    }
}
