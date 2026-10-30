package io.datadynamics.atlas.client.lineage;

public final class LineageFixtures {

    /**
     * <pre>
     * sales ----\
     *            p_agg --> daily --> p_report --> dashboard (deleted)
     * customer -/           ^  \
     *                       \- p_fix (reads and writes daily: a cycle)
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
