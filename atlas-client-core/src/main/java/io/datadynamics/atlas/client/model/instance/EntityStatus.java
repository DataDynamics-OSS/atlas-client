package io.datadynamics.atlas.client.model.instance;

/**
 * 엔티티의 생명주기 상태입니다. {@code DELETED}는 소프트 삭제이고, {@code PURGED}는 완전히 지워진 상태입니다.
 */
public enum EntityStatus {
    /** 정상적으로 사용 중인 엔티티입니다. */
    ACTIVE,
    /** 소프트 삭제된 엔티티입니다. Atlas에는 남아 있어 조회할 수 있지만 삭제된 것으로 표시됩니다. */
    DELETED,
    /** 완전히 제거(purge)된 엔티티입니다. 더 이상 Atlas에 남아 있지 않습니다. */
    PURGED
}
