package io.datadynamics.atlas.client.api;

import io.datadynamics.atlas.client.http.AtlasTransport;
import io.datadynamics.atlas.client.http.QueryParams;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo.LineageDirection;

import java.util.Map;

import static io.datadynamics.atlas.client.http.AtlasTransport.GET;

/**
 * {@code /api/atlas/v2/lineage} 엔드포인트로 데이터셋이나 프로세스의 리니지를 조회합니다.
 * <p>
 * 리니지를 <em>등록</em>하는 것은 이 클래스가 아니라, {@link EntitiesApi}로 {@code inputs}/{@code outputs}를 가진
 * 프로세스 엔티티를 만드는 방식으로 합니다. lineage 모듈이 이를 위한 DSL을 제공합니다.
 * <p>
 * 상태를 가지지 않으므로 여러 스레드에서 함께 써도 됩니다.
 */
public final class LineageApi {

    private static final String BASE = "/api/atlas/v2/lineage";
    /** 깊이를 지정하지 않았을 때 쓰는 기본 리니지 깊이(hop 수)입니다. */
    public static final int DEFAULT_DEPTH = 3;

    private final AtlasTransport transport;

    /**
     * API 객체를 만듭니다.
     *
     * @param transport 요청을 보낼 전송 계층
     */
    public LineageApi(AtlasTransport transport) {
        this.transport = transport;
    }

    /**
     * guid로 엔티티의 리니지를 양방향({@code BOTH}), 기본 깊이({@value #DEFAULT_DEPTH})로 가져옵니다.
     *
     * @param guid 기준 엔티티 guid
     * @return 리니지 정보
     */
    public AtlasLineageInfo get(String guid) {
        return get(guid, LineageDirection.BOTH, DEFAULT_DEPTH);
    }

    /**
     * guid로 엔티티의 리니지를 방향과 깊이를 지정해 가져옵니다.
     * {@code GET /api/atlas/v2/lineage/{guid}?direction=...&depth=...}를 호출합니다.
     *
     * @param guid      기준 엔티티 guid
     * @param direction 조회 방향(입력 쪽, 출력 쪽, 양쪽)
     * @param depth     hop 수. 데이터셋 -&gt; 프로세스 -&gt; 데이터셋 한 단계를 1 hop으로 셉니다
     * @return 리니지 정보
     */
    public AtlasLineageInfo get(String guid, LineageDirection direction, int depth) {
        QueryParams query = QueryParams.create().add("direction", direction).add("depth", depth);
        return transport.call(GET, BASE + "/" + QueryParams.pathSegment(guid), query, null, AtlasLineageInfo.class);
    }

    /**
     * 타입 이름과 {@code qualifiedName}으로 엔티티의 리니지를 양방향, 기본 깊이로 가져옵니다.
     *
     * @param typeName      기준 엔티티의 타입 이름
     * @param qualifiedName 기준 엔티티의 qualifiedName
     * @return 리니지 정보
     */
    public AtlasLineageInfo getByQualifiedName(String typeName, String qualifiedName) {
        return getByQualifiedName(typeName, qualifiedName, LineageDirection.BOTH, DEFAULT_DEPTH);
    }

    /**
     * 타입 이름과 {@code qualifiedName}으로 엔티티의 리니지를 방향과 깊이를 지정해 가져옵니다.
     * {@code GET /api/atlas/v2/lineage/uniqueAttribute/type/{typeName}?attr:qualifiedName=...}을 호출하므로
     * guid를 먼저 알아낼 필요가 없습니다.
     *
     * @param typeName      기준 엔티티의 타입 이름
     * @param qualifiedName 기준 엔티티의 qualifiedName
     * @param direction     조회 방향
     * @param depth         hop 수
     * @return 리니지 정보
     */
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
