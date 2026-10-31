package io.datadynamics.atlas.client.lineage;

import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 리니지 등록 결과입니다.
 *
 * @param guids           프로세스, 컬럼 리니지, 새로 만든 엔티티의 guid. {@link EntityRef#key()}를 키로 씁니다.
 * @param createdEntities 없어서 새로 만든 엔티티({@link MissingEntityPolicy#CREATE}인 경우)
 * @param response        Atlas가 돌려준 원래 응답
 */
public record LineageRegistration(Map<String, String> guids, List<EntityRef> createdEntities,
                                  EntityMutationResponse response) {

    /** 맵과 목록을 변경 불가능한 복사본으로 바꿉니다. */
    public LineageRegistration {
        guids = Map.copyOf(guids);
        createdEntities = List.copyOf(createdEntities);
    }

    /**
     * 참조한 엔티티의 guid를 찾습니다. 생성 속성은 무시하고 타입과 qualifiedName으로만 찾습니다.
     *
     * @param ref 엔티티 참조
     * @return guid. 이번 등록 결과에 없으면 빈 값
     */
    public Optional<String> guidOf(EntityRef ref) {
        return Optional.ofNullable(guids.get(ref.key()));
    }

    /**
     * 타입과 qualifiedName으로 엔티티의 guid를 찾습니다.
     *
     * @param typeName      엔티티 타입
     * @param qualifiedName 엔티티 qualifiedName
     * @return guid. 이번 등록 결과에 없으면 빈 값
     */
    public Optional<String> guidOf(String typeName, String qualifiedName) {
        return guidOf(EntityRef.of(typeName, qualifiedName));
    }
}
