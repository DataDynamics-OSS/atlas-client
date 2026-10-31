package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.api.AdminApi;
import io.datadynamics.atlas.client.model.typedef.AtlasTypeDefHeader;
import io.datadynamics.atlas.client.model.typedef.TypeCategory;
import io.datadynamics.atlas.demo.config.DemoProperties;
import io.datadynamics.atlas.demo.events.EntityEventHub;
import io.datadynamics.atlas.demo.trace.RequestTrace;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Controller
class DashboardController {

    private final AtlasClient client;
    private final DemoProperties properties;
    private final ObjectProvider<EntityEventHub> eventHub;
    private final RequestTrace trace;

    DashboardController(AtlasClient client, DemoProperties properties, ObjectProvider<EntityEventHub> eventHub,
                        RequestTrace trace) {
        this.client = client;
        this.properties = properties;
        this.eventHub = eventHub;
        this.trace = trace;
    }

    @GetMapping("/")
    String dashboard(Model model) {
        model.addAttribute("serverUrls", client.config().getServerUrls());
        model.addAttribute("username", properties.username());
        model.addAttribute("kafka", properties.kafka());
        model.addAttribute("listener", eventHub.getIfAvailable());
        String status = Actions.call(model, () -> client.admin().status());
        model.addAttribute("status", status);
        if (status != null) {
            AdminApi.AtlasVersion version = Actions.call(model, () -> client.admin().version());
            model.addAttribute("version", version);
            List<AtlasTypeDefHeader> headers = Actions.call(model, () -> client.types().getHeaders());
            if (headers != null) {
                Map<TypeCategory, Long> counts = headers.stream().collect(Collectors.groupingBy(
                        AtlasTypeDefHeader::getCategory, TreeMap::new, Collectors.counting()));
                model.addAttribute("typeCounts", counts);
            }
        }
        return "dashboard";
    }

    @GetMapping("/requests")
    String requests(Model model) {
        model.addAttribute("history", trace.history());
        return "requests";
    }

    @PostMapping("/requests/clear")
    String clearRequests() {
        trace.clearHistory();
        return "redirect:/requests";
    }
}
