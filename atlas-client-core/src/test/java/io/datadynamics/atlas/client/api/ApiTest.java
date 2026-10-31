package io.datadynamics.atlas.client.api;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.test.FakeAtlasServer;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.instance.AtlasClassification;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo.LineageDirection;
import io.datadynamics.atlas.client.model.search.SearchParameters;
import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasClassificationDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.datadynamics.atlas.client.http.AtlasTransport.GET;
import static io.datadynamics.atlas.client.http.AtlasTransport.POST;
import static io.datadynamics.atlas.client.http.AtlasTransport.PUT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * API 클래스들이 올바른 요청을 보내고 응답을 올바르게 해석하는지, 가짜 Atlas 서버({@link FakeAtlasServer})로
 * 검증합니다.
 * <p>
 * 타입 createOrUpdate의 분리·순서, 분류별 typedef 역직렬화, qualifiedName 조회, 임시 guid를 쓴 일괄 생성,
 * 단일 속성 수정, classification 목록 풀기, 리니지 조회 파라미터, 검색 스트림의 페이지 처리와 지연 실행을
 * 확인합니다.
 */
class ApiTest {

    private final FakeAtlasServer server = new FakeAtlasServer();
    private final AtlasClient atlas = AtlasClient.builder().serverUrl(server.url() + "/").build();

    // 테스트마다 띄운 가짜 서버를 닫습니다.
    @AfterEach
    void tearDown() {
        server.close();
    }

    /**
     * {@code TypesApi.createOrUpdate}가 새 타입과 기존 타입을 나눠 생성·수정하는지 확인합니다.
     * <p>
     * 준비: 헤더 조회에 {@code existing_table}이 있다고 응답하도록 설정 → 실행: 기존 타입 1개와 새 타입 2개로
     * createOrUpdate → 기대: POST에는 새 타입만, PUT에는 기존 타입만 담기고, POST가 PUT보다 먼저 나가며,
     * 결과에는 세 타입이 모두 들어 있습니다.
     */
    @Test
    void createOrUpdateTypesSplitsNewAndExistingTypes() {
        server.on(GET, "/api/atlas/v2/types/typedefs/headers", 200,
                "[{\"name\":\"DataSet\",\"category\":\"ENTITY\"},{\"name\":\"existing_table\",\"category\":\"ENTITY\"}]");
        server.on(POST, "/api/atlas/v2/types/typedefs", req -> FakeAtlasServer.respond(200, req.body));
        server.on(PUT, "/api/atlas/v2/types/typedefs", req -> FakeAtlasServer.respond(200, req.body));

        AtlasEntityDef existing = new AtlasEntityDef("existing_table");
        existing.addSuperType("DataSet");
        AtlasEntityDef created = new AtlasEntityDef("new_table");
        created.addSuperType("DataSet");
        AtlasClassificationDef pii = new AtlasClassificationDef("PII");

        AtlasTypesDef result = atlas.types().createOrUpdate(AtlasTypesDef.of(existing, created, pii));

        assertThat(result.stream().map(AtlasBaseTypeDef::getName)).containsExactlyInAnyOrder(
                "existing_table", "new_table", "PII");
        FakeAtlasServer.Request post = server.requests().stream().filter(r -> r.method.equals(POST)).findFirst().get();
        FakeAtlasServer.Request put = server.requests().stream().filter(r -> r.method.equals(PUT)).findFirst().get();
        assertThat(post.body).contains("new_table").contains("PII").doesNotContain("existing_table");
        assertThat(put.body).contains("existing_table").doesNotContain("new_table");
        // 수정되는 타입이 새 타입을 참조할 수 있도록 생성이 수정보다 먼저 일어나야 합니다.
        assertThat(server.requests().indexOf(post)).isLessThan(server.requests().indexOf(put));
    }

    /**
     * {@code TypesApi.findByName}이 응답의 category에 맞는 클래스로 역직렬화하는지 확인합니다.
     * <p>
     * 준비: CLASSIFICATION 타입 응답 등록 → 실행: 이름으로 조회 → 기대: {@link AtlasClassificationDef}로 읽히고,
     * 등록하지 않은 이름(404)은 빈 {@link java.util.Optional}이 됩니다.
     */
    @Test
    void findByNameReturnsCategorySpecificClass() {
        server.on(GET, "/api/atlas/v2/types/typedef/name/PII", 200,
                "{\"category\":\"CLASSIFICATION\",\"name\":\"PII\",\"entityTypes\":[\"hive_column\"]}");

        AtlasBaseTypeDef def = atlas.types().findByName("PII").orElseThrow();

        assertThat(def).isInstanceOf(AtlasClassificationDef.class);
        assertThat(((AtlasClassificationDef) def).getEntityTypes()).containsExactly("hive_column");
        assertThat(atlas.types().findByName("missing")).isEmpty();
    }

