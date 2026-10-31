package io.datadynamics.atlas.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.datadynamics.atlas.client.http.AtlasTransport;
import io.datadynamics.atlas.client.http.QueryParams;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.instance.AtlasClassification;
import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;
import io.datadynamics.atlas.client.model.instance.AtlasEntityWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;
import io.datadynamics.atlas.client.model.instance.ClassificationAssociateRequest;
import io.datadynamics.atlas.client.model.instance.EntityAuditEvent;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.datadynamics.atlas.client.http.AtlasTransport.DELETE;
import static io.datadynamics.atlas.client.http.AtlasTransport.GET;
import static io.datadynamics.atlas.client.http.AtlasTransport.POST;
import static io.datadynamics.atlas.client.http.AtlasTransport.PUT;

/**
 * {@code /api/atlas/v2/entity} 엔드포인트로 엔티티 CRUD, classification, 레이블, business metadata, 감사 이력을
 * 다룹니다.
 * <p>
 * 모든 조회는 guid로도, {@code typeName + qualifiedName}으로도 할 수 있으므로 호출하는 쪽이 guid를 직접
 * 알아낼 일이 거의 없습니다. 엔티티가 없을 때 {@code find*} 메서드는 {@link Optional#empty()}를 돌려주고,
 * {@code get*} 메서드는 {@link io.datadynamics.atlas.client.exception.AtlasNotFoundException}을 던집니다.
 * <p>
 * 상태를 가지지 않으므로 여러 스레드에서 함께 써도 됩니다. 보통 {@link io.datadynamics.atlas.client.AtlasClient}를
 * 통해 얻습니다.
 */
public final class EntitiesApi {

    private static final String BASE = "/api/atlas/v2/entity";

    private final AtlasTransport transport;

    /**
     * API 객체를 만듭니다.
     *
     * @param transport 요청을 보낼 전송 계층
     */
    public EntitiesApi(AtlasTransport transport) {
        this.transport = transport;
    }

    // ---------------------------------------------------------------- 조회

    /**
     * guid로 엔티티를 가져옵니다. 참조 엔티티와 relationship 속성을 모두 포함합니다.
     * {@code GET /api/atlas/v2/entity/guid/{guid}}를 호출합니다.
     *
     * @param guid 엔티티 guid
     * @return 엔티티와 참조 엔티티들
     * @throws io.datadynamics.atlas.client.exception.AtlasNotFoundException 엔티티가 없을 때
     */
    public AtlasEntityWithExtInfo get(String guid) {
        return get(guid, false, false);
    }

    /**
     * guid로 엔티티를 가져오되, 응답에 담을 정보의 양을 조절합니다.
     * {@code GET /api/atlas/v2/entity/guid/{guid}}를 호출합니다.
     *
     * @param guid                엔티티 guid
     * @param minExtInfo          참조 엔티티에 대해 최소한의 정보만 받습니다
     * @param ignoreRelationships {@code relationshipAttributes}를 받지 않습니다. relationship이 많은 엔티티
     *                            (예: 컬럼이 수천 개인 테이블)는 이 옵션을 켜면 훨씬 빠릅니다
     * @return 엔티티와 참조 엔티티들
     * @throws io.datadynamics.atlas.client.exception.AtlasNotFoundException 엔티티가 없을 때
     */
    public AtlasEntityWithExtInfo get(String guid, boolean minExtInfo, boolean ignoreRelationships) {
        QueryParams query = QueryParams.create()
                .add("minExtInfo", minExtInfo)
                .add("ignoreRelationships", ignoreRelationships);
        return transport.call(GET, guidPath(guid), query, null, AtlasEntityWithExtInfo.class);
    }

    /**
     * {@link #get(String)}과 같지만, 엔티티가 없으면 예외 대신 빈 {@link Optional}을 돌려줍니다.
     *
     * @param guid 엔티티 guid
     * @return 찾은 엔티티, 없으면 빈 {@link Optional}
     */
    public Optional<AtlasEntityWithExtInfo> find(String guid) {
        return ApiSupport.findOptional(() -> get(guid));
    }

