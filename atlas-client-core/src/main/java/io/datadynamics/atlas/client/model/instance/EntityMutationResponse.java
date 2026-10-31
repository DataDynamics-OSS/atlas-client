package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 엔티티 생성·수정·삭제 호출의 결과입니다.
 * <p>
 * 작업 종류({@link EntityOperation})별로 영향을 받은 엔티티 요약 목록과, 임시 guid에서 실제 guid로의 대응표를
 * 담습니다. {@link #created()}, {@link #updated()}, {@link #deleted()} 같은 도우미는 해당 작업이 없을 때
 * {@code null} 대신 빈 목록을 돌려줍니다.
 */
public class EntityMutationResponse extends AtlasModel {

    /** 엔티티에 적용된 작업의 종류입니다. */
    public enum EntityOperation {
        CREATE,
        UPDATE,
        PARTIAL_UPDATE,
        DELETE,
        PURGE
    }

    private Map<EntityOperation, List<AtlasEntityHeader>> mutatedEntities;
    private Map<String, String> guidAssignments;

    /** 작업 종류별로 영향을 받은 엔티티 요약 목록을 돌려줍니다. 응답에 없으면 {@code null}일 수 있습니다. */
    public Map<EntityOperation, List<AtlasEntityHeader>> getMutatedEntities() {
        return mutatedEntities;
    }

    public void setMutatedEntities(Map<EntityOperation, List<AtlasEntityHeader>> mutatedEntities) {
        this.mutatedEntities = mutatedEntities;
    }

    /** 요청에 쓴 임시(음수) guid에서 Atlas가 부여한 실제 guid로의 맵을 돌려줍니다. */
    public Map<String, String> getGuidAssignments() {
        return guidAssignments;
    }

    public void setGuidAssignments(Map<String, String> guidAssignments) {
        this.guidAssignments = guidAssignments;
    }

    /**
     * 한 작업 종류에 해당하는 엔티티 목록을 돌려줍니다.
     *
     * @param operation 작업 종류
     * @return 엔티티 요약 목록, 없으면 빈 목록(수정할 수 없을 수 있습니다)
     */
    public List<AtlasEntityHeader> get(EntityOperation operation) {
        if (mutatedEntities == null) {
            return Collections.emptyList();
        }
        return mutatedEntities.getOrDefault(operation, Collections.emptyList());
    }

    /** 새로 생성된 엔티티 목록을 돌려줍니다. 없으면 빈 목록입니다. */
    public List<AtlasEntityHeader> created() {
        return get(EntityOperation.CREATE);
    }

    /** 전체 수정(UPDATE)과 부분 수정(PARTIAL_UPDATE)된 엔티티를 합친 목록을 돌려줍니다. 없으면 빈 목록입니다. */
    public List<AtlasEntityHeader> updated() {
        List<AtlasEntityHeader> partial = get(EntityOperation.PARTIAL_UPDATE);
        if (partial.isEmpty()) {
            return get(EntityOperation.UPDATE);
        }
        return Stream.concat(get(EntityOperation.UPDATE).stream(), partial.stream())
                .collect(Collectors.toList());
    }

    /** 삭제(DELETE)된 엔티티 목록을 돌려줍니다. PURGE된 엔티티는 포함하지 않습니다. 없으면 빈 목록입니다. */
    public List<AtlasEntityHeader> deleted() {
        return get(EntityOperation.DELETE);
    }

    /** 작업 종류별로 영향을 받은 엔티티 수를 돌려줍니다. 로그를 남길 때 편리합니다. */
    public Map<EntityOperation, Integer> summary() {
        Map<EntityOperation, Integer> summary = new EnumMap<>(EntityOperation.class);
        if (mutatedEntities != null) {
            mutatedEntities.forEach((op, headers) -> summary.put(op, headers == null ? 0 : headers.size()));
        }
        return summary;
    }

    /**
     * 주어진 타입과 {@code qualifiedName}을 가진, 처음으로 생성되거나 수정된 엔티티의 guid를 찾습니다.
     * 엔티티 하나를 생성 또는 수정(upsert)한 직후에 그 guid를 얻을 때 유용합니다.
     * <p>
     * 응답에 담긴 모든 작업 종류를 살펴보므로 삭제된 엔티티도 대상이 될 수 있습니다. 엔티티 요약에
     * {@code qualifiedName} 속성이 들어 있어야 찾을 수 있습니다.
     *
     * @param typeName      엔티티 타입 이름
     * @param qualifiedName 엔티티의 qualifiedName
     * @return 찾은 guid, 없으면 빈 {@link Optional}
     */
    public Optional<String> findGuid(String typeName, String qualifiedName) {
        if (mutatedEntities == null) {
            return Optional.empty();
        }
        return mutatedEntities.values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(h -> typeName.equals(h.getTypeName()) && qualifiedName.equals(h.qualifiedName()))
                .map(AtlasEntityHeader::getGuid)
                .findFirst();
    }

    /** 작업 종류와 상관없이, 응답에 담긴 첫 번째 엔티티의 guid를 돌려줍니다. 엔티티가 없으면 빈 {@link Optional}입니다. */
    public Optional<String> firstGuid() {
        if (mutatedEntities == null) {
            return Optional.empty();
        }
        return mutatedEntities.values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(AtlasEntityHeader::getGuid)
                .findFirst();
    }
}
