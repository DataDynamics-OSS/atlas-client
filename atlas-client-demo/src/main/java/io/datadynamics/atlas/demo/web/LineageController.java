package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.kafka.AtlasHookPublisher;
import io.datadynamics.atlas.client.kafka.PublishResult;
import io.datadynamics.atlas.client.lineage.AtlasLineage;
import io.datadynamics.atlas.client.lineage.EntityRef;
import io.datadynamics.atlas.client.lineage.LineageRegistration;
import io.datadynamics.atlas.client.lineage.MissingEntityPolicy;
import io.datadynamics.atlas.client.lineage.ProcessTypeResolver;
import io.datadynamics.atlas.client.lineage.ProcessBuilder;
import io.datadynamics.atlas.client.lineage.graph.LineageGraph;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo.LineageDirection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * atlas-client-lineage 모듈 데모 페이지입니다. 리니지 그래프 조회, 영향 분석, 리니지 등록을 제공합니다.
 * <p>
 * {@link AtlasLineage}는 호출마다 다른 설정(삭제된 relationship 포함 여부, 없는 엔티티 처리 방식)을 담으므로
 * 요청마다 새로 만들고, 타입 판별 결과를 캐시하는 {@link ProcessTypeResolver}는 공유합니다.
 */
@Controller
@RequestMapping("/lineage")
class LineageController {

    private final AtlasClient client;
    private final ProcessTypeResolver processTypes;
    private final ObjectProvider<AtlasHookPublisher> hook;

    LineageController(AtlasClient client, ProcessTypeResolver processTypes, ObjectProvider<AtlasHookPublisher> hook) {
        this.client = client;
        this.processTypes = processTypes;
        this.hook = hook;
    }

    /**
     * 리니지 그래프 조회 폼 입력값입니다. 기준 엔티티는 guid로, 또는 타입과 qualifiedName으로 지정합니다.
     *
     * @param guid           기준 엔티티의 guid. 있으면 타입·qualifiedName보다 우선합니다.
     * @param typeName       기준 엔티티의 타입(guid가 없을 때)
     * @param qualifiedName  기준 엔티티의 qualifiedName(guid가 없을 때)
     * @param direction      따라갈 방향(기본값 {@code BOTH})
     * @param depth          각 방향으로 따라갈 프로세스 홉 수(기본값 3)
     * @param includeDeleted soft-delete된 relationship도 그래프에 포함할지(기본값 {@code false})
     */
    public record GraphForm(String guid, String typeName, String qualifiedName, LineageDirection direction, Integer depth,
                            Boolean includeDeleted) {

        /** 비어 있는 값에 기본값을 채웁니다. */
        public GraphForm {
            direction = direction == null ? LineageDirection.BOTH : direction;
            depth = depth == null ? 3 : depth;
            includeDeleted = includeDeleted != null && includeDeleted;
        }

        /**
         * 조회할 엔티티가 지정되었는지 알려 줍니다.
         *
         * @return guid나 qualifiedName 중 하나라도 비어 있지 않으면 {@code true}
         */
        public boolean submitted() {
            return guid != null && !guid.isBlank() || qualifiedName != null && !qualifiedName.isBlank();
        }
    }