    /**
     * guid로 엔티티 요약 정보만 가져옵니다. {@code GET /api/atlas/v2/entity/guid/{guid}/header}를 호출합니다.
     *
     * @param guid 엔티티 guid
     * @return 엔티티 요약 정보
     * @throws io.datadynamics.atlas.client.exception.AtlasNotFoundException 엔티티가 없을 때
     */
    public AtlasEntityHeader getHeader(String guid) {
        return transport.call(GET, guidPath(guid) + "/header", null, null, AtlasEntityHeader.class);
    }

    /**
     * 타입 이름과 uniqueAttributes로 엔티티를 가져옵니다.
     * {@code GET /api/atlas/v2/entity/uniqueAttribute/type/{typeName}?attr:<이름>=<값>}을 호출합니다.
     *
     * @param typeName         엔티티 타입 이름
     * @param uniqueAttributes 유일 속성 이름과 값
     * @return 엔티티와 참조 엔티티들
     * @throws io.datadynamics.atlas.client.exception.AtlasNotFoundException 엔티티가 없을 때
     */
    public AtlasEntityWithExtInfo getByUniqueAttributes(String typeName, Map<String, ?> uniqueAttributes) {
        return getByUniqueAttributes(typeName, uniqueAttributes, false, false);
    }

    /**
     * 타입 이름과 uniqueAttributes로 엔티티를 가져오되, 응답에 담을 정보의 양을 조절합니다.
     *
     * @param typeName            엔티티 타입 이름
     * @param uniqueAttributes    유일 속성 이름과 값
     * @param minExtInfo          참조 엔티티에 대해 최소한의 정보만 받습니다
     * @param ignoreRelationships {@code relationshipAttributes}를 받지 않습니다
     * @return 엔티티와 참조 엔티티들
     * @throws io.datadynamics.atlas.client.exception.AtlasNotFoundException 엔티티가 없을 때
     */
    public AtlasEntityWithExtInfo getByUniqueAttributes(String typeName, Map<String, ?> uniqueAttributes,
                                                        boolean minExtInfo, boolean ignoreRelationships) {
        QueryParams query = QueryParams.create()
                .uniqueAttributes(uniqueAttributes)
                .add("minExtInfo", minExtInfo)
                .add("ignoreRelationships", ignoreRelationships);
        return transport.call(GET, uniqueAttributePath(typeName), query, null, AtlasEntityWithExtInfo.class);
    }

    /**
     * 타입 이름과 {@code qualifiedName}으로 엔티티를 가져옵니다.
     *
     * @param typeName      엔티티 타입 이름
     * @param qualifiedName 엔티티의 qualifiedName
     * @return 엔티티와 참조 엔티티들
     * @throws io.datadynamics.atlas.client.exception.AtlasNotFoundException 엔티티가 없을 때
     */
    public AtlasEntityWithExtInfo getByQualifiedName(String typeName, String qualifiedName) {
        return getByUniqueAttributes(typeName, Map.of(AtlasObjectId.QUALIFIED_NAME, qualifiedName));
    }

    /**
     * {@link #getByQualifiedName(String, String)}과 같지만, 엔티티가 없으면 빈 {@link Optional}을 돌려줍니다.
     *
     * @param typeName      엔티티 타입 이름
     * @param qualifiedName 엔티티의 qualifiedName
     * @return 찾은 엔티티, 없으면 빈 {@link Optional}
     */
    public Optional<AtlasEntityWithExtInfo> findByQualifiedName(String typeName, String qualifiedName) {
        return ApiSupport.findOptional(() -> getByQualifiedName(typeName, qualifiedName));
    }

