package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.client.cli.ConsistencyChecker;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

/**
 * 메타데이터 정합성 검사 페이지입니다. CLI의 {@code atlas check}와 같은 검사를 수행합니다.
 */
@Controller
class CheckController {

    private final ConsistencyChecker checker;

    CheckController(ConsistencyChecker checker) {
        this.checker = checker;
    }

    /**
     * {@code GET /check}: 검사 폼을 보여 주고, 파라미터가 있으면 검사를 실행해 결과를 함께 보여 줍니다.
     * <p>
     * 두 파라미터가 모두 없으면 폼만 보여 줍니다(프로세스 타입 기본값은 {@code Process}).
     * {@code processType}이 비어 있지 않으면 그 타입과 하위 타입의 프로세스 엔티티를 검사하고,
     * {@code duplicates}에 적은 타입마다 같은 qualifiedName을 가진 활성 엔티티가 있는지 검사합니다.
     *
     * @param processType 검사할 프로세스 타입 이름
     * @param duplicates  중복 qualifiedName을 검사할 타입 이름들(쉼표·공백으로 구분)
     * @param model       뷰 모델
     * @return 뷰 이름 {@code check}
     */
    @GetMapping("/check")
    String check(@RequestParam(required = false) String processType,
                 @RequestParam(required = false) String duplicates, Model model) {
        model.addAttribute("processType", processType == null ? "Process" : processType);
        model.addAttribute("duplicates", duplicates);
        if (processType == null && duplicates == null) {
            return "check";
        }
        List<ConsistencyChecker.Finding> findings = Actions.call(model, () -> {
            List<ConsistencyChecker.Finding> all = new ArrayList<>();
            if (processType != null && !processType.isBlank()) {
                all.addAll(checker.checkProcesses(processType.trim()));
            }
            for (String typeName : TypesController.names(duplicates == null ? "" : duplicates)) {
                all.addAll(checker.checkDuplicates(typeName));
            }
            return all;
        });
        model.addAttribute("findings", findings);
        return "check";
    }
}
