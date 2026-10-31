package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.client.cli.ConsistencyChecker;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

/** Metadata consistency checks, as {@code atlas check}. */
@Controller
class CheckController {

    private final ConsistencyChecker checker;

    CheckController(ConsistencyChecker checker) {
        this.checker = checker;
    }

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
