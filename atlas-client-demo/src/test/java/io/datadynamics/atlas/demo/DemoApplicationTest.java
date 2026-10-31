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

/**
 * 가짜 Atlas 서버({@link FakeAtlasServer})를 상대로 데모 페이지를 검증합니다.
 * 페이지가 제대로 그려지는지, 클라이언트가 보낸 요청이 화면에 기록되는지, 오류가 어떻게 보고되는지를 확인합니다.
 * Kafka는 꺼 둔 상태로 실행합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoApplicationTest {

    /** 모든 테스트가 함께 쓰는 가짜 Atlas 서버입니다. 테스트마다 {@link #resetAtlas()}로 초기화합니다. */
    static final FakeAtlasServer ATLAS = new FakeAtlasServer();

    /** 데모가 가짜 Atlas 서버에 접속하고, Kafka 주소를 비워 Kafka 기능을 끄도록 설정합니다. */
    @DynamicPropertySource
    static void atlas(DynamicPropertyRegistry registry) {
        registry.add("atlas.urls", ATLAS::url);
        registry.add("atlas.kafka.bootstrap-servers", () -> "");
    }

    /** 모든 테스트가 끝나면 가짜 Atlas 서버를 닫습니다. */
    @AfterAll
    static void stopAtlas() {
        ATLAS.close();
    }

    @Autowired
    MockMvc mvc;

    /** 이전 테스트가 등록한 응답과 기록된 요청을 지웁니다. */
    @BeforeEach
    void resetAtlas() {
        ATLAS.reset();
    }

    /**
     * 준비: 상태, 버전, 타입 헤더 응답을 등록합니다. 실행: 대시보드를 엽니다.
     * 기대: 상태와 버전, 그리고 클라이언트가 보낸 요청 경로가 화면에 보이고, Kafka 메뉴는 보이지 않습니다.
     */
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
                // Kafka가 꺼져 있으므로 메뉴에 Kafka 항목이 없어야 합니다.
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/kafka\""))));
    }

    /**
     * 준비: 엔티티 조회에 404와 Atlas 오류 본문을 돌려주게 합니다. 실행: 엔티티 상세 페이지를 엽니다.
     * 기대: 응답 상태가 404이고, 클라이언트 예외 이름, Atlas 오류 코드, 오류 메시지가 화면에 보입니다.
     */
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

    /**
     * 준비: basic search 응답을 등록합니다. 실행: 타입과 속성 필터를 채워 검색 폼을 제출합니다.
     * 기대: 결과 엔티티와 상세 링크가 보이고, Atlas로 보낸 요청 본문에 타입, 삭제 제외 여부, 속성 필터가 들어 있습니다.
     */
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

    /**
     * 실행: 문법이 깨진 YAML로 타입 plan을 요청합니다.
     * 기대: 오류 페이지가 아니라 같은 폼(200)에 오류와 입력값이 그대로 보이고, Atlas에는 요청을 보내지 않습니다.
     */
    @Test
    void invalidTypeDefinitionIsReportedOnTheForm() throws Exception {
        mvc.perform(post("/types/plan").param("definition", "entityDefs: [: broken"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("alert error"), containsString("entityDefs: [: broken"))));
        assertThat(ATLAS.requests()).isEmpty();
    }

    /**
     * 준비: 상태 조회에 503을 돌려주게 합니다. 실행: 대시보드를 엽니다.
     * 기대: 대시보드는 200으로 열리고, 접속 실패 안내와 {@code AtlasUnavailableException}이 보입니다.
     */
    @Test
    void unreachableAtlasIsShownOnTheDashboard() throws Exception {
        ATLAS.on("GET", "/api/atlas/admin/status", 503, "");

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Atlas에 접속하지 못했습니다"),
                        containsString("AtlasUnavailableException"))));
    }
}
