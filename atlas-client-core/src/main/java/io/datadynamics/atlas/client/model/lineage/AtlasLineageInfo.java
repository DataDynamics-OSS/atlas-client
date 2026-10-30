package io.datadynamics.atlas.client.model.lineage;

import io.datadynamics.atlas.client.model.AtlasModel;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Raw lineage response: the entities involved keyed by guid, and directed edges between them.
 * Edges always point in data-flow direction (dataset -&gt; process -&gt; dataset).
 * <p>
 * The lineage module builds a traversable graph on top of this; here it is kept as Atlas sends it.
 */
public class AtlasLineageInfo extends AtlasModel {

    public enum LineageDirection {
        INPUT,
        OUTPUT,
        BOTH
    }

    private String baseEntityGuid;
    private LineageDirection lineageDirection;
    private Integer lineageDepth;
    private Map<String, AtlasEntityHeader> guidEntityMap;
    private Set<LineageRelation> relations;

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

    public Integer getLineageDepth() {
        return lineageDepth;
    }

    public void setLineageDepth(Integer lineageDepth) {
        this.lineageDepth = lineageDepth;
    }

    public Map<String, AtlasEntityHeader> getGuidEntityMap() {
        return guidEntityMap == null ? Collections.emptyMap() : guidEntityMap;
    }

    public void setGuidEntityMap(Map<String, AtlasEntityHeader> guidEntityMap) {
        this.guidEntityMap = guidEntityMap;
    }

    public Set<LineageRelation> getRelations() {
        return relations == null ? Collections.emptySet() : relations;
    }

    public void setRelations(Set<LineageRelation> relations) {
        this.relations = relations == null ? null : new LinkedHashSet<>(relations);
    }

    public static class LineageRelation extends AtlasModel {

        private String fromEntityId;
        private String toEntityId;
        private String relationshipId;

        public LineageRelation() {
        }

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

        @Override
        public int hashCode() {
            return Objects.hash(fromEntityId, toEntityId, relationshipId);
        }
    }
}
