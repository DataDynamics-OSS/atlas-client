package io.datadynamics.atlas.client.kafka.message;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * {@link EntityChangeEvent}의 엔티티나 relationship에 무슨 일이 일어났는지 나타냅니다.
 * 이름은 Atlas가 알림에 담아 보내는 {@code operationType} 값과 같습니다.
 */
public enum OperationType {
    /** 엔티티가 생성되었습니다. */
    ENTITY_CREATE,
    /** 엔티티가 갱신되었습니다. */
    ENTITY_UPDATE,
    /** 엔티티가 삭제되었습니다. */
    ENTITY_DELETE,
    /** 가져오기(import)로 엔티티가 생성되었습니다. */
    ENTITY_IMPORT_CREATE,
    /** 가져오기(import)로 엔티티가 갱신되었습니다. */
    ENTITY_IMPORT_UPDATE,
    /** 가져오기(import)로 엔티티가 삭제되었습니다. */
    ENTITY_IMPORT_DELETE,
    /** 엔티티에 classification이 추가되었습니다. */
    CLASSIFICATION_ADD,
    /** 엔티티에서 classification이 삭제되었습니다. */
    CLASSIFICATION_DELETE,
    /** 엔티티의 classification이 갱신되었습니다. */
    CLASSIFICATION_UPDATE,
    /** relationship이 생성되었습니다. */
    RELATIONSHIP_CREATE,
    /** relationship이 갱신되었습니다. */
    RELATIONSHIP_UPDATE,
    /** relationship이 삭제되었습니다. */
    RELATIONSHIP_DELETE,
    /** 더 새로운 Atlas 버전에서 추가되어 이 라이브러리가 모르는 작업입니다. */
    UNKNOWN;

    /**
     * JSON 값을 작업 종류로 바꿉니다. 모르는 값이면 역직렬화에 실패하지 않도록 {@link #UNKNOWN}을 돌려줍니다.
     *
     * @param value 알림의 {@code operationType} 문자열
     * @return 해당하는 작업 종류. 모르는 값이거나 {@code null}이면 {@link #UNKNOWN}
     */
    @JsonCreator
    public static OperationType from(String value) {
        for (OperationType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    /**
     * relationship에 대한 작업({@code RELATIONSHIP_*})인지 알려 줍니다.
     *
     * @return relationship 작업이면 {@code true}
     */
    public boolean isRelationshipOperation() {
        return name().startsWith("RELATIONSHIP_");
    }
}
