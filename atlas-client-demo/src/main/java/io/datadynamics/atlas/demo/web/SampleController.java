package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.demo.sample.SampleScenario;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Sample metadata to try the other pages with. */
@Controller
@RequestMapping("/samples")
class SampleController {

    private final SampleScenario scenario;

    SampleController(SampleScenario scenario) {
        this.scenario = scenario;
    }

    @GetMapping
    String page(Model model) {
        model.addAttribute("entities", Actions.call(model, scenario::entities));
        return "samples";
    }

    @PostMapping("/setup")
    String setUp(Model model) {
        model.addAttribute("setup", Actions.call(model, scenario::setUp));
        return page(model);
    }

    @PostMapping("/cleanup")
    String cleanUp(Model model) {
        model.addAttribute("cleanup", Actions.call(model, scenario::cleanUp));
        return page(model);
    }
}
