package io.datadynamics.atlas.client.lineage.graph;

import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;
import io.datadynamics.atlas.client.model.instance.EntityStatus;

/**
 * 리니지 그래프 안의 데이터셋 또는 프로세스 하나입니다. 동일성은 guid로만 판단합니다.
 *
 * @param guid          엔티티 guid
 * @param typeName      엔티티 타입 이름. Atlas 응답에 엔티티 정보가 없으면 {@code null}일 수 있습니다.
 * @param qualifiedName 엔티티 qualifiedName. 없으면 {@code null}
 * @param displayText   Atlas가 돌려준 표시 문자열. 없으면 {@code null}
 * @param status        엔티티 상태(ACTIVE, DELETED 등)
 * @param process       프로세스이면 {@code true}, 데이터셋이면 {@code false}
 * @param header        Atlas가 돌려준 엔티티 요약 정보
 */
public record LineageNode(String guid, String typeName, String qualifiedName, String displayText,
                          EntityStatus status, boolean process, AtlasEntityHeader header) {

    static LineageNode of(AtlasEntityHeader header, boolean process) {
        return new LineageNode(header.getGuid(), header.getTypeName(), header.qualifiedName(),
                header.getDisplayText(), header.getStatus(), process, header);
    }

    /**
     * 데이터셋 노드인지 알려 줍니다. 프로세스가 아닌 노드는 모두 데이터셋으로 봅니다.
     *
     * @return 프로세스가 아니면 {@code true}
     */
    public boolean isDataset() {
        return !process;
    }

    /**
     * 엔티티가 (soft-)delete된 상태인지 알려 줍니다.
     *
     * @return 상태가 {@link EntityStatus#DELETED}이면 {@code true}
     */
    public boolean isDeleted() {
        return status == EntityStatus.DELETED;
    }

    /**
     * 사람이 읽기 좋은 이름을 돌려줍니다. 표시 문자열이 있으면 그것을, 없으면 qualifiedName을, 그것도
     * 없으면 guid를 씁니다.
     *
     * @return 표시용 이름
     */
    public String label() {
        if (displayText != null && !displayText.isBlank()) {
            return displayText;
        }
        return qualifiedName != null ? qualifiedName : guid;
    }

    /** guid가 같으면 같은 노드로 봅니다. 다른 필드는 비교하지 않습니다. */
    @Override
    public boolean equals(Object o) {
        return o instanceof LineageNode && guid.equals(((LineageNode) o).guid);
    }

    /** {@link #equals(Object)}와 맞추어 guid의 해시값을 돌려줍니다. */
    @Override
    public int hashCode() {
        return guid.hashCode();
    }

    /** {@code typeName:qualifiedName} 형식(qualifiedName이 없으면 guid)의 문자열을 돌려줍니다. */
    @Override
    public String toString() {
        return typeName + ":" + (qualifiedName != null ? qualifiedName : guid);
    }
}
