package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 엔티티에 붙은 classification(태그)입니다.
 * <p>
 * 타입 이름은 classification 타입 이름이고, 속성 값은 그 classification 타입이 정의한 속성들입니다.
 */
public class AtlasClassification extends AtlasStruct {

    private String entityGuid;
    private EntityStatus entityStatus;
    private Boolean propagate;
    private Boolean removePropagationsOnEntityDelete;
    private List<TimeBoundary> validityPeriods;

    /** 빈 classification을 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasClassification() {
    }

    /**
     * classification 타입 이름만 지정해 만듭니다.
     *
     * @param typeName classification 타입 이름(예: {@code PII})
     */
    public AtlasClassification(String typeName) {
        super(typeName);
    }

    /**
     * classification 타입 이름과 속성 값으로 만듭니다. 맵은 복사해서 보관합니다.
     *
     * @param typeName   classification 타입 이름
     * @param attributes classification 속성 값
     */
    public AtlasClassification(String typeName, Map<String, Object> attributes) {
        super(typeName, attributes);
    }

    /** 이 classification이 붙은 엔티티의 guid를 돌려줍니다. 전파된 classification이면 원래 엔티티의 guid입니다. */
    public String getEntityGuid() {
        return entityGuid;
    }

    public void setEntityGuid(String entityGuid) {
        this.entityGuid = entityGuid;
    }

    /** {@link #getEntityGuid()} 엔티티의 상태를 돌려줍니다. */
    public EntityStatus getEntityStatus() {
        return entityStatus;
    }

    public void setEntityStatus(EntityStatus entityStatus) {
        this.entityStatus = entityStatus;
    }

    /** 이 classification을 relationship으로 연결된 다른 엔티티로 전파할지 돌려줍니다. */
    public Boolean getPropagate() {
        return propagate;
    }

    public void setPropagate(Boolean propagate) {
        this.propagate = propagate;
    }

    /** 원래 엔티티가 삭제될 때 전파된 classification도 함께 제거할지 돌려줍니다. */
    public Boolean getRemovePropagationsOnEntityDelete() {
        return removePropagationsOnEntityDelete;
    }

    public void setRemovePropagationsOnEntityDelete(Boolean removePropagationsOnEntityDelete) {
        this.removePropagationsOnEntityDelete = removePropagationsOnEntityDelete;
    }

    /** classification이 유효한 기간 목록을 돌려줍니다. 비어 있으면 기간 제한이 없습니다. */
    public List<TimeBoundary> getValidityPeriods() {
        return validityPeriods;
    }

    public void setValidityPeriods(List<TimeBoundary> validityPeriods) {
        this.validityPeriods = validityPeriods;
    }

    /**
     * 유효 기간을 하나 추가합니다. 목록이 없으면 새로 만듭니다.
     *
     * @param period 추가할 유효 기간
     */
    public void addValidityPeriod(TimeBoundary period) {
        if (validityPeriods == null) {
            validityPeriods = new ArrayList<>();
        }
        validityPeriods.add(period);
    }

    /**
     * classification의 유효 기간입니다. 시간은 Atlas 형식인 {@code yyyy/MM/dd HH:mm:ss} 문자열로 씁니다.
     */
    public static class TimeBoundary extends AtlasModel {

        private String startTime;
        private String endTime;
        private String timeZone;

        /** 빈 기간을 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
        public TimeBoundary() {
        }

        /**
         * 유효 기간을 만듭니다.
         *
         * @param startTime 시작 시각({@code yyyy/MM/dd HH:mm:ss})
         * @param endTime   끝 시각({@code yyyy/MM/dd HH:mm:ss})
         * @param timeZone  시각을 해석할 시간대(예: {@code Asia/Seoul})
         */
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