    /**
     * {@code qualifiedName}으로 엔티티의 guid를 알아냅니다. relationship과 참조 엔티티 정보는 받지 않으므로
     * ({@code minExtInfo=true}, {@code ignoreRelationships=true}) 가볍게 호출할 수 있습니다.
     *
     * @param typeName      엔티티 타입 이름
     * @param qualifiedName 엔티티의 qualifiedName
     * @return 엔티티 guid, 엔티티가 없으면 빈 {@link Optional}
     */
    public Optional<String> findGuid(String typeName, String qualifiedName) {
        return ApiSupport.findOptional(() -> getByUniqueAttributes(typeName,
                        Map.of(AtlasObjectId.QUALIFIED_NAME, qualifiedName), true, true))
                .map(e -> e.getEntity().getGuid());
    }

    /**
     * 참조(guid 또는 uniqueAttributes)가 가리키는 엔티티를 가져옵니다. guid가 있으면 guid를 우선 사용합니다.
     *
     * @param objectId 엔티티 참조
     * @return 엔티티와 참조 엔티티들
     * @throws io.datadynamics.atlas.client.exception.AtlasNotFoundException 엔티티가 없을 때
     */
    public AtlasEntityWithExtInfo get(AtlasObjectId objectId) {
        if (objectId.getGuid() != null) {
            return get(objectId.getGuid());
        }
        return getByUniqueAttributes(objectId.getTypeName(), objectId.getUniqueAttributes());
    }

    /**
     * 여러 엔티티를 guid로 한 번에 가져옵니다. {@code GET /api/atlas/v2/entity/bulk?guid=...}를 호출합니다.
     *
     * @param guids 엔티티 guid 목록
     * @return 엔티티들과 참조 엔티티들
     */
    public AtlasEntitiesWithExtInfo getBulk(Collection<String> guids) {
        return getBulk(guids, false, false);
    }

    /**
     * 여러 엔티티를 guid로 한 번에 가져오되, 응답에 담을 정보의 양을 조절합니다.
     *
     * @param guids               엔티티 guid 목록
     * @param minExtInfo          참조 엔티티에 대해 최소한의 정보만 받습니다
     * @param ignoreRelationships {@code relationshipAttributes}를 받지 않습니다
     * @return 엔티티들과 참조 엔티티들
     */
    public AtlasEntitiesWithExtInfo getBulk(Collection<String> guids, boolean minExtInfo, boolean ignoreRelationships) {
        QueryParams query = QueryParams.create()
                .addAll("guid", guids)
                .add("minExtInfo", minExtInfo)
                .add("ignoreRelationships", ignoreRelationships);
        return transport.call(GET, BASE + "/bulk", query, null, AtlasEntitiesWithExtInfo.class);
    }

    /**
     * 한 타입의 여러 엔티티를 {@code qualifiedName}으로 한 번에 가져옵니다.
     * {@code GET /api/atlas/v2/entity/bulk/uniqueAttribute/type/{typeName}}을 호출하며, 각 값은
     * {@code attr_0:qualifiedName}, {@code attr_1:qualifiedName}, ... 처럼 번호를 붙인 쿼리 파라미터로 보냅니다.
     * <p>
     * Atlas 2.3은 없는 엔티티를 만나면 호출 전체를 실패시키지 않고 그 엔티티만 건너뜁니다. 그보다 오래된
     * 버전에서는 확인하지 않았습니다.
     *
     * @param typeName       엔티티 타입 이름
     * @param qualifiedNames 가져올 엔티티들의 qualifiedName
     * @return 찾은 엔티티들과 참조 엔티티들
     */
    public AtlasEntitiesWithExtInfo getBulkByQualifiedNames(String typeName, Collection<String> qualifiedNames) {
        QueryParams query = QueryParams.create();
        int i = 0;
        for (String qualifiedName : qualifiedNames) {
            query.add("attr_" + i++ + ":" + AtlasObjectId.QUALIFIED_NAME, qualifiedName);
        }
        return transport.call(GET, BASE + "/bulk/uniqueAttribute/type/" + QueryParams.pathSegment(typeName),
                query, null, AtlasEntitiesWithExtInfo.class);
    }

    // ---------------------------------------------------------------- 생성 / 수정

