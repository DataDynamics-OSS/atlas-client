package io.datadynamics.atlas.client.typedef.plan;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.test.FakeAtlasServer;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.search.SearchParameters;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.typedef.TypeIssue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static io.datadynamics.atlas.client.http.AtlasTransport.DELETE;
import static io.datadynamics.atlas.client.http.AtlasTransport.GET;
import static io.datadynamics.atlas.client.http.AtlasTransport.POST;
import static io.datadynamics.atlas.client.http.AtlasTransport.PUT;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.STRING;
import static io.datadynamics.atlas.client.typedef.TypeDefs.attr;
import static io.datadynamics.atlas.client.typedef.TypeDefs.classification;
import static io.datadynamics.atlas.client.typedef.TypeDefs.end;
import static io.datadynamics.atlas.client.typedef.TypeDefs.entity;
import static io.datadynamics.atlas.client.typedef.TypeDefs.relationship;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TypeDefManager}가 Atlas REST API를 올바른 순서와 내용으로 호출하는지 검증합니다.
 * <p>
 * {@link FakeAtlasServer}가 typedefs 조회에 {@link #SERVER_TYPES}를 돌려주도록 준비하고, 테스트마다 필요한
 * POST/PUT/DELETE와 basic search 응답을 추가로 등록합니다. 그다음 보낸 요청의 메서드와 본문을 확인합니다.
 */
class TypeDefManagerTest {

    private static final String TYPEDEFS = "/api/atlas/v2/types/typedefs";
    private static final String BASIC_SEARCH = "/api/atlas/v2/search/basic";

    /**
     * 서버에 이미 있는 타입들입니다. DataSet, my_table(guid g1, 속성 owner), my_column, 둘을 잇는 composition
     * relationship my_table_columns, classification PII로 구성됩니다. DataSet을 뺀 나머지 셋은 serviceType이
     * {@code mine}입니다.
     */
    private static final String SERVER_TYPES = "{"
            + "\"entityDefs\":["
            + "{\"category\":\"ENTITY\",\"name\":\"DataSet\",\"attributeDefs\":[]},"
            + "{\"category\":\"ENTITY\",\"name\":\"my_table\",\"superTypes\":[\"DataSet\"],\"serviceType\":\"mine\","
            + "\"guid\":\"g1\",\"attributeDefs\":[{\"name\":\"owner\",\"typeName\":\"string\",\"isOptional\":true}]},"
            + "{\"category\":\"ENTITY\",\"name\":\"my_column\",\"superTypes\":[\"DataSet\"],\"serviceType\":\"mine\",\"attributeDefs\":[]}],"
            + "\"relationshipDefs\":[{\"category\":\"RELATIONSHIP\",\"name\":\"my_table_columns\",\"serviceType\":\"mine\","
            + "\"relationshipCategory\":\"COMPOSITION\","
            + "\"endDef1\":{\"type\":\"my_table\",\"name\":\"columns\",\"cardinality\":\"SET\",\"isContainer\":true},"
            + "\"endDef2\":{\"type\":\"my_column\",\"name\":\"table\",\"cardinality\":\"SINGLE\"}}],"
            + "\"classificationDefs\":[{\"category\":\"CLASSIFICATION\",\"name\":\"PII\",\"attributeDefs\":[]}]"
            + "}";

    private final FakeAtlasServer server = new FakeAtlasServer()
            .on(GET, TYPEDEFS, 200, SERVER_TYPES);
    private final TypeDefManager manager = new TypeDefManager(AtlasClient.builder().serverUrl(server.url()).build());

    /** 테스트마다 가짜 서버를 닫아 포트를 반납합니다. */
    @AfterEach
    void tearDown() {
        server.close();
    }

    /**
     * sync가 GET으로 현재 정의를 읽은 뒤 POST(생성), PUT(수정) 순서로 호출하는지 확인합니다.
     * 새 타입 my_view만 POST로 가고, my_table은 서버 guid와 기존 속성 owner를 유지한 채 comment가 추가되어
     * PUT으로 가며, 바뀌지 않은 PII는 어느 요청에도 들어가지 않아야 합니다.
     */
    @Test
    void syncCreatesThenUpdates() {
        server.on(POST, TYPEDEFS, req -> FakeAtlasServer.respond(200, req.body));
        server.on(PUT, TYPEDEFS, req -> FakeAtlasServer.respond(200, req.body));

        TypeApplyResult result = manager.sync(
                entity("my_table").dataSet().attribute(attr("owner", STRING)).attribute(attr("comment", STRING)),
                entity("my_view").dataSet(),
                classification("PII"));

        assertThat(result.created()).containsExactly("my_view");
        assertThat(result.updated()).containsExactly("my_table");
        assertThat(result.unchanged()).containsExactly("PII");

        List<String> methods = server.requests().stream().map(r -> r.method).collect(Collectors.toList());
        assertThat(methods).containsExactly(GET, POST, PUT);
        FakeAtlasServer.Request put = server.requests().get(2);
        AtlasTypesDef updated = AtlasJson.fromJson(put.body, AtlasTypesDef.class);
        assertThat(updated.getEntityDefs().get(0).getGuid()).isEqualTo("g1");
        assertThat(updated.getEntityDefs().get(0).getAttributeDefs()).extracting(a -> a.getName())
                .containsExactly("owner", "comment");
        assertThat(server.requests().get(1).body).contains("my_view").doesNotContain("my_table");
    }

    /**
     * 원하는 정의가 서버와 같으면 처음 GET 외에는 아무 요청도 보내지 않는지 확인합니다.
     */
    @Test
    void nothingIsSentWhenUpToDate() {
        TypeApplyResult result = manager.sync(entity("my_table").dataSet().attribute(attr("owner", STRING)));

        assertThat(result.hasChanges()).isFalse();
        assertThat(server.requests()).hasSize(1);
    }

    /**
     * 기존 타입에 필수 속성을 추가하는 plan은 오류이므로 apply가 {@link TypePlanException}을 던지고,
     * GET 외의 요청은 보내지 않는지 확인합니다.
     */
    @Test
    void planWithErrorsIsNotApplied() {
        TypePlan plan = manager.plan(entity("my_table").dataSet()
                .attribute(attr("owner", STRING))
                .attribute(attr("mandatory", STRING).required()));

        assertThatThrownBy(() -> manager.apply(plan))
                .isInstanceOf(TypePlanException.class)
                .hasMessageContaining("new attribute 'mandatory' must be optional");
        assertThat(server.requests()).extracting(r -> r.method).containsOnly(GET);
    }

    /**
     * relationship이 아직 참조하는 엔티티 타입(my_column)만 삭제하려 하면, 참조하는 relationship과
     * 참조 위치(endDef2)를 밝힌 오류가 나오는지 확인합니다.
     */
    @Test
    void deletingReferencedTypeIsBlocked() {
        server.on(POST, BASIC_SEARCH, 200, "{\"entities\":[]}");

        TypePlan plan = manager.planDelete(List.of("my_column"));

        assertThat(plan.getErrors()).extracting(TypeIssue::message).containsExactly(
                "still referenced by RELATIONSHIP my_table_columns (endDef2); delete it too or remove the reference");
    }

    /**
     * 엔티티(soft delete된 것 포함)가 남아 있는 타입은 삭제 오류가 되고, 서버에 없는 타입과 relationship은
     * 경고로 처리되는지 확인합니다. basic search 요청이 삭제된 엔티티를 포함하고 하위 타입은 제외하도록
     * 보내졌는지도 확인합니다.
     */
    @Test
    void deletingTypeWithEntitiesIsBlocked() {
        server.on(POST, BASIC_SEARCH, req -> {
            SearchParameters params = AtlasJson.fromJson(req.body, SearchParameters.class);
            boolean hasEntities = "my_table".equals(params.getTypeName());
            return FakeAtlasServer.respond(200, hasEntities
                    ? "{\"entities\":[{\"typeName\":\"my_table\",\"guid\":\"e1\",\"status\":\"DELETED\"}]}"
                    : "{\"entities\":[]}");
        });

        TypePlan plan = manager.planDelete(List.of("my_table", "my_column", "my_table_columns", "missing"));

        assertThat(plan.getErrors()).extracting(TypeIssue::message).containsExactly(
                "entities of this type exist (soft-deleted ones count too); delete and purge them first");
        assertThat(plan.getWarnings()).extracting(TypeIssue::typeName).contains("missing", "my_table_columns");
        SearchParameters sent = AtlasJson.fromJson(server.requests().get(1).body, SearchParameters.class);
        assertThat(sent.getExcludeDeletedEntities()).isFalse();
        assertThat(sent.getIncludeSubTypes()).isFalse();
    }

    /**
     * 서로 참조하는 타입을 함께 삭제하면 오류 없이 DELETE 요청이 가고, 본문에 서버의 정의
     * (엔티티 2개, relationship 1개)가 담기는지 확인합니다.
     */
    @Test
    void deleteSendsServerDefinitions() {
        server.on(POST, BASIC_SEARCH, 200, "{\"entities\":[]}");
        server.on(DELETE, TYPEDEFS, 204, "");

        TypeApplyResult result = manager.delete(List.of("my_table", "my_column", "my_table_columns"));

        assertThat(result.deleted()).containsExactly("my_table", "my_column", "my_table_columns");
        AtlasTypesDef sent = AtlasJson.fromJson(server.lastRequest().body, AtlasTypesDef.class);
        assertThat(sent.getEntityDefs()).hasSize(2);
        assertThat(sent.getRelationshipDefs()).hasSize(1);
    }

    /**
     * serviceType {@code mine}인 정의만 내보내고, guid 같은 서버 필드는 제거되는지 확인합니다.
     */
    @Test
    void exportByServiceTypeStripsServerFields() {
        AtlasTypesDef exported = manager.exportByServiceType("mine");

        assertThat(exported.stream().map(d -> d.getName()))
                .containsExactly("my_table", "my_column", "my_table_columns");
        assertThat(exported.getEntityDefs().get(0).getGuid()).isNull();
    }

    /**
     * DSL로 서버와 같은 relationship을 정의하면, 서버가 채운 기본값(isContainer 등)과 상관없이
     * UNCHANGED로 판단되는지 확인합니다.
     */
    @Test
    void relationshipDefinedWithDsl() {
        TypePlan plan = manager.plan(relationship("my_table_columns").composition()
                .end1(end("my_table", "columns").set().container())
                .end2(end("my_column", "table")));

        assertThat(plan.getChanges()).extracting(TypeChange::action).containsExactly(TypeChange.Action.UNCHANGED);
    }
}
