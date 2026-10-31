package io.datadynamics.atlas.client.it;

import io.datadynamics.atlas.client.lineage.AtlasLineage;
import io.datadynamics.atlas.client.lineage.EntityRef;
import io.datadynamics.atlas.client.lineage.LineageException;
import io.datadynamics.atlas.client.lineage.LineageRegistration;
import io.datadynamics.atlas.client.lineage.MissingEntityPolicy;
import io.datadynamics.atlas.client.lineage.graph.Impact;
import io.datadynamics.atlas.client.lineage.graph.LineageGraph;
import io.datadynamics.atlas.client.lineage.graph.LineageNode;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;
import io.datadynamics.atlas.client.typedef.plan.TypeDefManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.datadynamics.atlas.client.typedef.TypeDefs.attr;
import static io.datadynamics.atlas.client.typedef.TypeDefs.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 Atlas를 상대로 리니지 등록과 탐색을 검증합니다. 직접 정의한 데이터셋, 프로세스, 컬럼 리니지 타입을 씁니다.
 */
class LineageIT extends AtlasIT {

    /** 테스트용 타입 이름입니다. */
    private static String table;
    private static String column;
    private static String job;
    private static String columnLineage;
    /** 컬럼 리니지 타입이 설정된 리니지 도우미입니다. */
    private static AtlasLineage lineage;

    /**
     * 테이블·컬럼 데이터셋 타입, 프로세스 타입, 그리고 {@code query} 속성으로 상위 프로세스를 가리키는 컬럼 리니지
     * 프로세스 타입을 만들고, 이 컬럼 리니지 타입을 쓰도록 {@link AtlasLineage}를 설정합니다.
     */
    @BeforeAll
    static void createTypes() {
        table = name("table");
        column = name("column");
        job = name("job");
        columnLineage = name("column_lineage");
        cleanupTypes(columnLineage, job, column, table);
        new TypeDefManager(atlas).sync(
                entity(table).dataSet(),
                entity(column).dataSet(),
                entity(job).process(),
                entity(columnLineage).process().attribute(attr("query", job)));
        lineage = new AtlasLineage(atlas).columnLineageType(columnLineage, "query");
    }

    /** {@code <접두어><suffix>@it} qualifiedName을 가진 테이블 참조를 만듭니다. */
    private static EntityRef table(String suffix) {
        return EntityRef.of(table, name(suffix) + "@it");
    }

    /** {@code <접두어><suffix>@it} qualifiedName을 가진 컬럼 참조를 만듭니다. */
    private static EntityRef column(String suffix) {
        return EntityRef.of(column, name(suffix) + "@it");
    }

    /** 참조한 데이터셋 엔티티를 한 번의 요청으로 만들고, 새로 생성된 것을 정리 대상으로 등록합니다. */
    private static void createDatasets(EntityRef... refs) {
        List<AtlasEntity> entities = java.util.Arrays.stream(refs)
                .map(r -> new AtlasEntity(r.typeName(), Map.of("qualifiedName", r.qualifiedName(), "name", r.defaultName())))
                .collect(Collectors.toList());
        atlas.entities().createOrUpdate(entities).created().forEach(h -> cleanupEntity(h.getGuid()));
    }

    /** 노드 목록에서 qualifiedName만 뽑아 돌려줍니다. */
    private static List<String> qualifiedNames(List<LineageNode> nodes) {
        return nodes.stream().map(LineageNode::qualifiedName).collect(Collectors.toList());
    }

    /**
     * sales, customer → [agg 프로세스] → daily → [report 프로세스] → report 리니지를 등록한 뒤, daily를 중심으로 한 그래프에서
     * 상위 데이터셋 2개, 하위 데이터셋 1개, 프로세스 2개가 보여야 합니다. sales의 영향도는 daily(1홉), report(2홉)
     * 순서여야 합니다.
     */
    @Test
    void registeredLineageIsVisibleAndImpactIsComputed() {
        EntityRef sales = table("sales");
        EntityRef customer = table("customer");
        EntityRef daily = table("daily");
        EntityRef report = table("report");
        createDatasets(sales, customer, daily, report);

        LineageRegistration first = lineage.process(job, name("agg") + "@it")
                .inputs(sales, customer).outputs(daily).register();
        LineageRegistration second = lineage.process(job, name("report") + "@it")
                .inputs(daily).outputs(report).register();
        first.guids().values().forEach(AtlasIT::cleanupEntity);
        second.guids().values().forEach(AtlasIT::cleanupEntity);

        LineageGraph graph = lineage.graph(daily);
        assertThat(graph.base().qualifiedName()).isEqualTo(daily.qualifiedName());
        assertThat(qualifiedNames(graph.upstreamDatasets(graph.base())))
                .containsExactlyInAnyOrder(sales.qualifiedName(), customer.qualifiedName());
        assertThat(qualifiedNames(graph.downstreamDatasets(graph.base()))).containsExactly(report.qualifiedName());
        assertThat(graph.processes()).allMatch(LineageNode::process).hasSize(2);

        List<Impact> impact = lineage.impact(sales, 5);
        assertThat(impact).extracting(i -> i.dataset().qualifiedName() + "@" + i.distance())
                .containsExactly(daily.qualifiedName() + "@1", report.qualifiedName() + "@2");
    }