    /**
     * qualifiedName으로 엔티티 guid를 찾는 요청의 쿼리 문자열을 확인합니다.
     * <p>
     * 준비: uniqueAttribute 조회 응답 등록 → 실행: {@code findGuid} 호출 → 기대: guid를 돌려주고, 쿼리에
     * 인코딩된 {@code attr:qualifiedName}과 가벼운 조회 옵션({@code minExtInfo}, {@code ignoreRelationships})이
     * 붙습니다. 없는 엔티티(404)는 빈 {@link java.util.Optional}이 됩니다.
     */
    @Test
    void entityLookupByQualifiedName() {
        server.on(GET, "/api/atlas/v2/entity/uniqueAttribute/type/hive_table", 200,
                "{\"entity\":{\"typeName\":\"hive_table\",\"guid\":\"g1\",\"attributes\":{\"qualifiedName\":\"db.t@c\"}}}");

        assertThat(atlas.entities().findGuid("hive_table", "db.t@c")).contains("g1");
        assertThat(server.lastRequest().rawQuery)
                .isEqualTo("attr%3AqualifiedName=db.t%40c&minExtInfo=true&ignoreRelationships=true");

        assertThat(atlas.entities().findByQualifiedName("hive_db", "missing@c")).isEmpty();
    }

    /**
     * 임시 guid로 서로를 참조하는 엔티티들을 일괄 생성하는 요청을 확인합니다.
     * <p>
     * 준비: 데이터베이스 엔티티에 임시 guid를 붙이고, 테이블 엔티티가 relationship 속성으로 이를 참조 →
     * 실행: 일괄 createOrUpdate → 기대: 요청 본문의 참조에 임시 guid가 담기고, 응답에서 첫 guid를 얻습니다.
     */
    @Test
    void bulkCreateWithPlaceholderReferences() {
        server.on(POST, "/api/atlas/v2/entity/bulk", 200,
                "{\"mutatedEntities\":{\"CREATE\":[{\"typeName\":\"hive_db\",\"guid\":\"g-db\"}]}}");

        AtlasEntity db = new AtlasEntity("hive_db", Map.of("qualifiedName", "db@c", "name", "db"))
                .assignPlaceholderGuid();
        AtlasEntity table = new AtlasEntity("hive_table", Map.of("qualifiedName", "db.t@c", "name", "t"));
        table.setRelationshipAttribute("db", db.toObjectId());

        EntityMutationResponse response = atlas.entities().createOrUpdate(List.of(db, table));

        assertThat(response.firstGuid()).contains("g-db");
        String body = server.lastRequest().body;
        assertThat(body).contains("\"relationshipAttributes\":{\"db\":{\"guid\":\"" + db.getGuid() + "\"");
    }

    /**
     * 단일 속성 수정이 속성 이름을 쿼리로, 값을 JSON 본문으로 보내는지 확인합니다.
     * <p>
     * 준비: PUT 응답 등록 → 실행: 문자열 값으로 {@code updateAttribute} 호출 → 기대: 쿼리는
     * {@code name=description}, 본문은 JSON 문자열 {@code "hello"}입니다.
     */
    @Test
    void updateSingleAttributeSendsJsonValue() {
        server.on(PUT, "/api/atlas/v2/entity/guid/g1", 200, "{}");

        atlas.entities().updateAttribute("g1", "description", "hello");

        assertThat(server.lastRequest().rawQuery).isEqualTo("name=description");
        assertThat(server.lastRequest().body).isEqualTo("\"hello\"");
    }

    /**
     * classification 목록 응답의 {@code list} 감싸기를 풀어서 돌려주는지 확인합니다.
     * <p>
     * 준비: {@code {"list":[...]}} 형태의 응답 등록 → 실행: {@code getClassifications} 호출 → 기대: 목록 안의
     * classification이 필드 값과 함께 읽힙니다.
     */
    @Test
    void classificationsAreUnwrappedFromList() {
        server.on(GET, "/api/atlas/v2/entity/guid/g1/classifications", 200,
                "{\"list\":[{\"typeName\":\"PII\",\"propagate\":true}],\"totalCount\":1}");

        List<AtlasClassification> classifications = atlas.entities().getClassifications("g1");

        assertThat(classifications).extracting(AtlasClassification::getTypeName).containsExactly("PII");
        assertThat(classifications.get(0).getPropagate()).isTrue();
    }

