package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityWithExtInfo;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;

@Command(name = "entities", description = "Imports and reads entities.",
        subcommands = {EntitiesCommand.Import.class, EntitiesCommand.Get.class})
final class EntitiesCommand {

    @Command(name = "import", description = {
            "Creates or updates entities from a CSV, YAML or JSON file, matched by qualifiedName.",
            "CSV headers: 'attr', 'attr[]' (values separated by |), 'attr@refType' (reference by qualifiedName), "
                    + "'attr[]@refType', and the special columns typeName, classifications, labels."})
    static final class Import extends ClientCommand {

        @Parameters(index = "0", paramLabel = "FILE", description = "Entities as .csv, .yaml, .yml or .json")
        Path file;

        @Option(names = {"-t", "--type"}, description = "Entity type of rows without a typeName")
        String typeName;

        @Option(names = "--batch-size", defaultValue = "100",
                description = "Entities per Atlas request (default: ${DEFAULT-VALUE})")
        int batchSize;

        @Option(names = "--dry-run", description = "Only read and validate the file")
        boolean dryRun;

        @Override
        public Integer call() {
            if (batchSize < 1) {
                throw new IllegalArgumentException("--batch-size must be positive");
            }
            List<AtlasEntity> entities = EntityFiles.read(file, typeName);
            out().printf("Read %d entities from %s%n", entities.size(), file);
            if (dryRun) {
                if (!entities.isEmpty()) {
                    out().println("First entity:");
                    out().println(AtlasJson.toPrettyJson(entities.get(0)));
                }
                return 0;
            }
            int batches = (entities.size() + batchSize - 1) / batchSize;
            int created = 0;
            int updated = 0;
            for (int b = 0; b < batches; b++) {
                List<AtlasEntity> batch = EntityFiles.linkWithinBatch(
                        entities.subList(b * batchSize, Math.min((b + 1) * batchSize, entities.size())));
                EntityMutationResponse response = client().entities().createOrUpdate(batch);
                created += response.created().size();
                updated += response.updated().size();
                if (batches > 1) {
                    out().printf("Batch %d/%d: %d entities (%d created, %d updated)%n", b + 1, batches, batch.size(),
                            response.created().size(), response.updated().size());
                }
            }
            out().printf("Imported %d entities: %d created, %d updated%n", entities.size(), created, updated);
            return 0;
        }
    }

    @Command(name = "get", description = "Prints an entity as JSON.")
    static final class Get extends ClientCommand {

        @Parameters(index = "0", paramLabel = "TYPE")
        String typeName;

        @Parameters(index = "1", paramLabel = "QUALIFIED_NAME")
        String qualifiedName;

        @Option(names = "--with-referred", description = "Include referred entities (columns, ...)")
        boolean withReferred;

        @Override
        public Integer call() {
            AtlasEntityWithExtInfo entity = client().entities().getByQualifiedName(typeName, qualifiedName);
            out().println(AtlasJson.toPrettyJson(withReferred ? entity : entity.getEntity()));
            return 0;
        }
    }
}