    /**
     * {@code GET /lineage}: 폼을 보여 주고, 엔티티가 지정되면 리니지 그래프를 조회해 함께 보여 줍니다.
     * <p>
     * 그래프는 Mermaid와 DOT 형식으로도 넣고, 기준 엔티티가 있으면 그 엔티티가 바뀌었을 때 영향을 받는 데이터셋
     * ({@code impacts})과 그 엔티티의 원천 데이터셋({@code sources})도 계산해 넣습니다.
     * 조회가 실패하면 같은 페이지에 실패 내용을 보여 줍니다.
     */
    @GetMapping
    String graph(@ModelAttribute("form") GraphForm form, Model model) {
        model.addAttribute("directions", LineageDirection.values());
        if (!form.submitted()) {
            return "lineage/graph";
        }
        AtlasLineage lineage = new AtlasLineage(client, processTypes).includeDeletedRelationships(form.includeDeleted());
        LineageGraph graph = Actions.call(model, () -> form.guid() != null && !form.guid().isBlank()
                ? lineage.graph(form.guid().trim(), form.direction(), form.depth())
                : lineage.graph(EntityRef.of(form.typeName().trim(), form.qualifiedName().trim()),
                form.direction(), form.depth()));
        if (graph != null) {
            model.addAttribute("graph", graph);
            model.addAttribute("mermaid", graph.toMermaid());
            model.addAttribute("dot", graph.toDot());
            if (graph.base() != null) {
                model.addAttribute("impacts", graph.impactOf(graph.base()));
                model.addAttribute("sources", graph.sourcesOf(graph.base()));
            }
        }
        return "lineage/graph";
    }

    /**
     * 리니지 등록 폼 입력값입니다. 입력·출력 엔티티는 한 줄에 하나씩 {@code type:qualifiedName}으로 쓰고,
     * 컬럼 리니지는 한 줄에 하나씩 {@code outType:outQn <- inType:inQn, inType:inQn}으로 씁니다.
     * 빈 줄과 {@code #}으로 시작하는 줄은 무시합니다.
     *
     * @param processType     프로세스 타입 이름(필수)
     * @param qualifiedName   프로세스의 qualifiedName(필수)
     * @param name            프로세스 이름(선택)
     * @param description     프로세스 설명(선택)
     * @param inputs          입력 엔티티 목록
     * @param outputs         출력 엔티티 목록
     * @param columnLineage   컬럼 리니지 목록(선택)
     * @param missingEntities 참조한 엔티티가 Atlas에 없을 때의 처리 방식(기본값 {@code FAIL})
     * @param via             {@code rest}(bulk 요청 한 번, 기본값) 또는 {@code kafka}({@code ATLAS_HOOK} 메시지)
     */
    public record RegisterForm(String processType, String qualifiedName, String name, String description, String inputs,
                        String outputs, String columnLineage, MissingEntityPolicy missingEntities, String via) {

        /** 비어 있는 값에 기본값을 채웁니다. */
        public RegisterForm {
            missingEntities = missingEntities == null ? MissingEntityPolicy.FAIL : missingEntities;
            via = via == null ? "rest" : via;
        }

        /**
         * 등록 폼에 미리 채울 예제를 만듭니다. 없는 엔티티는 만들도록({@code CREATE}) 해 두어 바로 시험할 수 있습니다.
         *
         * @return 예제 입력값
         */
        public static RegisterForm sample() {
            return new RegisterForm("Process", "console.sample_etl@demo", "sample_etl", null,
                    "DataSet:console.sample_a@demo", "DataSet:console.sample_b@demo", null,
                    MissingEntityPolicy.CREATE, "rest");
        }
    }

    /** {@code GET /lineage/register}: 예제를 채운 등록 폼을 보여 줍니다. */
    @GetMapping("/register")
    String registerForm(Model model) {
        model.addAttribute("form", RegisterForm.sample());
        model.addAttribute("policies", MissingEntityPolicy.values());
        return "lineage/register";
    }

