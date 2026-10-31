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

/**
 * 첫 화면(대시보드)과 Atlas 요청 이력 페이지입니다.
 */
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

    /**
     * {@code GET /}: 연결 설정, Kafka 리스너 상태, Atlas 서버 상태를 보여 줍니다.
     * <p>
     * 먼저 HA 상태({@code /api/atlas/admin/status})를 조회하고, 성공했을 때만 버전과 타입 헤더를 조회해
     * 카테고리별 타입 수를 셉니다. 접속에 실패해도 오류 페이지로 가지 않고 대시보드에 실패 내용을 보여 줍니다.
     */
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

    /** {@code GET /requests}: 데모가 지금까지 Atlas에 보낸 요청 이력(최근 것부터)을 보여 줍니다. */
    @GetMapping("/requests")
    String requests(Model model) {
        model.addAttribute("history", trace.history());
        return "requests";
    }

    /** {@code POST /requests/clear}: 요청 이력을 비우고 이력 페이지로 다시 이동합니다(redirect). */
    @PostMapping("/requests/clear")
    String clearRequests() {
        trace.clearHistory();
        return "redirect:/requests";
    }
}
