package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.client.lineage.EntityRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 리니지 등록 폼의 {@code type:qualifiedName} 참조 파싱을 검증합니다. */
class LineageControllerTest {

    /**
     * 빈 줄과 {@code #} 주석 줄이 섞인 입력을 파싱하면, 앞뒤 공백을 지운 참조만 순서대로 나오고
     * qualifiedName 안의 {@code :}(예: {@code hdfs://nn:8020/x})는 그대로 남는지 확인합니다.
     */
    @Test
    void parsesOneReferencePerLine() {
        assertThat(LineageController.refs("hive_table:db.a@prod\n\n# comment\n hdfs_path : hdfs://nn:8020/x \n"))
                .containsExactly(EntityRef.of("hive_table", "db.a@prod"), EntityRef.of("hdfs_path", "hdfs://nn:8020/x"));
    }

    /** 타입이 없거나 qualifiedName이 비어 있는 참조는 {@link IllegalArgumentException}으로 거부하는지 확인합니다. */
    @Test
    void rejectsReferenceWithoutType() {
        assertThatThrownBy(() -> LineageController.ref("db.a@prod")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LineageController.ref("hive_table:")).isInstanceOf(IllegalArgumentException.class);
    }
}
