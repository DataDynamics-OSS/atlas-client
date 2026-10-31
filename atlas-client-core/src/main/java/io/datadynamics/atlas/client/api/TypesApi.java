package io.datadynamics.atlas.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.datadynamics.atlas.client.exception.AtlasNotFoundException;
import io.datadynamics.atlas.client.http.AtlasTransport;
import io.datadynamics.atlas.client.http.QueryParams;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasBusinessMetadataDef;
import io.datadynamics.atlas.client.model.typedef.AtlasClassificationDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEnumDef;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef;
import io.datadynamics.atlas.client.model.typedef.AtlasStructDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypeDefHeader;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.model.typedef.TypeCategory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.datadynamics.atlas.client.http.AtlasTransport.DELETE;
import static io.datadynamics.atlas.client.http.AtlasTransport.GET;
import static io.datadynamics.atlas.client.http.AtlasTransport.POST;
import static io.datadynamics.atlas.client.http.AtlasTransport.PUT;

/**
 * {@code /api/atlas/v2/types} 엔드포인트로 타입 정의를 조회, 생성, 수정, 삭제합니다.
 * <p>
 * Atlas는 생성(이미 있으면 실패)과 수정(없으면 실패)을 별도 호출로 나눠 두었기 때문에, 원래는 타입이 이미
 * 있는지를 호출하는 쪽이 알아야 합니다. 이 클래스는 단순 호출 외에 {@link #createOrUpdate(AtlasTypesDef)}를
 * 제공해 그런 확인을 대신 해 줍니다.
 * <p>
 * 상태를 가지지 않으므로 여러 스레드에서 함께 써도 됩니다. 보통 {@link io.datadynamics.atlas.client.AtlasClient}를
 * 통해 얻습니다.
 */
public final class TypesApi {

    private static final String BASE = "/api/atlas/v2/types";

    // 정의 클래스별로 "/api/atlas/v2/types/{segment}/name/{name}" 경로에 들어갈 segment 값입니다.
    private static final Map<Class<? extends AtlasBaseTypeDef>, String> DEF_PATHS = Map.of(
            AtlasEnumDef.class, "enumdef",
            AtlasStructDef.class, "structdef",
            AtlasClassificationDef.class, "classificationdef",
            AtlasEntityDef.class, "entitydef",
            AtlasRelationshipDef.class, "relationshipdef",
            AtlasBusinessMetadataDef.class, "businessmetadatadef");

    private final AtlasTransport transport;

    /**
     * API 객체를 만듭니다.
     *
     * @param transport 요청을 보낼 전송 계층
     */
    public TypesApi(AtlasTransport transport) {
        this.transport = transport;
    }

    /**
     * Atlas가 알고 있는 모든 타입 정의(기본 제공 타입 포함)를 가져옵니다.
     * {@code GET /api/atlas/v2/types/typedefs}를 호출합니다.
     *
     * @return 모든 타입 정의
     */
    public AtlasTypesDef getAll() {
        return transport.call(GET, BASE + "/typedefs", null, null, AtlasTypesDef.class);
    }

    /**
     * 한 분류에 속한 타입 정의만 가져옵니다. {@code GET /api/atlas/v2/types/typedefs?type=...}를 호출합니다.
     *
     * @param category 가져올 분류. ENUM, STRUCT, CLASSIFICATION, ENTITY, RELATIONSHIP, BUSINESS_METADATA만 됩니다.
     * @return 해당 분류의 타입 정의
     * @throws IllegalArgumentException 위 목록에 없는 분류일 때
     */
    public AtlasTypesDef getAll(TypeCategory category) {
        QueryParams query = QueryParams.create().add("type", filterValue(category));
        return transport.call(GET, BASE + "/typedefs", query, null, AtlasTypesDef.class);
    }

    /**
     * 모든 타입의 요약 정보(guid, 이름, 분류, 서비스 구분)를 가져옵니다.
     * {@code GET /api/atlas/v2/types/typedefs/headers}를 호출하며, 전체 정의보다 응답이 훨씬 작습니다.
     *
     * @return 타입 요약 정보 목록
     */
    public List<AtlasTypeDefHeader> getHeaders() {
        return transport.call(GET, BASE + "/typedefs/headers", null, null,
                new TypeReference<List<AtlasTypeDefHeader>>() {
                });
    }

    /**
     * 모든 타입의 이름을 가져옵니다. 많은 타입이 있는지를 한 번에 확인하는 값싼 방법입니다.
     * 내부적으로 {@link #getHeaders()}를 호출합니다.
     *
     * @return 타입 이름 집합
     */
    public Set<String> getTypeNames() {
        return getHeaders().stream().map(AtlasTypeDefHeader::getName).collect(Collectors.toSet());
    }

