package io.datadynamics.atlas.demo;

import io.datadynamics.atlas.client.test.FakeAtlasServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pages against a fake Atlas: rendering, client request tracing and error reporting. */
@SpringBootTest
@AutoConfigureMockMvc
class DemoApplicationTest {

    static final FakeAtlasServer ATLAS = new FakeAtlasServer();

    @DynamicPropertySource
    static void atlas(DynamicPropertyRegistry registry) {
        registry.add("atlas.urls", ATLAS::url);
        registry.add("atlas.kafka.bootstrap-servers", () -> "");
    }

    @AfterAll
    static void stopAtlas() {
        ATLAS.close();
    }

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void resetAtlas() {
        ATLAS.reset();
    }

    @Test
    void dashboardShowsStatusAndTheRequestsItMade() throws Exception {
        ATLAS.on("GET", "/api/atlas/admin/status", 200, "{\"Status\":\"ACTIVE\"}")
                .on("GET", "/api/atlas/admin/version", 200, "{\"Version\":\"2.3.0\"}")
                .on("GET", "/api/atlas/v2/types/typedefs/headers", 200,
                        "[{\"name\":\"DataSet\",\"category\":\"ENTITY\"},{\"name\":\"PII\",\"category\":\"CLASSIFICATION\"}]");

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("ACTIVE"),
                        containsString("2.3.0"),
                        containsString("/api/atlas/admin/version"),
                        containsString("/api/atlas/v2/types/typedefs/headers"))))
                // Kafka is disabled: no menu entry.
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/kafka\""))));
    }

    @Test
    void atlasErrorsShowTheClientExceptionAndAtlasErrorCode() throws Exception {
        ATLAS.on("GET", "/api/atlas/v2/entity/guid/nope", 404,
                "{\"errorCode\":\"ATLAS-404-00-005\",\"errorMessage\":\"Given instance guid nope is invalid/not found\"}");

        mvc.perform(get("/entities/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(allOf(
                        containsString("AtlasNotFoundException"),
                        containsString("ATLAS-404-00-005"),
                        containsString("invalid/not found"))));
    }

    @Test
    void basicSearchSendsTheFormAsSearchParameters() throws Exception {
        ATLAS.on("POST", "/api/atlas/v2/search/basic", 200,
                "{\"entities\":[{\"guid\":\"g-1\",\"typeName\":\"hive_table\",\"displayText\":\"orders\","
                        + "\"status\":\"ACTIVE\",\"attributes\":{\"qualifiedName\":\"db.orders@prod\"}}]}");

        mvc.perform(get("/entities").param("search", "1").param("typeName", "hive_table")
                        .param("filterAttribute", "owner").param("filterOperator", "EQ").param("filterValue", "kim"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("db.orders@prod"), containsString("/entities/g-1"))));

        String body = ATLAS.lastRequest().body;
        assertThat(body).contains("\"typeName\":\"hive_table\"", "\"excludeDeletedEntities\":true",
                "\"attributeName\":\"owner\"", "\"attributeValue\":\"kim\"");
    }

    @Test
    void invalidTypeDefinitionIsReportedOnTheForm() throws Exception {
        mvc.perform(post("/types/plan").param("definition", "entityDefs: [: broken"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("alert error"), containsString("entityDefs: [: broken"))));
        assertThat(ATLAS.requests()).isEmpty();
    }

    @Test
    void unreachableAtlasIsShownOnTheDashboard() throws Exception {
        ATLAS.on("GET", "/api/atlas/admin/status", 503, "");

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Atlas에 접속하지 못했습니다"),
                        containsString("AtlasUnavailableException"))));
    }
}
