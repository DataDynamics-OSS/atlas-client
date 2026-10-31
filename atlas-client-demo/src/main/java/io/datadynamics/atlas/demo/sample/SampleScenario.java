package io.datadynamics.atlas.demo.sample;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.lineage.AtlasLineage;
import io.datadynamics.atlas.client.lineage.EntityRef;
import io.datadynamics.atlas.client.lineage.ProcessTypeResolver;
import io.datadynamics.atlas.client.model.instance.AtlasClassification;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;
import io.datadynamics.atlas.client.model.instance.EntityStatus;
import io.datadynamics.atlas.client.model.search.SearchParameters;
import io.datadynamics.atlas.client.typedef.plan.TypeApplyResult;
import io.datadynamics.atlas.client.typedef.plan.TypeDefManager;
import io.datadynamics.atlas.client.typedef.plan.TypePlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.datadynamics.atlas.client.typedef.AttributeTypes.LONG;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.STRING;
import static io.datadynamics.atlas.client.typedef.TypeDefs.attr;
import static io.datadynamics.atlas.client.typedef.TypeDefs.businessMetadata;
import static io.datadynamics.atlas.client.typedef.TypeDefs.classification;
import static io.datadynamics.atlas.client.typedef.TypeDefs.entity;
import static io.datadynamics.atlas.client.typedef.TypeDefs.enumType;

/**
 * Sample metadata that touches every module: types (typedef), tables with classification, labels
 * and business metadata (core), and two lineage processes (lineage).
 * <pre>
 * raw.orders ──┐
 *              ├─ daily_sales_etl ─ mart.daily_sales ─ sales_kpi_report ─ report.sales_kpi
 * raw.customers┘
 * </pre>
 * Setting it up again updates the same entities; cleanup removes the entities (purged) and the types.
 */
@Component
public class SampleScenario {

    public static final String TABLE = "console_demo_table";
    public static final String JOB = "console_demo_job";
    public static final String TIER = "console_demo_tier";
    public static final String PII = "console_demo_PII";
    public static final String QUALITY = "console_demo_quality";
    /** Deleted in this order: users of a type first. */
    static final List<String> TYPES = List.of(QUALITY, PII, JOB, TABLE, TIER);

    public static final String RAW_ORDERS = "raw.orders@demo";
    public static final String RAW_CUSTOMERS = "raw.customers@demo";
    public static final String MART_DAILY_SALES = "mart.daily_sales@demo";
    public static final String REPORT_SALES_KPI = "report.sales_kpi@demo";

    private final AtlasClient client;
    private final TypeDefManager manager;
    private final ProcessTypeResolver processTypes;

    public SampleScenario(AtlasClient client, TypeDefManager manager, ProcessTypeResolver processTypes) {
        this.client = client;
        this.manager = manager;
        this.processTypes = processTypes;
    }

    public record SetupResult(TypePlan plan, TypeApplyResult types, EntityMutationResponse tables,
                              Map<String, String> lineageGuids) {
    }

    public TypePlan planTypes() {
        return manager.plan(
                enumType(TIER).values("BRONZE", "SILVER", "GOLD"),
                entity(TABLE).dataSet()
                        .attribute(attr("tier", TIER))
                        .attribute(attr("rowCount", LONG)),
                entity(JOB).process()
                        .attribute(attr("schedule", STRING)),
                classification(PII).entityTypes(TABLE),
                businessMetadata(QUALITY)
                        .attribute(attr("score", LONG))
                        .applicableTo(TABLE));
    }

    public SetupResult setUp() {
        TypePlan plan = planTypes();
        TypeApplyResult types = manager.apply(plan);

        List<AtlasEntity> tables = List.of(
                table(RAW_ORDERS, "orders", "BRONZE", 120_000, "kim"),
                table(RAW_CUSTOMERS, "customers", "BRONZE", 8_000, "kim"),
                table(MART_DAILY_SALES, "daily_sales", "SILVER", 365, "lee"),
                table(REPORT_SALES_KPI, "sales_kpi", "GOLD", 12, "park"));
        tables.get(1).setClassifications(List.of(new AtlasClassification(PII)));
        tables.get(3).setLabels(Set.of("dashboard", "finance"));
        EntityMutationResponse response = client.entities().createOrUpdate(tables);

        client.entities().findGuid(TABLE, MART_DAILY_SALES).ifPresent(guid -> client.entities().setBusinessMetadata(
                guid, Map.of(QUALITY, Map.of("score", 87)), false));

        AtlasLineage lineage = new AtlasLineage(client, processTypes);
        Map<String, String> guids = lineage.register(List.of(
                lineage.process(JOB, "daily_sales_etl@demo")
                        .inputs(ref(RAW_ORDERS), ref(RAW_CUSTOMERS))
                        .outputs(ref(MART_DAILY_SALES))
                        .attribute("schedule", "0 2 * * *"),
                lineage.process(JOB, "sales_kpi_report@demo")
                        .inputs(ref(MART_DAILY_SALES))
                        .outputs(ref(REPORT_SALES_KPI))
                        .attribute("schedule", "0 6 * * *"))).guids();
        return new SetupResult(plan, types, response, guids);
    }

    /** Active and deleted demo entities. */
    public List<AtlasEntityHeader> entities() {
        if (!client.types().exists(TABLE)) {
            return List.of();
        }
        List<AtlasEntityHeader> result = new ArrayList<>();
        for (String type : List.of(JOB, TABLE)) {
            SearchParameters params = SearchParameters.forType(type);
            params.setExcludeDeletedEntities(false);
            client.search().basicStream(params).forEach(result::add);
        }
        return result;
    }

    public record CleanupResult(int deleted, int purged, TypeApplyResult types) {
    }

    /** Deletes and purges the demo entities, then deletes the demo types. */
    public CleanupResult cleanUp() {
        List<AtlasEntityHeader> entities = entities();
        List<String> active = entities.stream().filter(h -> h.getStatus() != EntityStatus.DELETED)
                .map(AtlasEntityHeader::getGuid).collect(Collectors.toList());
        if (!active.isEmpty()) {
            client.entities().delete(active);
        }
        Set<String> all = entities.stream().map(AtlasEntityHeader::getGuid)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!all.isEmpty()) {
            client.admin().purge(all);
        }
        List<String> existing = TYPES.stream().filter(client.types()::exists).collect(Collectors.toList());
        TypeApplyResult types = existing.isEmpty() ? null : manager.delete(existing);
        return new CleanupResult(active.size(), all.size(), types);
    }

    private static AtlasEntity table(String qualifiedName, String name, String tier, long rowCount, String owner) {
        AtlasEntity entity = new AtlasEntity(TABLE);
        entity.setAttribute("qualifiedName", qualifiedName);
        entity.setAttribute("name", name);
        entity.setAttribute("tier", tier);
        entity.setAttribute("rowCount", rowCount);
        entity.setAttribute("owner", owner);
        return entity;
    }

    private static EntityRef ref(String qualifiedName) {
        return EntityRef.of(TABLE, qualifiedName);
    }
}
