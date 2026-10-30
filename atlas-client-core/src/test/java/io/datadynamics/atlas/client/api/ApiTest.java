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

class ApiTest {

    private final FakeAtlasServer server = new FakeAtlasServer();
    private final AtlasClient atlas = AtlasClient.builder().serverUrl(server.url() + "/").build();

    @AfterEach
    void tearDown() {
        server.close();
    }

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
        // Creates must happen before updates so updated types can reference new ones.
        assertThat(server.requests().indexOf(post)).isLessThan(server.requests().indexOf(put));
    }

    @Test
    void findByNameReturnsCategorySpecificClass() {
        server.on(GET, "/api/atlas/v2/types/typedef/name/PII", 200,
                "{\"category\":\"CLASSIFICATION\",\"name\":\"PII\",\"entityTypes\":[\"hive_column\"]}");

        AtlasBaseTypeDef def = atlas.types().findByName("PII").orElseThrow();

        assertThat(def).isInstanceOf(AtlasClassificationDef.class);
        assertThat(((AtlasClassificationDef) def).getEntityTypes()).containsExactly("hive_column");
        assertThat(atlas.types().findByName("missing")).isEmpty();
    }

    @Test
    void entityLookupByQualifiedName() {
        server.on(GET, "/api/atlas/v2/entity/uniqueAttribute/type/hive_table", 200,
                "{\"entity\":{\"typeName\":\"hive_table\",\"guid\":\"g1\",\"attributes\":{\"qualifiedName\":\"db.t@c\"}}}");

        assertThat(atlas.entities().findGuid("hive_table", "db.t@c")).contains("g1");
        assertThat(server.lastRequest().rawQuery)
                .isEqualTo("attr%3AqualifiedName=db.t%40c&minExtInfo=true&ignoreRelationships=true");

        assertThat(atlas.entities().findByQualifiedName("hive_db", "missing@c")).isEmpty();
    }

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

    @Test
    void updateSingleAttributeSendsJsonValue() {
        server.on(PUT, "/api/atlas/v2/entity/guid/g1", 200, "{}");

        atlas.entities().updateAttribute("g1", "description", "hello");

        assertThat(server.lastRequest().rawQuery).isEqualTo("name=description");
        assertThat(server.lastRequest().body).isEqualTo("\"hello\"");
    }

    @Test
    void classificationsAreUnwrappedFromList() {
        server.on(GET, "/api/atlas/v2/entity/guid/g1/classifications", 200,
                "{\"list\":[{\"typeName\":\"PII\",\"propagate\":true}],\"totalCount\":1}");

        List<AtlasClassification> classifications = atlas.entities().getClassifications("g1");

        assertThat(classifications).extracting(AtlasClassification::getTypeName).containsExactly("PII");
        assertThat(classifications.get(0).getPropagate()).isTrue();
    }

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

    @Test
    void basicStreamPagesUntilEmptyPage() {
        // A short page (1 of 2) is not treated as the end: Atlas may return short pages mid-way.
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

    @Test
    void basicStreamIsLazy() {
        server.on(POST, "/api/atlas/v2/search/basic", req -> page(req, "a", "b"));

        assertThat(atlas.search().basicStream(SearchParameters.forType("t"), 2).findFirst()).isPresent();
        assertThat(server.requests()).hasSize(1);
    }

    private static FakeAtlasServer.Response page(FakeAtlasServer.Request request, String... guids) {
        String entities = java.util.Arrays.stream(guids)
                .map(g -> "{\"typeName\":\"hive_table\",\"guid\":\"" + g + "\"}")
                .collect(Collectors.joining(","));
        return FakeAtlasServer.respond(200, "{\"queryType\":\"BASIC\",\"entities\":[" + entities + "]}");
    }
}