    /**
     * 엔티티를 생성하거나, 같은 유일 속성({@code qualifiedName})을 가진 엔티티가 이미 있으면 수정합니다.
     * 요청에 넣지 않은 속성은 바뀌지 않고 그대로 남습니다. {@code POST /api/atlas/v2/entity}를 호출합니다.
     *
     * @param entity 생성하거나 수정할 엔티티
     * @return 영향을 받은 엔티티와 guid 대응 정보
     */
    public EntityMutationResponse createOrUpdate(AtlasEntity entity) {
        return createOrUpdate(new AtlasEntityWithExtInfo(entity));
    }

    /**
     * 참조 엔티티 정보와 함께 엔티티를 생성하거나 수정합니다. {@code POST /api/atlas/v2/entity}를 호출합니다.
     *
     * @param entity 엔티티와 참조 엔티티들
     * @return 영향을 받은 엔티티와 guid 대응 정보
     */
    public EntityMutationResponse createOrUpdate(AtlasEntityWithExtInfo entity) {
        return transport.call(POST, BASE, null, entity, EntityMutationResponse.class);
    }

    /**
     * 여러 엔티티를 하나의 트랜잭션으로 생성하거나 수정합니다. {@code POST /api/atlas/v2/entity/bulk}를 호출합니다.
     * <p>
     * 엔티티끼리는 임시 guid({@link AtlasEntity#assignPlaceholderGuid()})나 qualifiedName으로 서로를 참조할 수
     * 있습니다.
     *
     * @param entities 생성하거나 수정할 엔티티들
     * @return 영향을 받은 엔티티와 임시 guid → 실제 guid 대응 정보
     */
    public EntityMutationResponse createOrUpdate(Collection<AtlasEntity> entities) {
        return createOrUpdate(new AtlasEntitiesWithExtInfo(entities));
    }

    /**
     * 참조 엔티티 정보와 함께 여러 엔티티를 한 번에 생성하거나 수정합니다.
     * {@code POST /api/atlas/v2/entity/bulk}를 호출합니다.
     *
     * @param entities 엔티티들과 참조 엔티티들
     * @return 영향을 받은 엔티티와 guid 대응 정보
     */
    public EntityMutationResponse createOrUpdate(AtlasEntitiesWithExtInfo entities) {
        return transport.call(POST, BASE + "/bulk", null, entities, EntityMutationResponse.class);
    }

    /**
     * uniqueAttributes로 찾은 엔티티에서, {@code entity}에 들어 있는 속성만 수정합니다.
     * {@code PUT /api/atlas/v2/entity/uniqueAttribute/type/{typeName}?attr:<이름>=<값>}을 호출합니다.
     *
     * @param typeName         엔티티 타입 이름
     * @param uniqueAttributes 수정할 엔티티를 찾을 유일 속성 이름과 값
     * @param entity           바꿀 속성만 채운 엔티티
     * @return 영향을 받은 엔티티 정보
     */
    public EntityMutationResponse partialUpdateByUniqueAttributes(String typeName, Map<String, ?> uniqueAttributes,
                                                                  AtlasEntity entity) {
        QueryParams query = QueryParams.create().uniqueAttributes(uniqueAttributes);
        return transport.call(PUT, uniqueAttributePath(typeName), query, new AtlasEntityWithExtInfo(entity),
                EntityMutationResponse.class);
    }

    /**
     * {@code qualifiedName}으로 찾은 엔티티에서 {@code entity}에 들어 있는 속성만 수정합니다.
     *
     * @param typeName      엔티티 타입 이름
     * @param qualifiedName 수정할 엔티티의 qualifiedName
     * @param entity        바꿀 속성만 채운 엔티티
     * @return 영향을 받은 엔티티 정보
     */
    public EntityMutationResponse partialUpdateByQualifiedName(String typeName, String qualifiedName, AtlasEntity entity) {
        return partialUpdateByUniqueAttributes(typeName, Map.of(AtlasObjectId.QUALIFIED_NAME, qualifiedName), entity);
    }

