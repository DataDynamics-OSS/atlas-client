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

class TypeDefFilesTest {

    @TempDir
    Path dir;

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

    @Test
    void readsAtlasModelJsonAndStripsServerFields() throws Exception {
        // Shape of the files under Atlas' models/ directory, plus fields a GET would add.
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
