package io.datadynamics.atlas.client.model.lineage;

import io.datadynamics.atlas.client.model.AtlasModel;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 가공하지 않은 리니지 응답입니다. 관련된 엔티티들(guid를 키로 하는 맵)과 그 사이의 방향 있는 간선들로
 * 이루어집니다. 간선은 항상 데이터가 흐르는 방향(데이터셋 -&gt; 프로세스 -&gt; 데이터셋)을 가리킵니다.
 * <p>
 * lineage 모듈은 이 위에 탐색 가능한 그래프를 만듭니다. 여기서는 Atlas가 보낸 형태 그대로 둡니다.
 */
public class AtlasLineageInfo extends AtlasModel {

    /** 리니지를 조회할 방향입니다. */
    public enum LineageDirection {
        /** 기준 엔티티로 들어오는 쪽(상류, upstream)입니다. */
        INPUT,
        /** 기준 엔티티에서 나가는 쪽(하류, downstream)입니다. */
        OUTPUT,
        /** 양쪽 모두입니다. */
        BOTH
    }

    private String baseEntityGuid;
    private LineageDirection lineageDirection;
    private Integer lineageDepth;
    private Map<String, AtlasEntityHeader> guidEntityMap;
    private Set<LineageRelation> relations;

    /** 리니지 조회의 기준이 된 엔티티의 guid를 돌려줍니다. */
    public String getBaseEntityGuid() {
        return baseEntityGuid;
    }

    public void setBaseEntityGuid(String baseEntityGuid) {
        this.baseEntityGuid = baseEntityGuid;
    }

    public LineageDirection getLineageDirection() {
        return lineageDirection;
    }

    public void setLineageDirection(LineageDirection lineageDirection) {
        this.lineageDirection = lineageDirection;
    }

    /** 조회한 리니지 깊이(hop 수)를 돌려줍니다. */
    public Integer getLineageDepth() {
        return lineageDepth;
    }

    public void setLineageDepth(Integer lineageDepth) {
        this.lineageDepth = lineageDepth;
    }

    /** 리니지에 등장하는 엔티티들을 guid를 키로 돌려줍니다. 없으면 {@code null} 대신 빈 맵입니다. */
    public Map<String, AtlasEntityHeader> getGuidEntityMap() {
        return guidEntityMap == null ? Collections.emptyMap() : guidEntityMap;
    }

    public void setGuidEntityMap(Map<String, AtlasEntityHeader> guidEntityMap) {
        this.guidEntityMap = guidEntityMap;
    }

    /** 엔티티 사이의 간선들을 돌려줍니다. 없으면 {@code null} 대신 빈 집합입니다. */
    public Set<LineageRelation> getRelations() {
        return relations == null ? Collections.emptySet() : relations;
    }

    /** 간선 집합을 설정합니다. 넘긴 집합은 순서를 유지하는 새 집합으로 복사해 보관합니다. */
    public void setRelations(Set<LineageRelation> relations) {
        this.relations = relations == null ? null : new LinkedHashSet<>(relations);
    }

    /**
     * 리니지 간선 하나입니다. {@code fromEntityId}에서 {@code toEntityId}로 데이터가 흐릅니다.
     * <p>
     * 세 필드가 모두 같으면 같은 간선으로 보도록 {@link #equals(Object)}/{@link #hashCode()}를 구현했습니다.
     * 그래서 집합에 넣으면 중복 간선이 하나로 합쳐집니다.
     */
    public static class LineageRelation extends AtlasModel {

        private String fromEntityId;
        private String toEntityId;
        private String relationshipId;

        /** 빈 간선을 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
        public LineageRelation() {
        }

        /**
         * 간선을 만듭니다.
         *
         * @param fromEntityId   데이터가 나오는 엔티티의 guid
         * @param toEntityId     데이터가 들어가는 엔티티의 guid
         * @param relationshipId 간선에 해당하는 relationship 인스턴스의 guid
         */
        public LineageRelation(String fromEntityId, String toEntityId, String relationshipId) {
            this.fromEntityId = fromEntityId;
            this.toEntityId = toEntityId;
            this.relationshipId = relationshipId;
        }

        public String getFromEntityId() {
            return fromEntityId;
        }

        public void setFromEntityId(String fromEntityId) {
            this.fromEntityId = fromEntityId;
        }

        public String getToEntityId() {
            return toEntityId;
        }

        public void setToEntityId(String toEntityId) {
            this.toEntityId = toEntityId;
        }

        public String getRelationshipId() {
            return relationshipId;
        }

        public void setRelationshipId(String relationshipId) {
            this.relationshipId = relationshipId;
        }

        /** 세 필드({@code fromEntityId}, {@code toEntityId}, {@code relationshipId})가 모두 같으면 같은 간선입니다. */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof LineageRelation)) {
                return false;
            }
            LineageRelation that = (LineageRelation) o;
            return Objects.equals(fromEntityId, that.fromEntityId)
                    && Objects.equals(toEntityId, that.toEntityId)
                    && Objects.equals(relationshipId, that.relationshipId);
        }

        /** {@link #equals(Object)}와 같은 세 필드로 해시 값을 계산합니다. */
        @Override
        public int hashCode() {
            return Objects.hash(fromEntityId, toEntityId, relationshipId);
        }
    }
}
