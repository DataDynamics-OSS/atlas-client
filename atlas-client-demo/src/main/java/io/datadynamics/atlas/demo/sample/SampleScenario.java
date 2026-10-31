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
 * 모든 모듈을 한 번씩 거치는 샘플 메타데이터입니다. 타입 정의(typedef 모듈), classification·레이블·
 * business metadata가 붙은 테이블(core 모듈), 리니지 프로세스 두 개(lineage 모듈)를 만듭니다.
 * <pre>
 * raw.orders ──┐
 *              ├─ daily_sales_etl ─ mart.daily_sales ─ sales_kpi_report ─ report.sales_kpi
 * raw.customers┘
 * </pre>
 * 다시 설정하면 같은 qualifiedName의 엔티티를 새로 만들지 않고 갱신합니다. 정리하면 엔티티를 삭제한 뒤
 * purge로 완전히 지우고, 타입도 삭제합니다.
 */
@Component
public class SampleScenario {

    /** 샘플 테이블 엔티티 타입 이름입니다(DataSet 하위 타입). */
    public static final String TABLE = "console_demo_table";
    /** 샘플 작업(프로세스) 엔티티 타입 이름입니다(Process 하위 타입). */
    public static final String JOB = "console_demo_job";
    /** 테이블 등급을 나타내는 enum 타입 이름입니다(BRONZE, SILVER, GOLD). */
    public static final String TIER = "console_demo_tier";
    /** 개인정보 테이블에 붙이는 classification 타입 이름입니다. */
    public static final String PII = "console_demo_PII";
    /** 테이블 품질 점수를 담는 business metadata 타입 이름입니다. */
    public static final String QUALITY = "console_demo_quality";
    /**
     * 샘플 타입을 지울 때의 순서입니다. 다른 타입을 쓰는 타입을 먼저 지워야 합니다. 그래서 테이블 타입을 쓰는
     * business metadata와 classification을 앞에, enum 타입을 쓰는 테이블을 그 뒤에, enum을 맨 뒤에 둡니다.
     */
    static final List<String> TYPES = List.of(QUALITY, PII, JOB, TABLE, TIER);

    /** 원천 주문 테이블의 qualifiedName입니다. */
    public static final String RAW_ORDERS = "raw.orders@demo";
    /** 원천 고객 테이블의 qualifiedName입니다. PII classification이 붙습니다. */
    public static final String RAW_CUSTOMERS = "raw.customers@demo";
    /** 일별 매출 마트 테이블의 qualifiedName입니다. 품질 business metadata가 붙습니다. */
    public static final String MART_DAILY_SALES = "mart.daily_sales@demo";
    /** 매출 KPI 리포트 테이블의 qualifiedName입니다. 레이블이 붙습니다. */
    public static final String REPORT_SALES_KPI = "report.sales_kpi@demo";

    private final AtlasClient client;
    private final TypeDefManager manager;
    private final ProcessTypeResolver processTypes;

    /**
     * 시나리오를 만듭니다.
     *
     * @param client       Atlas 클라이언트
     * @param manager      타입 plan/apply에 쓰는 {@link TypeDefManager}
     * @param processTypes 리니지 등록 시 타입이 프로세스인지 데이터셋인지 판별하는 데 쓰는 리졸버
     */
    public SampleScenario(AtlasClient client, TypeDefManager manager, ProcessTypeResolver processTypes) {
        this.client = client;
        this.manager = manager;
        this.processTypes = processTypes;
    }

    /**
     * {@link #setUp()}의 결과입니다.
     *
     * @param plan         적용 전에 계산한 타입 plan
     * @param types        타입 적용 결과
     * @param tables       테이블 엔티티 생성·갱신 응답
     * @param lineageGuids 리니지 등록으로 만들어진 프로세스와 엔티티의 guid. 키는 {@link EntityRef#key()}입니다.
     */
    public record SetupResult(TypePlan plan, TypeApplyResult types, EntityMutationResponse tables,
                              Map<String, String> lineageGuids) {
    }

    /**
     * 샘플 타입(enum, 테이블, 작업, classification, business metadata)을 만들기 위한 plan을 계산합니다.
     * Atlas에는 아무것도 바꾸지 않고, 현재 타입 정의와 비교만 합니다.
     *
     * @return 만들거나 바꿔야 할 내용이 담긴 타입 plan
     */
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

    /**
     * 샘플 메타데이터를 설정합니다. 순서는 다음과 같습니다.
     * <ol>
     * <li>타입 plan을 계산해 적용합니다.</li>
     * <li>테이블 엔티티 네 개를 한 번에 만들거나 갱신합니다. 고객 테이블에는 PII classification을,
     *     KPI 리포트 테이블에는 레이블을 붙입니다.</li>
     * <li>마트 테이블의 guid를 찾아 품질 business metadata(score=87)를 설정합니다.</li>
     * <li>{@link AtlasLineage}로 작업 프로세스 두 개를 등록해 테이블 사이의 리니지를 만듭니다.</li>
     * </ol>
     * 같은 qualifiedName을 쓰므로 여러 번 호출해도 엔티티가 늘어나지 않습니다.
     *
     * @return 각 단계의 결과
     */
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

    /**
     * 샘플 엔티티(작업과 테이블)를 활성·삭제 상태 모두 포함해 돌려줍니다.
     * 테이블 타입이 아직 없으면(샘플을 설정하기 전이면) 검색하지 않고 빈 목록을 돌려줍니다.
     *
     * @return 샘플 엔티티 헤더 목록
     */
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

    /**
     * {@link #cleanUp()}의 결과입니다.
     *
     * @param deleted 삭제한(활성 상태였던) 엔티티 수
     * @param purged  purge한 엔티티 수(이미 삭제 상태였던 것 포함)
     * @param types   타입 삭제 결과. 지울 타입이 없었으면 null
     */
    public record CleanupResult(int deleted, int purged, TypeApplyResult types) {
    }

    /**
     * 샘플 엔티티를 삭제하고 purge한 다음 샘플 타입을 삭제합니다.
     * <p>
     * Atlas의 purge는 이미 삭제(DELETED) 상태인 엔티티만 완전히 지우므로, 먼저 활성 엔티티를 삭제한 뒤
     * 전체를 purge합니다. 엔티티가 남아 있으면 타입을 지울 수 없으므로 타입 삭제는 마지막에 하며,
     * 존재하는 타입만 {@link #TYPES} 순서대로 지웁니다.
     *
     * @return 삭제·purge한 엔티티 수와 타입 삭제 결과
     */
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

    /** 샘플 테이블 엔티티 하나를 만듭니다(아직 Atlas에 보내지 않습니다). */
    private static AtlasEntity table(String qualifiedName, String name, String tier, long rowCount, String owner) {
        AtlasEntity entity = new AtlasEntity(TABLE);
        entity.setAttribute("qualifiedName", qualifiedName);
        entity.setAttribute("name", name);
        entity.setAttribute("tier", tier);
        entity.setAttribute("rowCount", rowCount);
        entity.setAttribute("owner", owner);
        return entity;
    }

    /** 샘플 테이블을 가리키는 리니지 입출력 참조를 만듭니다. */
    private static EntityRef ref(String qualifiedName) {
        return EntityRef.of(TABLE, qualifiedName);
    }
}
