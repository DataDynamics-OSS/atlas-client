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

/**
 * {@code atlas entities} 명령 그룹입니다. 파일에서 엔티티를 가져오는 import와 엔티티를 조회하는 get을 묶습니다.
 */
@Command(name = "entities", description = "Imports and reads entities.",
        subcommands = {EntitiesCommand.Import.class, EntitiesCommand.Get.class})
final class EntitiesCommand {

    @Command(name = "import", description = {
            "Creates or updates entities from a CSV, YAML or JSON file, matched by qualifiedName.",
            "CSV headers: 'attr', 'attr[]' (values separated by |), 'attr@refType' (reference by qualifiedName), "
                    + "'attr[]@refType', and the special columns typeName, classifications, labels."})
    /**
     * {@code atlas entities import}: CSV, YAML, JSON 파일의 엔티티를 qualifiedName 기준으로 생성하거나 수정합니다.
     * <p>
     * 파일 형식은 {@link EntityFiles}가 설명합니다. 엔티티는 {@code --batch-size}개씩 나눠
     * {@code createOrUpdate} 요청 하나로 보내며, 배치 하나는 Atlas에서 한 트랜잭션으로 처리됩니다.
     * 같은 배치 안의 엔티티끼리는 qualifiedName 참조가 placeholder guid로 연결됩니다
     * ({@link EntityFiles#linkWithinBatch(List)}).
     * 배치 사이에는 트랜잭션이 이어지지 않으므로, 중간 배치가 실패하면 앞의 배치는 이미 반영된 상태로 남습니다.
     * <p>
     * {@code --dry-run}이면 파일을 읽고 검증만 하며 Atlas에 접속하지 않습니다.
     */
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

        /**
         * 파일을 읽어 검증한 뒤 배치 단위로 Atlas에 보내고, 생성·수정된 개수를 출력합니다.
         *
         * @return 항상 0. 실패는 예외로 알립니다
         * @throws IllegalArgumentException {@code --batch-size}가 1보다 작거나 파일 내용이 잘못된 경우
         */
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
            // 올림 나눗셈으로 배치 수를 구합니다.
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
    /**
     * {@code atlas entities get}: typeName과 qualifiedName으로 엔티티 하나를 조회해 JSON으로 출력합니다.
     * <p>
     * 기본으로는 엔티티 본문만 출력하고, {@code --with-referred}를 주면 참조된 엔티티(컬럼 등)를 담은
     * {@code referredEntities}까지 포함한 응답 전체를 출력합니다.
     */
    static final class Get extends ClientCommand {

        @Parameters(index = "0", paramLabel = "TYPE")
        String typeName;

        @Parameters(index = "1", paramLabel = "QUALIFIED_NAME")
        String qualifiedName;

        @Option(names = "--with-referred", description = "Include referred entities (columns, ...)")
        boolean withReferred;

        /**
         * 엔티티를 조회해 보기 좋게 들여쓴 JSON으로 출력합니다.
         *
         * @return 항상 0. 엔티티가 없으면 클라이언트가 던지는 예외로 실패합니다
         */
        @Override
        public Integer call() {
            AtlasEntityWithExtInfo entity = client().entities().getByQualifiedName(typeName, qualifiedName);
            out().println(AtlasJson.toPrettyJson(withReferred ? entity : entity.getEntity()));
            return 0;
        }
    }
}
