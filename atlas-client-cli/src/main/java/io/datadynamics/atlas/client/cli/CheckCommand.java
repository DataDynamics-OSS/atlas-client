package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.cli.ConsistencyChecker.Finding;
import io.datadynamics.atlas.client.lineage.ProcessTypeResolver;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code atlas check} 명령입니다. Atlas가 스스로 막아 주지 않는 메타데이터 문제를 찾아 출력합니다.
 * <p>
 * 찾는 문제는 입력이나 출력이 없는 프로세스, 삭제된 엔티티를 가리키는 리니지, 같은 qualifiedName을 가진
 * 중복 엔티티입니다. 실제 검사는 {@link ConsistencyChecker}가 하며, 이 명령은 옵션에 따라 검사를 고르고
 * 결과를 한 줄에 하나씩 출력합니다. 문제가 하나라도 있으면 {@link AtlasCli#EXIT_FAILURE}(1)로 종료합니다.
 */
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

    /**
     * 선택된 검사를 실행하고 발견한 문제와 개수를 출력합니다.
     * <p>
     * {@code --skip-processes}가 없으면 프로세스 검사를 먼저 하고, 이어서 {@code --duplicates}로 지정한
     * 타입마다 중복 qualifiedName 검사를 합니다.
     *
     * @return 문제가 없으면 0, 하나라도 있으면 {@link AtlasCli#EXIT_FAILURE}
     */
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