    /**
     * 분류와 상관없이 이름으로 타입 정의를 찾습니다. {@code GET /api/atlas/v2/types/typedef/name/{name}}을
     * 호출하고, 응답의 {@code category} 값에 맞는 클래스(예: ENTITY면 {@link AtlasEntityDef})로 변환합니다.
     *
     * @param name 타입 이름
     * @return 찾은 정의, 없으면(HTTP 404) 빈 {@link Optional}
     * @throws IllegalStateException 응답의 분류가 이 클라이언트가 모르는 값일 때
     */
    public Optional<AtlasBaseTypeDef> findByName(String name) {
        return ApiSupport.findOptional(() -> {
            JsonNode node = transport.call(GET, BASE + "/typedef/name/" + QueryParams.pathSegment(name),
                    null, null, JsonNode.class);
            return node == null ? null : AtlasJson.convert(node, classFor(node.path("category").asText()));
        });
    }

    /**
     * 이름에 해당하는 타입이 있는지 확인합니다. {@link #findByName(String)}을 호출하므로 정의 전체를 내려받습니다.
     * 여러 타입을 확인할 때는 {@link #getTypeNames()}가 더 효율적입니다.
     *
     * @param name 타입 이름
     * @return 타입이 있으면 {@code true}
     */
    public boolean exists(String name) {
        return findByName(name).isPresent();
    }

    /**
     * 이름과 기대하는 정의 종류로 타입 정의를 가져옵니다.
     * {@code GET /api/atlas/v2/types/{entitydef|classificationdef|...}/name/{name}}을 호출합니다.
     *
     * @param name     타입 이름
     * @param defClass 기대하는 정의 클래스(예: {@code AtlasEntityDef.class})
     * @param <T>      정의 클래스 타입
     * @return 타입 정의
     * @throws AtlasNotFoundException   그런 타입이 없을 때
     * @throws IllegalArgumentException 지원하지 않는 정의 클래스일 때
     */
    public <T extends AtlasBaseTypeDef> T get(String name, Class<T> defClass) {
        String segment = DEF_PATHS.get(defClass);
        if (segment == null) {
            throw new IllegalArgumentException("Unsupported type definition class: " + defClass.getName());
        }
        return transport.call(GET, BASE + "/" + segment + "/name/" + QueryParams.pathSegment(name),
                null, null, defClass);
    }

    /**
     * {@link #get(String, Class)}와 같지만, 타입이 없으면 예외 대신 빈 {@link Optional}을 돌려줍니다.
     *
     * @param name     타입 이름
     * @param defClass 기대하는 정의 클래스
     * @param <T>      정의 클래스 타입
     * @return 찾은 정의, 없으면 빈 {@link Optional}
     */
    public <T extends AtlasBaseTypeDef> Optional<T> find(String name, Class<T> defClass) {
        return ApiSupport.findOptional(() -> get(name, defClass));
    }

    /** 이름으로 엔티티 타입 정의를 찾습니다. 없으면 빈 {@link Optional}을 돌려줍니다. */
    public Optional<AtlasEntityDef> findEntityDef(String name) {
        return find(name, AtlasEntityDef.class);
    }

    /** 이름으로 classification 타입 정의를 찾습니다. 없으면 빈 {@link Optional}을 돌려줍니다. */
    public Optional<AtlasClassificationDef> findClassificationDef(String name) {
        return find(name, AtlasClassificationDef.class);
    }

    /** 이름으로 relationship 타입 정의를 찾습니다. 없으면 빈 {@link Optional}을 돌려줍니다. */
    public Optional<AtlasRelationshipDef> findRelationshipDef(String name) {
        return find(name, AtlasRelationshipDef.class);
    }

    /** 이름으로 enum 타입 정의를 찾습니다. 없으면 빈 {@link Optional}을 돌려줍니다. */
    public Optional<AtlasEnumDef> findEnumDef(String name) {
        return find(name, AtlasEnumDef.class);
    }

    /** 이름으로 struct 타입 정의를 찾습니다. 없으면 빈 {@link Optional}을 돌려줍니다. */
    public Optional<AtlasStructDef> findStructDef(String name) {
        return find(name, AtlasStructDef.class);
    }

    /** 이름으로 business metadata 타입 정의를 찾습니다. 없으면 빈 {@link Optional}을 돌려줍니다. */
    public Optional<AtlasBusinessMetadataDef> findBusinessMetadataDef(String name) {
        return find(name, AtlasBusinessMetadataDef.class);
    }

