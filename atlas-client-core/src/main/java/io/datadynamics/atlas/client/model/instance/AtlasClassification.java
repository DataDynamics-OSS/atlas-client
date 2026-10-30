package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Classification (tag) attached to an entity.
 */
public class AtlasClassification extends AtlasStruct {

    private String entityGuid;
    private EntityStatus entityStatus;
    private Boolean propagate;
    private Boolean removePropagationsOnEntityDelete;
    private List<TimeBoundary> validityPeriods;

    public AtlasClassification() {
    }

    public AtlasClassification(String typeName) {
        super(typeName);
    }

    public AtlasClassification(String typeName, Map<String, Object> attributes) {
        super(typeName, attributes);
    }

    public String getEntityGuid() {
        return entityGuid;
    }

    public void setEntityGuid(String entityGuid) {
        this.entityGuid = entityGuid;
    }

    public EntityStatus getEntityStatus() {
        return entityStatus;
    }

    public void setEntityStatus(EntityStatus entityStatus) {
        this.entityStatus = entityStatus;
    }

    public Boolean getPropagate() {
        return propagate;
    }

    public void setPropagate(Boolean propagate) {
        this.propagate = propagate;
    }

    public Boolean getRemovePropagationsOnEntityDelete() {
        return removePropagationsOnEntityDelete;
    }

    public void setRemovePropagationsOnEntityDelete(Boolean removePropagationsOnEntityDelete) {
        this.removePropagationsOnEntityDelete = removePropagationsOnEntityDelete;
    }

    public List<TimeBoundary> getValidityPeriods() {
        return validityPeriods;
    }

    public void setValidityPeriods(List<TimeBoundary> validityPeriods) {
        this.validityPeriods = validityPeriods;
    }

    public void addValidityPeriod(TimeBoundary period) {
        if (validityPeriods == null) {
            validityPeriods = new ArrayList<>();
        }
        validityPeriods.add(period);
    }

    /**
     * Validity window of a classification. Times use Atlas' {@code yyyy/MM/dd HH:mm:ss} format.
     */
    public static class TimeBoundary extends AtlasModel {

        private String startTime;
        private String endTime;
        private String timeZone;

        public TimeBoundary() {
        }

        public TimeBoundary(String startTime, String endTime, String timeZone) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.timeZone = timeZone;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        public String getTimeZone() {
            return timeZone;
        }

        public void setTimeZone(String timeZone) {
            this.timeZone = timeZone;
        }
    }
}
