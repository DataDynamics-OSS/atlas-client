package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.client.lineage.EntityRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LineageControllerTest {

    @Test
    void parsesOneReferencePerLine() {
        assertThat(LineageController.refs("hive_table:db.a@prod\n\n# comment\n hdfs_path : hdfs://nn:8020/x \n"))
                .containsExactly(EntityRef.of("hive_table", "db.a@prod"), EntityRef.of("hdfs_path", "hdfs://nn:8020/x"));
    }

    @Test
    void rejectsReferenceWithoutType() {
        assertThatThrownBy(() -> LineageController.ref("db.a@prod")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LineageController.ref("hive_table:")).isInstanceOf(IllegalArgumentException.class);
    }
}
