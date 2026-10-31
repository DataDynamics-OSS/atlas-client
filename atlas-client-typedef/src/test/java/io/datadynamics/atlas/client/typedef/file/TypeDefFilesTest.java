package io.datadynamics.atlas.client.typedef.file;

import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.datadynamics.atlas.client.typedef.AttributeTypes.STRING;
import static io.datadynamics.atlas.client.typedef.TypeDefs.attr;
import static io.datadynamics.atlas.client.typedef.TypeDefs.classification;
import static io.datadynamics.atlas.client.typedef.TypeDefs.entity;
import static io.datadynamics.atlas.client.typedef.TypeDefs.typesDef;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TypeDefFiles}의 파일 읽기/쓰기를 검증합니다. YAML 왕복 변환, 출력 형식, Atlas 모델 JSON 읽기와
 * 서버 필드 제거, 디렉터리 전체 읽기 순서를 확인합니다. 각 테스트는 JUnit 임시 디렉터리를 씁니다.
 */
class TypeDefFilesTest {

    /** 테스트마다 새로 만들어지는 임시 디렉터리입니다. */
    @TempDir
    Path dir;

    /**
     * 정의를 YAML 파일로 쓰고 다시 읽으면 엔티티, 속성 설정, classification이 그대로 복원되는지 확인합니다.
     */
    @Test
    void yamlRoundTrip() {
        AtlasTypesDef types = typesDef(
                entity("my_table").dataSet().attribute(attr("owner", STRING).indexable()),
                classification("PII"));

        Path file = dir.resolve("types.yaml");
        TypeDefFiles.write(types, file);
        AtlasTypesDef read = TypeDefFiles.read(file);

        assertThat(read.getEntityDefs()).extracting(AtlasEntityDef::getName).containsExactly("my_table");
        assertThat(read.getEntityDefs().get(0).findAttributeDef("owner").orElseThrow().getIndexable()).isTrue();
        assertThat(read.getClassificationDefs()).hasSize(1);
    }

    /**
     * YAML 출력이 문서 시작 표시 없이 {@code entityDefs:}로 시작하고, 빈 목록과 null 값을 쓰지 않는지 확인합니다.
     */
    @Test
    void yamlIsCompactAndReadable() {
        String yaml = TypeDefFiles.toYaml(typesDef(entity("my_table").dataSet().attribute(attr("owner", STRING))));

        assertThat(yaml)
                .startsWith("entityDefs:")
                .contains("name: my_table")
                .contains("isOptional: true")
                .doesNotContain("enumDefs")
                .doesNotContain("null");
    }

    /**
     * Atlas 모델 파일 형식의 JSON을 읽을 수 있고, 읽을 때는 guid 같은 서버 필드가 유지되지만
     * JSON으로 내보낼 때는 guid, createTime, subTypes가 빠지고 typeVersion은 남는지 확인합니다.
     */
    @Test
    void readsAtlasModelJsonAndStripsServerFields() throws Exception {
        // Atlas models/ 디렉터리에 있는 파일의 모양에, GET 응답이라면 추가로 붙을 필드를 더한 것입니다.
        Path file = dir.resolve("1030-my_model.json");
        Files.writeString(file, "{\"enumDefs\":[],\"structDefs\":[],\"classificationDefs\":[],"
                + "\"entityDefs\":[{\"name\":\"my_table\",\"superTypes\":[\"DataSet\"],\"serviceType\":\"my\","
                + "\"typeVersion\":\"1.1\",\"guid\":\"g1\",\"createTime\":1700000000000,\"subTypes\":[\"x\"],"
                + "\"attributeDefs\":[{\"name\":\"owner\",\"typeName\":\"string\",\"cardinality\":\"SINGLE\","
                + "\"isIndexable\":false,\"isOptional\":true,\"isUnique\":false}]}],\"relationshipDefs\":[]}");

        AtlasTypesDef read = TypeDefFiles.read(file);
        assertThat(read.getEntityDefs().get(0).getGuid()).isEqualTo("g1");

        String exported = TypeDefFiles.toJson(read);
        assertThat(exported).contains("\"typeVersion\" : \"1.1\"")
                .doesNotContain("guid").doesNotContain("createTime").doesNotContain("subTypes");
    }

    /**
     * 하위 디렉터리를 포함한 여러 정의 파일을 경로 순서대로 합치고, 정의 파일이 아닌 파일(README.md)은
     * 무시하는지 확인합니다.
     */
    @Test
    void readAllMergesFilesInPathOrder() throws Exception {
        Files.createDirectories(dir.resolve("b"));
        TypeDefFiles.write(typesDef(entity("second").dataSet()), dir.resolve("b/2000-second.yaml"));
        TypeDefFiles.write(typesDef(entity("first").dataSet()), dir.resolve("1000-first.json"));
        Files.writeString(dir.resolve("README.md"), "ignored");

        AtlasTypesDef all = TypeDefFiles.readAll(dir);

        assertThat(all.getEntityDefs()).extracting(AtlasEntityDef::getName).containsExactly("first", "second");
    }
}
