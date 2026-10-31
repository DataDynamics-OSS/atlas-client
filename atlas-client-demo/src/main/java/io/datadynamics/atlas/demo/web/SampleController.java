package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.demo.sample.SampleScenario;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 다른 페이지를 시험해 볼 수 있도록 샘플 메타데이터를 만들고 지우는 페이지입니다.
 * 실제 작업은 {@link SampleScenario}가 합니다.
 */
@Controller
@RequestMapping("/samples")
class SampleController {

    private final SampleScenario scenario;

    SampleController(SampleScenario scenario) {
        this.scenario = scenario;
    }

    /** {@code GET /samples}: Atlas에 있는 샘플 엔티티 목록(삭제된 것 포함)을 보여 줍니다. */
    @GetMapping
    String page(Model model) {
        model.addAttribute("entities", Actions.call(model, scenario::entities));
        return "samples";
    }

    /** {@code POST /samples/setup}: 샘플 타입과 엔티티를 만들거나 갱신하고, 결과와 함께 샘플 페이지를 다시 보여 줍니다. */
    @PostMapping("/setup")
    String setUp(Model model) {
        model.addAttribute("setup", Actions.call(model, scenario::setUp));
        return page(model);
    }

    /** {@code POST /samples/cleanup}: 샘플 엔티티를 삭제·purge하고 샘플 타입도 지운 뒤, 결과와 함께 샘플 페이지를 다시 보여 줍니다. */
    @PostMapping("/cleanup")
    String cleanUp(Model model) {
        model.addAttribute("cleanup", Actions.call(model, scenario::cleanUp));
        return page(model);
    }
}