    /**
     * 같은 프로세스를 입력만 바꿔 다시 등록하면 새 프로세스가 생기지 않고 기존 guid가 유지되어야 하며,
     * 리니지의 입력은 새 입력(b)으로 바뀌어야 합니다.
     */
    @Test
    void reRegisteringReplacesInputsAndKeepsTheProcess() {
        EntityRef a = table("re_a");
        EntityRef b = table("re_b");
        EntityRef out = table("re_out");
        createDatasets(a, b, out);
        String processName = name("re_job") + "@it";

        String guid = lineage.process(job, processName).inputs(a).outputs(out).register()
                .guidOf(job, processName).orElseThrow();
        cleanupEntity(guid);
        String guidAgain = lineage.process(job, processName).inputs(b).outputs(out).register()
                .guidOf(job, processName).orElseThrow();

        assertThat(guidAgain).as("placeholder guid must resolve to the existing process").isEqualTo(guid);
        LineageGraph graph = lineage.graph(out);
        assertThat(qualifiedNames(graph.upstreamDatasets(graph.base()))).containsExactly(b.qualifiedName());
    }

    /**
     * 없는 데이터셋을 입력으로 등록하면 기본 설정에서는 {@link LineageException}으로 실패하고 프로세스도 만들어지지
     * 않아야 합니다. {@link MissingEntityPolicy#CREATE}로 설정하면 없는 데이터셋을 만들고 등록에 성공해야 합니다.
     */
    @Test
    void missingDatasetsFailOrAreCreated() {
        EntityRef existing = table("existing");
        EntityRef missing = table("missing");
        createDatasets(existing);

        assertThatThrownBy(() -> lineage.process(job, name("fails") + "@it").inputs(missing).outputs(existing).register())
                .isInstanceOf(LineageException.class)
                .satisfies(e -> assertThat(((LineageException) e).getMissingEntities()).containsExactly(missing));
        assertThat(atlas.entities().findGuid(job, name("fails") + "@it")).isEmpty();

        AtlasLineage creating = new AtlasLineage(atlas).missingEntities(MissingEntityPolicy.CREATE);
        LineageRegistration registration = creating.process(job, name("creates") + "@it")
                .inputs(missing).outputs(existing).register();
        registration.guids().values().forEach(AtlasIT::cleanupEntity);

        assertThat(registration.createdEntities()).containsExactly(missing);
        assertThat(atlas.entities().findGuid(missing.typeName(), missing.qualifiedName())).isPresent();
    }

    /**
     * 컬럼 리니지(price, qty → amount)를 함께 등록하면 컬럼 리니지 엔티티의 {@code query} 속성이 상위 프로세스의
     * guid를 가리켜야 하고, amount의 리니지 그래프에서 price와 qty가 상위로 보여야 합니다.
     */
    @Test
    void columnLineageIsLinkedToItsProcess() {
        EntityRef src = table("col_src");
        EntityRef dst = table("col_dst");
        EntityRef price = column("col_src.price");
        EntityRef qty = column("col_src.qty");
        EntityRef amount = column("col_dst.amount");
        createDatasets(src, dst, price, qty, amount);
        String processName = name("col_job") + "@it";

        LineageRegistration registration = lineage.process(job, processName)
                .inputs(src).outputs(dst)
                .columnLineage(amount, price, qty)
                .register();
        registration.guids().values().forEach(AtlasIT::cleanupEntity);

        String processGuid = registration.guidOf(job, processName).orElseThrow();
        String columnLineageGuid = registration.guidOf(columnLineage, processName + ":" + amount.qualifiedName())
                .orElseThrow();
        AtlasEntity stored = atlas.entities().get(columnLineageGuid).getEntity();
        Object query = stored.getAttribute("query");
        assertThat(query).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) query).get("guid")).isEqualTo(processGuid);

        LineageGraph columnGraph = lineage.graph(amount);
        assertThat(qualifiedNames(columnGraph.upstreamDatasets(columnGraph.base())))
                .containsExactlyInAnyOrder(price.qualifiedName(), qty.qualifiedName());
    }

    /** 타입 정의를 조회해 프로세스 타입(job)은 프로세스로, 데이터셋 타입(table)은 프로세스가 아닌 것으로 판정해야 합니다. */
    @Test
    void processTypesAreResolvedFromTypeDefinitions() {
        assertThat(new io.datadynamics.atlas.client.lineage.ProcessTypeResolver(atlas).test(job)).isTrue();
        assertThat(new io.datadynamics.atlas.client.lineage.ProcessTypeResolver(atlas).test(table)).isFalse();
        assertThat(AtlasObjectId.QUALIFIED_NAME).isEqualTo("qualifiedName");
    }
}
