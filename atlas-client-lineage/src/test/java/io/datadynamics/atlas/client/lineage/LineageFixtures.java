package io.datadynamics.atlas.client.lineage;

/** 리니지 테스트에서 함께 쓰는 Atlas 리니지 응답 JSON입니다. */
public final class LineageFixtures {

    /**
     * 기준 엔티티가 {@code daily}인 양방향 리니지 응답입니다. 그래프 모양은 다음과 같습니다.
     * {@code etl_job} 타입이 프로세스이고, {@code dashboard}는 삭제(DELETED) 상태입니다.
     * <pre>
     * sales ----\
     *            p_agg --> daily --> p_report --> dashboard (deleted)
     * customer -/           ^  \
     *                       \- p_fix (daily를 읽고 다시 쓰는 순환)
     * </pre>
     */
    public static final String LINEAGE = "{\"baseEntityGuid\":\"daily\",\"lineageDirection\":\"BOTH\",\"lineageDepth\":3,"
            + "\"guidEntityMap\":{"
            + "\"sales\":{\"guid\":\"sales\",\"typeName\":\"hive_table\",\"displayText\":\"sales\",\"attributes\":{\"qualifiedName\":\"raw.sales@prod\"}},"
            + "\"customer\":{\"guid\":\"customer\",\"typeName\":\"hive_table\",\"attributes\":{\"qualifiedName\":\"raw.customer@prod\"}},"
            + "\"p_agg\":{\"guid\":\"p_agg\",\"typeName\":\"etl_job\",\"displayText\":\"daily \\\"agg\\\"\",\"attributes\":{\"qualifiedName\":\"agg@prod\"}},"
            + "\"daily\":{\"guid\":\"daily\",\"typeName\":\"hive_table\",\"attributes\":{\"qualifiedName\":\"mart.daily@prod\"}},"
            + "\"p_fix\":{\"guid\":\"p_fix\",\"typeName\":\"etl_job\",\"attributes\":{\"qualifiedName\":\"fix@prod\"}},"
            + "\"p_report\":{\"guid\":\"p_report\",\"typeName\":\"etl_job\",\"attributes\":{\"qualifiedName\":\"report@prod\"}},"
            + "\"dashboard\":{\"guid\":\"dashboard\",\"typeName\":\"bi_dashboard\",\"status\":\"DELETED\",\"attributes\":{\"qualifiedName\":\"dash@prod\"}}},"
            + "\"relations\":["
            + "{\"fromEntityId\":\"sales\",\"toEntityId\":\"p_agg\"},"
            + "{\"fromEntityId\":\"customer\",\"toEntityId\":\"p_agg\"},"
            + "{\"fromEntityId\":\"p_agg\",\"toEntityId\":\"daily\"},"
            + "{\"fromEntityId\":\"daily\",\"toEntityId\":\"p_fix\"},"
            + "{\"fromEntityId\":\"p_fix\",\"toEntityId\":\"daily\"},"
            + "{\"fromEntityId\":\"daily\",\"toEntityId\":\"p_report\"},"
            + "{\"fromEntityId\":\"p_report\",\"toEntityId\":\"dashboard\"}]}";

    private LineageFixtures() {
    }
}