    /**
     * qualifiedName으로 리니지를 조회할 때의 요청 파라미터와 응답 해석을 확인합니다.
     * <p>
     * 준비: 엔티티 3개와 간선 2개를 담은 리니지 응답 등록 → 실행: INPUT 방향, 깊이 2로 조회 → 기대: 엔티티 맵과
     * 간선이 모두 읽히고, 쿼리에 qualifiedName, 방향, 깊이가 순서대로 붙습니다.
     */
    @Test
    void lineageByQualifiedName() {
        server.on(GET, "/api/atlas/v2/lineage/uniqueAttribute/type/hive_table", 200,
                "{\"baseEntityGuid\":\"t2\",\"lineageDirection\":\"INPUT\",\"lineageDepth\":2,"
                        + "\"guidEntityMap\":{\"t1\":{\"typeName\":\"hive_table\",\"guid\":\"t1\"},"
                        + "\"p1\":{\"typeName\":\"hive_process\",\"guid\":\"p1\"},"
                        + "\"t2\":{\"typeName\":\"hive_table\",\"guid\":\"t2\"}},"
                        + "\"relations\":[{\"fromEntityId\":\"t1\",\"toEntityId\":\"p1\"},"
                        + "{\"fromEntityId\":\"p1\",\"toEntityId\":\"t2\"}]}");

        AtlasLineageInfo lineage = atlas.lineage().getByQualifiedName("hive_table", "db.t2@c", LineageDirection.INPUT, 2);

        assertThat(lineage.getGuidEntityMap()).containsOnlyKeys("t1", "p1", "t2");
        assertThat(lineage.getRelations()).hasSize(2);
        assertThat(server.lastRequest().rawQuery)
                .isEqualTo("attr%3AqualifiedName=db.t2%40c&direction=INPUT&depth=2");
    }

    /**
     * basic 검색 스트림이 빈 페이지를 만날 때까지 페이지를 계속 가져오는지 확인합니다.
     * <p>
     * 준비: 페이지 크기 2에 대해 2개, 1개, 2개, 0개를 차례로 돌려주도록 설정 → 실행: 스트림을 끝까지 소비 →
     * 기대: 다섯 결과를 모두 받고, offset이 0, 2, 4, 6으로 요청됩니다.
     */
    @Test
    void basicStreamPagesUntilEmptyPage() {
        // 결과가 모자란 페이지(2개 중 1개)를 끝으로 보지 않습니다. Atlas는 중간에도 모자란 페이지를 돌려줄 수 있습니다.
        server.on(POST, "/api/atlas/v2/search/basic", req -> page(req, "a", "b"))
                .on(POST, "/api/atlas/v2/search/basic", req -> page(req, "c"))
                .on(POST, "/api/atlas/v2/search/basic", req -> page(req, "d", "e"))
                .on(POST, "/api/atlas/v2/search/basic", req -> page(req));

        List<String> guids = atlas.search().basicStream(SearchParameters.forType("hive_table"), 2)
                .map(AtlasEntityHeader::getGuid)
                .collect(Collectors.toList());

        assertThat(guids).containsExactly("a", "b", "c", "d", "e");
        assertThat(server.requests()).extracting(r -> AtlasJson.fromJson(r.body, SearchParameters.class).getOffset())
                .containsExactly(0, 2, 4, 6);
    }

    /**
     * basic 검색 스트림이 필요한 만큼만 요청하는지(지연 실행) 확인합니다.
     * <p>
     * 준비: 첫 페이지 응답 등록 → 실행: 스트림에서 첫 요소만 꺼냄 → 기대: 요청이 한 번만 나갑니다.
     */
    @Test
    void basicStreamIsLazy() {
        server.on(POST, "/api/atlas/v2/search/basic", req -> page(req, "a", "b"));

        assertThat(atlas.search().basicStream(SearchParameters.forType("t"), 2).findFirst()).isPresent();
        assertThat(server.requests()).hasSize(1);
    }

    // 주어진 guid들을 엔티티로 담은 basic 검색 응답 한 페이지를 만듭니다. guid가 없으면 빈 페이지입니다.
    private static FakeAtlasServer.Response page(FakeAtlasServer.Request request, String... guids) {
        String entities = java.util.Arrays.stream(guids)
                .map(g -> "{\"typeName\":\"hive_table\",\"guid\":\"" + g + "\"}")
                .collect(Collectors.joining(","));
        return FakeAtlasServer.respond(200, "{\"queryType\":\"BASIC\",\"entities\":[" + entities + "]}");
    }
}