    /**
     * 새 타입들을 생성합니다. {@code POST /api/atlas/v2/types/typedefs}를 호출합니다.
     *
     * @param typesDef 생성할 타입 정의들
     * @return Atlas에 저장된 형태의 정의
     * @throws io.datadynamics.atlas.client.exception.AtlasConflictException 하나라도 이미 있는 타입일 때
     */
    public AtlasTypesDef create(AtlasTypesDef typesDef) {
        return transport.call(POST, BASE + "/typedefs", null, typesDef, AtlasTypesDef.class);
    }

    /**
     * 기존 타입들을 수정합니다. {@code PUT /api/atlas/v2/types/typedefs}를 호출합니다.
     *
     * @param typesDef 수정할 타입 정의들
     * @return Atlas에 저장된 형태의 정의
     * @throws AtlasNotFoundException 하나라도 없는 타입일 때
     */
    public AtlasTypesDef update(AtlasTypesDef typesDef) {
        return transport.call(PUT, BASE + "/typedefs", null, typesDef, AtlasTypesDef.class);
    }

    /**
     * 아직 없는 타입은 생성하고, 이미 있는 타입은 수정합니다.
     * <p>
     * 먼저 {@link #getTypeNames()}로 존재하는 타입 이름을 확인해 정의를 생성용과 수정용으로 나눕니다.
     * 수정되는 타입이 새 타입을 참조할 수 있도록 새 타입을 먼저 생성한 뒤 수정합니다.
     * 두 호출은 하나의 트랜잭션이 아니므로, 생성은 성공하고 수정이 실패하면 생성된 타입은 그대로 남습니다.
     *
     * @param typesDef 생성하거나 수정할 타입 정의들
     * @return Atlas에 저장된 형태의 정의(생성 결과와 수정 결과를 합친 것)
     */
    public AtlasTypesDef createOrUpdate(AtlasTypesDef typesDef) {
        Set<String> existing = getTypeNames();
        AtlasTypesDef toCreate = new AtlasTypesDef();
        AtlasTypesDef toUpdate = new AtlasTypesDef();
        typesDef.stream().forEach(def -> (existing.contains(def.getName()) ? toUpdate : toCreate).add(def));

        AtlasTypesDef result = new AtlasTypesDef();
        if (!toCreate.isEmpty()) {
            AtlasTypesDef created = create(toCreate);
            if (created != null) {
                created.stream().forEach(result::add);
            }
        }
        if (!toUpdate.isEmpty()) {
            AtlasTypesDef updated = update(toUpdate);
            if (updated != null) {
                updated.stream().forEach(result::add);
            }
        }
        return result;
    }

    /**
     * 주어진 타입들을 삭제합니다. {@code DELETE /api/atlas/v2/types/typedefs}를 호출합니다.
     * 해당 타입의 인스턴스나 하위 타입이 아직 있으면 Atlas가 삭제를 거부합니다.
     *
     * @param typesDef 삭제할 타입 정의들
     */
    public void delete(AtlasTypesDef typesDef) {
        transport.call(DELETE, BASE + "/typedefs", null, typesDef, Void.class);
    }

    /**
     * 이름으로 타입 하나를 삭제합니다. {@code DELETE /api/atlas/v2/types/typedef/name/{name}}을 호출합니다.
     * 인스턴스나 하위 타입이 남아 있으면 Atlas가 거부합니다.
     *
     * @param name 삭제할 타입 이름
     */
    public void deleteByName(String name) {
        transport.call(DELETE, BASE + "/typedef/name/" + QueryParams.pathSegment(name), null, null, Void.class);
    }

    // typedefs 조회의 "type" 쿼리 파라미터에 넣을 값으로 바꿉니다. Atlas가 필터로 받아들이는 분류만 지원합니다.
    private static String filterValue(TypeCategory category) {
        switch (category) {
            case ENUM:
                return "enum";
            case STRUCT:
                return "struct";
            case CLASSIFICATION:
                return "classification";
            case ENTITY:
                return "entity";
            case RELATIONSHIP:
                return "relationship";
            case BUSINESS_METADATA:
                return "business_metadata";
            default:
                throw new IllegalArgumentException("Cannot list types of category " + category);
        }
    }

    // typedef 응답의 "category" 문자열을 역직렬화할 모델 클래스로 바꿉니다.
    private static Class<? extends AtlasBaseTypeDef> classFor(String category) {
        switch (category) {
            case "ENUM":
                return AtlasEnumDef.class;
            case "CLASSIFICATION":
                return AtlasClassificationDef.class;
            case "ENTITY":
                return AtlasEntityDef.class;
            case "RELATIONSHIP":
                return AtlasRelationshipDef.class;
            case "BUSINESS_METADATA":
                return AtlasBusinessMetadataDef.class;
            case "STRUCT":
                return AtlasStructDef.class;
            default:
                throw new IllegalStateException("Unexpected type category: " + category);
        }
    }
}