    /**
     * 엔티티의 속성 하나를 설정합니다. {@code PUT /api/atlas/v2/entity/guid/{guid}?name=<속성 이름>}을 호출하며,
     * 값은 JSON으로 직렬화해 본문으로 보냅니다(문자열, 숫자, 맵, 참조 등).
     *
     * @param guid          엔티티 guid
     * @param attributeName 설정할 속성 이름
     * @param value         새 값
     * @return 영향을 받은 엔티티 정보
     */
    public EntityMutationResponse updateAttribute(String guid, String attributeName, Object value) {
        QueryParams query = QueryParams.create().add("name", attributeName);
        return transport.call(PUT, guidPath(guid), query, value, EntityMutationResponse.class);
    }

    // ---------------------------------------------------------------- 삭제

    /**
     * 엔티티를 삭제합니다. {@code DELETE /api/atlas/v2/entity/guid/{guid}}를 호출합니다.
     * Atlas 기본 설정에서는 소프트 삭제이므로, 완전히 지우려면 {@link AdminApi#purge}를 참고합니다.
     *
     * @param guid 삭제할 엔티티 guid
     * @return 영향을 받은 엔티티 정보
     */
    public EntityMutationResponse delete(String guid) {
        return transport.call(DELETE, guidPath(guid), null, null, EntityMutationResponse.class);
    }

    /**
     * 타입 이름과 {@code qualifiedName}으로 찾은 엔티티를 삭제합니다.
     * {@code DELETE /api/atlas/v2/entity/uniqueAttribute/type/{typeName}?attr:qualifiedName=...}을 호출합니다.
     *
     * @param typeName      엔티티 타입 이름
     * @param qualifiedName 삭제할 엔티티의 qualifiedName
     * @return 영향을 받은 엔티티 정보
     */
    public EntityMutationResponse deleteByQualifiedName(String typeName, String qualifiedName) {
        QueryParams query = QueryParams.create().uniqueAttributes(Map.of(AtlasObjectId.QUALIFIED_NAME, qualifiedName));
        return transport.call(DELETE, uniqueAttributePath(typeName), query, null, EntityMutationResponse.class);
    }

    /**
     * 여러 엔티티를 guid로 한 번에 삭제합니다. {@code DELETE /api/atlas/v2/entity/bulk?guid=...}를 호출합니다.
     *
     * @param guids 삭제할 엔티티 guid 목록
     * @return 영향을 받은 엔티티 정보
     */
    public EntityMutationResponse delete(Collection<String> guids) {
        QueryParams query = QueryParams.create().addAll("guid", guids);
        return transport.call(DELETE, BASE + "/bulk", query, null, EntityMutationResponse.class);
    }

    // ---------------------------------------------------------------- classifications

    /**
     * 엔티티에 붙은 classification 목록을 가져옵니다. {@code GET /api/atlas/v2/entity/guid/{guid}/classifications}를
     * 호출합니다. Atlas는 목록을 {@code list} 필드로 감싸서 돌려주므로 그 안의 값만 꺼냅니다.
     *
     * @param guid 엔티티 guid
     * @return classification 목록, 응답에 {@code list}가 없으면 빈 목록
     */
    public List<AtlasClassification> getClassifications(String guid) {
        JsonNode node = transport.call(GET, guidPath(guid) + "/classifications", null, null, JsonNode.class);
        if (node == null || !node.has("list")) {
            return Collections.emptyList();
        }
        return AtlasJson.mapper().convertValue(node.get("list"), new TypeReference<List<AtlasClassification>>() {
        });
    }

    /**
     * 엔티티에 classification들을 붙입니다. {@code POST /api/atlas/v2/entity/guid/{guid}/classifications}를
     * 호출합니다.
     *
     * @param guid            엔티티 guid
     * @param classifications 붙일 classification 목록
     */
    public void addClassifications(String guid, List<AtlasClassification> classifications) {
        transport.call(POST, guidPath(guid) + "/classifications", null, classifications, Void.class);
    }

    /**
     * 엔티티에 classification 하나를 붙입니다.
     *
     * @param guid           엔티티 guid
     * @param classification 붙일 classification
     */
    public void addClassification(String guid, AtlasClassification classification) {
        addClassifications(guid, List.of(classification));
    }

