package io.datadynamics.atlas.client.api;

import io.datadynamics.atlas.client.http.AtlasTransport;
import io.datadynamics.atlas.client.http.QueryParams;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo.LineageDirection;

import java.util.Map;

import static io.datadynamics.atlas.client.http.AtlasTransport.GET;

/**
 * {@code /api/atlas/v2/lineage}: lineage of a dataset or process.
 * <p>
 * Lineage is <em>registered</em> by creating process entities with {@code inputs}/{@code outputs}
 * through {@link EntitiesApi}; the lineage module provides a DSL for that.
 */
public final class LineageApi {

    private static final String BASE = "/api/atlas/v2/lineage";
    public static final int DEFAULT_DEPTH = 3;

    private final AtlasTransport transport;

    public LineageApi(AtlasTransport transport) {
        this.transport = transport;
    }

    public AtlasLineageInfo get(String guid) {
        return get(guid, LineageDirection.BOTH, DEFAULT_DEPTH);
    }

    /**
     * @param depth number of hops; each dataset -&gt; process -&gt; dataset step counts as one hop
     */
    public AtlasLineageInfo get(String guid, LineageDirection direction, int depth) {
        QueryParams query = QueryParams.create().add("direction", direction).add("depth", depth);
        return transport.call(GET, BASE + "/" + QueryParams.pathSegment(guid), query, null, AtlasLineageInfo.class);
    }

    public AtlasLineageInfo getByQualifiedName(String typeName, String qualifiedName) {
        return getByQualifiedName(typeName, qualifiedName, LineageDirection.BOTH, DEFAULT_DEPTH);
    }

    public AtlasLineageInfo getByQualifiedName(String typeName, String qualifiedName,
                                               LineageDirection direction, int depth) {
        QueryParams query = QueryParams.create()
                .uniqueAttributes(Map.of(AtlasObjectId.QUALIFIED_NAME, qualifiedName))
                .add("direction", direction)
                .add("depth", depth);
        return transport.call(GET, BASE + "/uniqueAttribute/type/" + QueryParams.pathSegment(typeName),
                query, null, AtlasLineageInfo.class);
    }
}
