package io.datadynamics.atlas.client.lineage;

/**
 * 프로세스의 입력이나 출력으로 참조한 엔티티가 Atlas에 아직 없을 때 어떻게 할지 정합니다.
 * {@link AtlasLineage#missingEntities(MissingEntityPolicy)}로 설정합니다.
 */
public enum MissingEntityPolicy {

    /**
     * 참조한 엔티티를 모두 먼저 확인하고, 없는 것이 있으면 그 목록을 담아 실패합니다(기본값).
     * 이때 Atlas에는 아무것도 보내지 않으며 {@link LineageException}이 발생합니다.
     */
    FAIL,

    /**
     * 없는 엔티티를 {@code qualifiedName}, {@code name}, 그리고 {@link EntityRef#attributes()}에 지정한
     * 속성으로 만듭니다. 그 밖의 필수 속성이 있는 타입(예: {@code hive_table}은 {@code db}가 필요합니다)은
     * 그 속성을 {@link EntityRef#attributes()}에 넣어 주어야 합니다.
     */
    CREATE,

    /**
     * 확인하지 않고 요청을 보냅니다. 참조를 해석할 수 없으면 Atlas가 요청을 거부합니다.
     * 존재 여부 조회 호출이 없으므로 가장 빠르고, Atlas 없이 페이로드만 만들 때도 씁니다.
     */
    NO_CHECK
}
