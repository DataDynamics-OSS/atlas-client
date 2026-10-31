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

/**
 * {@code atlas types} 명령 그룹입니다. 타입 정의를 plan, apply, export, delete 하는 하위 명령을 묶습니다.
 * <p>
 * 실제 비교와 반영은 {@link TypeDefManager}가 하며, 이 클래스의 하위 명령은 파일을 읽고 결과를 출력하는
 * 역할만 합니다.
 */
@Command(name = "types", description = "Plans, applies, exports and deletes type definitions.",
        subcommands = {TypesCommand.Plan.class, TypesCommand.Apply.class, TypesCommand.Export.class,
                TypesCommand.Delete.class})
final class TypesCommand {

    /**
     * 타입 정의 파일과 디렉터리를 읽어 하나의 {@link AtlasTypesDef}로 합칩니다.
     * 디렉터리는 {@link TypeDefFiles#readAll(Path)}로, 파일은 {@link TypeDefFiles#read(Path)}로 읽습니다.
     *
     * @param paths 명령줄에 지정한 YAML/JSON 파일 또는 디렉터리
     * @return 모든 정의를 합친 결과
     */
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
    /**
     * {@code atlas types plan}: 정의 파일과 Atlas의 현재 타입을 비교해, apply가 무엇을 바꿀지 출력만 합니다.
     * Atlas는 바꾸지 않습니다. plan에 오류가 있으면 {@link AtlasCli#EXIT_FAILURE}로 끝납니다.
     */
    static final class Plan extends ClientCommand {

        @Parameters(arity = "1..*", paramLabel = "PATH", description = "YAML/JSON files or directories")
        List<Path> paths;

        /**
         * plan을 계산해 출력합니다.
         *
         * @return plan에 오류가 없으면 0, 있으면 {@link AtlasCli#EXIT_FAILURE}
         */
        @Override
        public Integer call() {
            // 변경 없이 비교 결과만 출력합니다.
            TypePlan plan = new TypeDefManager(client()).plan(read(paths));
            out().print(plan.render());
            return plan.hasErrors() ? AtlasCli.EXIT_FAILURE : 0;
        }
    }

    @Command(name = "apply", description = "Creates and updates types so Atlas matches the definition files.")
    /**
     * {@code atlas types apply}: Atlas의 타입이 정의 파일과 같아지도록 타입을 생성·수정합니다.
     * <p>
     * 먼저 plan을 출력하고, 오류가 있으면 아무것도 반영하지 않고 끝냅니다. 바뀔 것이 없으면 그대로 성공합니다.
     * {@code --yes}가 없으면 반영 전에 확인을 받으며, 거절하면 {@link AtlasCli#EXIT_FAILURE}로 끝납니다.
     */
    static final class Apply extends ClientCommand {

        @Parameters(arity = "1..*", paramLabel = "PATH", description = "YAML/JSON files or directories")
        List<Path> paths;

        @Option(names = {"-y", "--yes"}, description = "Apply without asking for confirmation")
        boolean yes;

        /**
         * plan을 출력하고, 확인을 받은 뒤 반영합니다.
         *
         * @return 반영했거나 바뀔 것이 없으면 0, plan 오류나 사용자 거절이면 {@link AtlasCli#EXIT_FAILURE}
         */
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
            // 사용자가 본 것과 같은 plan을 반영합니다(다시 계산하지 않습니다).
            TypeApplyResult result = manager.apply(plan);
            out().println("Applied: " + result);
            return 0;
        }
    }

    @Command(name = "export", description = "Writes type definitions from Atlas as YAML or JSON, "
            + "ready to be edited and applied again.")
    /**
     * {@code atlas types export}: Atlas에 저장된 타입 정의를 YAML 또는 JSON으로 내보냅니다.
     * <p>
     * 서버가 관리하는 필드는 빠지므로, 결과를 고친 뒤 그대로 {@code types apply}에 다시 쓸 수 있습니다.
     * 타입 이름과 {@code --service-type} 중 적어도 하나가 필요하며, 둘 다 주면 결과를 합칩니다.
     * {@code -o}로 파일을 지정하면 형식은 파일 확장자로 정해지고, 지정하지 않으면 {@code --format}에 따라
     * 표준 출력에 씁니다.
     */
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

        /** 표준 출력에 쓸 형식입니다. 값 이름이 그대로 명령줄 값({@code yaml}, {@code json})이 됩니다. */
        enum Format { yaml, json }

        /**
         * 지정한 타입을 조회해 파일 또는 표준 출력에 씁니다.
         *
         * @return 항상 0. 실패는 예외로 알립니다
         * @throws picocli.CommandLine.ParameterException 타입 이름과 {@code --service-type}이 모두 없는 경우
         */
        @Override
        public Integer call() {
            // 내보낼 대상이 없으면 사용법 오류로 처리합니다(종료 코드 2).
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
    /**
     * {@code atlas types delete}: 타입을 쓰는 곳이 없는지 확인한 뒤 삭제합니다.
     * <p>
     * 하위 타입, 다른 타입의 참조, 남아 있는 엔티티 등 Atlas가 삭제를 거부할 이유가 있으면 plan에 오류로 나오고
     * 아무것도 삭제하지 않습니다. {@code --yes}가 없으면 삭제 전에 확인을 받습니다.
     */
    static final class Delete extends ClientCommand {

        @Parameters(arity = "1..*", paramLabel = "TYPE", description = "Type names to delete")
        List<String> names;

        @Option(names = {"-y", "--yes"}, description = "Delete without asking for confirmation")
        boolean yes;

        /**
         * 삭제 plan을 출력하고, 확인을 받은 뒤 타입을 삭제합니다.
         *
         * @return 삭제했거나 삭제할 것이 없으면 0, 타입이 아직 쓰이고 있거나 사용자가 거절하면
         *         {@link AtlasCli#EXIT_FAILURE}
         */
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
            // delete()는 삭제 plan을 다시 세운 뒤 반영합니다. 확인을 기다리는 동안 상태가 바뀌었다면 그 시점의
            // 상태를 기준으로 다시 검사합니다.
            out().println("Deleted: " + manager.delete(names));
            return 0;
        }
    }
}