    /**
     * 타입 이름과 {@code qualifiedName}으로 찾은 엔티티에 classification들을 붙입니다.
     * {@code POST /api/atlas/v2/entity/uniqueAttribute/type/{typeName}/classifications}를 호출합니다.
     *
     * @param typeName        엔티티 타입 이름
     * @param qualifiedName   엔티티의 qualifiedName
     * @param classifications 붙일 classification 목록
     */
    public void addClassificationsByQualifiedName(String typeName, String qualifiedName,
                                                  List<AtlasClassification> classifications) {
        QueryParams query = QueryParams.create().uniqueAttributes(Map.of(AtlasObjectId.QUALIFIED_NAME, qualifiedName));
        transport.call(POST, uniqueAttributePath(typeName) + "/classifications", query, classifications, Void.class);
    }

    /**
     * classification 하나를 여러 엔티티에 한 번의 호출로 붙입니다.
     * {@code POST /api/atlas/v2/entity/bulk/classification}을 호출합니다.
     *
     * @param classification 붙일 classification
     * @param guids          대상 엔티티 guid 목록
     */
    public void addClassificationToEntities(AtlasClassification classification, Collection<String> guids) {
        transport.call(POST, BASE + "/bulk/classification", null,
                new ClassificationAssociateRequest(classification, guids), Void.class);
    }

    /**
     * 엔티티에 이미 붙어 있는 classification의 속성, 전파 여부, 유효 기간을 수정합니다.
     * {@code PUT /api/atlas/v2/entity/guid/{guid}/classifications}를 호출합니다.
     *
     * @param guid            엔티티 guid
     * @param classifications 수정할 classification 목록
     */
    public void updateClassifications(String guid, List<AtlasClassification> classifications) {
        transport.call(PUT, guidPath(guid) + "/classifications", null, classifications, Void.class);
    }

    /**
     * 엔티티에서 classification 하나를 뗍니다.
     * {@code DELETE /api/atlas/v2/entity/guid/{guid}/classification/{classificationName}}을 호출합니다.
     *
     * @param guid               엔티티 guid
     * @param classificationName 뗄 classification 타입 이름
     */
    public void removeClassification(String guid, String classificationName) {
        transport.call(DELETE, guidPath(guid) + "/classification/" + QueryParams.pathSegment(classificationName),
                null, null, Void.class);
    }

    /**
     * 타입 이름과 {@code qualifiedName}으로 찾은 엔티티에서 classification 하나를 뗍니다.
     *
     * @param typeName           엔티티 타입 이름
     * @param qualifiedName      엔티티의 qualifiedName
     * @param classificationName 뗄 classification 타입 이름
     */
    public void removeClassificationByQualifiedName(String typeName, String qualifiedName, String classificationName) {
        QueryParams query = QueryParams.create().uniqueAttributes(Map.of(AtlasObjectId.QUALIFIED_NAME, qualifiedName));
        transport.call(DELETE, uniqueAttributePath(typeName) + "/classification/"
                + QueryParams.pathSegment(classificationName), query, null, Void.class);
    }

    // ---------------------------------------------------------------- 레이블

    /**
     * 엔티티의 레이블 전체를 주어진 집합으로 바꿉니다. {@code POST /api/atlas/v2/entity/guid/{guid}/labels}를
     * 호출합니다.
     *
     * @param guid   엔티티 guid
     * @param labels 새 레이블 집합
     */
    public void setLabels(String guid, Set<String> labels) {
        transport.call(POST, guidPath(guid) + "/labels", null, new LinkedHashSet<>(labels), Void.class);
    }

    /**
     * 기존 레이블을 유지한 채 레이블을 추가합니다. {@code PUT /api/atlas/v2/entity/guid/{guid}/labels}를 호출합니다.
     *
     * @param guid   엔티티 guid
     * @param labels 추가할 레이블
     */
    public void addLabels(String guid, Set<String> labels) {
        transport.call(PUT, guidPath(guid) + "/labels", null, new LinkedHashSet<>(labels), Void.class);
    }

