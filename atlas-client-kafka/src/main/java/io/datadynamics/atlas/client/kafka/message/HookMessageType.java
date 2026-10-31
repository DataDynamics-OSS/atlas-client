package io.datadynamics.atlas.client.kafka.message;

/** Atlas가 {@code ATLAS_HOOK} 토픽에서 읽어 처리하는 V2 hook 메시지의 종류입니다. */
public enum HookMessageType {
    /** 엔티티 생성 또는 갱신({@link EntityCreateRequest})입니다. */
    ENTITY_CREATE_V2,
    /** 엔티티 부분 갱신({@link EntityPartialUpdateRequest})입니다. */
    ENTITY_PARTIAL_UPDATE_V2,
    /** 엔티티 전체 교체({@link EntityFullUpdateRequest})입니다. */
    ENTITY_FULL_UPDATE_V2,
    /** 엔티티 삭제({@link EntityDeleteRequest})입니다. */
    ENTITY_DELETE_V2
}