    /**
     * {@code POST /lineage/register}: 폼 입력으로 프로세스를 만들어 리니지를 등록합니다.
     * <p>
     * {@code via=kafka}이면 같은 엔티티를 hook 메시지로 {@code ATLAS_HOOK}에 보내고 보낸 내용(payload)을 보여 주며,
     * Kafka가 설정되지 않았으면 실패로 보여 줍니다. 그 밖에는 REST로 등록하고 등록 결과를 보여 줍니다.
     * 입력이 잘못되었거나 등록이 실패하면 같은 페이지에 실패 내용을 보여 줍니다.
     */
    @PostMapping("/register")
    String register(@ModelAttribute("form") RegisterForm form, Model model) {
        model.addAttribute("policies", MissingEntityPolicy.values());
        AtlasLineage lineage = new AtlasLineage(client, processTypes).missingEntities(form.missingEntities());
        ProcessBuilder process = Actions.call(model, () -> build(lineage, form));
        if (process == null) {
            return "lineage/register";
        }
        if ("kafka".equals(form.via())) {
            AtlasHookPublisher publisher = hook.getIfAvailable();
            if (publisher == null) {
                model.addAttribute(Actions.FAILURE, Failure.of(new IllegalStateException("Kafka가 설정되지 않았습니다.")));
                return "lineage/register";
            }
            PublishResult result = Actions.call(model, () -> publisher.createOrUpdate(process.toEntities()));
            model.addAttribute("published", result);
            if (result != null) {
                model.addAttribute("payload", AtlasJson.toPrettyJson(process.toEntities()));
            }
        } else {
            LineageRegistration registration = Actions.call(model, process::register);
            model.addAttribute("registration", registration);
        }
        return "lineage/register";
    }

    /**
     * 폼 입력을 검사하고 {@link ProcessBuilder}를 만듭니다. 아직 Atlas에 아무것도 보내지 않습니다.
     *
     * @throws IllegalArgumentException 필수 값이 없거나 참조·컬럼 리니지 형식이 잘못된 경우
     */
    private static ProcessBuilder build(AtlasLineage lineage, RegisterForm form) {
        require(form.processType(), "process type");
        require(form.qualifiedName(), "qualifiedName");
        ProcessBuilder process = lineage.process(form.processType().trim(), form.qualifiedName().trim())
                .inputs(refs(form.inputs()).toArray(EntityRef[]::new))
                .outputs(refs(form.outputs()).toArray(EntityRef[]::new));
        if (form.name() != null && !form.name().isBlank()) {
            process.name(form.name().trim());
        }
        if (form.description() != null && !form.description().isBlank()) {
            process.description(form.description().trim());
        }
        for (String line : lines(form.columnLineage())) {
            String[] parts = line.split("<-", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Column lineage must look like 'outType:outQn <- inType:inQn, ...': " + line);
            }
            // 쉼표로 구분된 입력 컬럼을 줄바꿈으로 바꿔 refs()의 한 줄에 하나 규칙을 그대로 씁니다.
            process.columnLineage(ref(parts[0]), refs(parts[1].replace(',', '\n')).toArray(EntityRef[]::new));
        }
        return process;
    }

    /**
     * 한 줄에 하나씩 쓴 {@code type:qualifiedName} 목록을 참조 목록으로 바꿉니다.
     * 빈 줄과 {@code #}으로 시작하는 줄은 건너뜁니다.
     */
    static List<EntityRef> refs(String text) {
        List<EntityRef> refs = new ArrayList<>();
        for (String line : lines(text)) {
            refs.add(ref(line));
        }
        return refs;
    }

    /**
     * {@code type:qualifiedName} 하나를 참조로 바꿉니다.
     * 타입 이름에는 {@code :}이 들어가지 않지만 qualifiedName에는 들어갈 수 있으므로(예: {@code hdfs://...})
     * 첫 번째 {@code :}을 기준으로 나눕니다.
     *
     * @throws IllegalArgumentException 타입이나 qualifiedName 부분이 비어 있는 경우
     */
    static EntityRef ref(String text) {
        String value = text.trim();
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) {
            throw new IllegalArgumentException("Expected type:qualifiedName but got '" + value + "'");
        }
        return EntityRef.of(value.substring(0, colon).trim(), value.substring(colon + 1).trim());
    }

    /** 텍스트를 줄로 나누고 앞뒤 공백을 지운 뒤, 빈 줄과 {@code #} 주석 줄을 뺍니다. */
    private static List<String> lines(String text) {
        if (text == null) {
            return List.of();
        }
        return text.lines().map(String::trim).filter(s -> !s.isEmpty() && !s.startsWith("#")).toList();
    }

    /** 값이 비어 있으면 {@code what}을 이름으로 한 {@link IllegalArgumentException}을 던집니다. */
    private static void require(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " is required");
        }
    }
}