    /**
     * 주어진 레이블을 엔티티에서 제거합니다. {@code DELETE /api/atlas/v2/entity/guid/{guid}/labels}를 호출하며
     * 제거할 레이블은 요청 본문으로 보냅니다.
     *
     * @param guid   엔티티 guid
     * @param labels 제거할 레이블
     */
    public void removeLabels(String guid, Set<String> labels) {
        transport.call(DELETE, guidPath(guid) + "/labels", null, new LinkedHashSet<>(labels), Void.class);
    }

    // ---------------------------------------------------------------- business metadata

    /**
     * business metadata 속성을 설정합니다. 값의 형태는 {@code {businessMetadataName: {attributeName: value}}}입니다.
     * {@code POST /api/atlas/v2/entity/guid/{guid}/businessmetadata?isOverwrite=...}를 호출합니다.
     *
     * @param guid             엔티티 guid
     * @param businessMetadata business metadata 이름 → (속성 이름 → 값) 맵
     * @param overwrite        {@code true}이면 엔티티의 business metadata 전체를 바꾸고, {@code false}이면 병합합니다
     */
    public void setBusinessMetadata(String guid, Map<String, Map<String, Object>> businessMetadata, boolean overwrite) {
        QueryParams query = QueryParams.create().add("isOverwrite", overwrite);
        transport.call(POST, guidPath(guid) + "/businessmetadata", query, businessMetadata, Void.class);
    }

    /**
     * 주어진 business metadata 속성을 엔티티에서 제거합니다.
     * {@code DELETE /api/atlas/v2/entity/guid/{guid}/businessmetadata}를 호출합니다.
     *
     * @param guid             엔티티 guid
     * @param businessMetadata 제거할 business metadata 이름 → (속성 이름 → 값) 맵
     */
    public void removeBusinessMetadata(String guid, Map<String, Map<String, Object>> businessMetadata) {
        transport.call(DELETE, guidPath(guid) + "/businessmetadata", null, businessMetadata, Void.class);
    }

    // ---------------------------------------------------------------- 감사 이력

    /**
     * 엔티티의 감사 이벤트를 최신 것부터 가져옵니다. {@code GET /api/atlas/v2/entity/{guid}/audit}를 호출합니다.
     * <p>
     * 다음 페이지를 가져오려면 이전 페이지 마지막 이벤트의 {@link EntityAuditEvent#getEventKey()}를
     * {@code startKey}로 넘깁니다.
     *
     * @param guid     엔티티 guid
     * @param startKey 이전 페이지 마지막 이벤트의 {@link EntityAuditEvent#getEventKey()}, 첫 페이지면 {@code null}
     * @param count    가져올 이벤트의 최대 개수
     * @return 감사 이벤트 목록, 응답이 비어 있으면 빈 목록
     */
    public List<EntityAuditEvent> getAuditEvents(String guid, String startKey, int count) {
        QueryParams query = QueryParams.create().add("startKey", startKey).add("count", count);
        List<EntityAuditEvent> events = transport.call(GET, BASE + "/" + QueryParams.pathSegment(guid) + "/audit",
                query, null, new TypeReference<List<EntityAuditEvent>>() {
                });
        return events == null ? new ArrayList<>() : events;
    }

    // ---------------------------------------------------------------- 경로 도우미

    // "/api/atlas/v2/entity/guid/{guid}" 경로를 만듭니다. guid는 URL 인코딩합니다.
    private static String guidPath(String guid) {
        return BASE + "/guid/" + QueryParams.pathSegment(guid);
    }

    // "/api/atlas/v2/entity/uniqueAttribute/type/{typeName}" 경로를 만듭니다. 유일 속성은 쿼리 파라미터로 따로 붙입니다.
    private static String uniqueAttributePath(String typeName) {
        return BASE + "/uniqueAttribute/type/" + QueryParams.pathSegment(typeName);
    }
}
