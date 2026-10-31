package io.datadynamics.atlas.client.lineage;

import io.datadynamics.atlas.client.exception.AtlasClientException;

import java.util.List;

/**
 * 리니지를 등록할 수 없었음을 나타냅니다. 이 예외가 발생했다면 Atlas에는 아무것도 보내지 않은 상태입니다.
 */
public class LineageException extends AtlasClientException {

    private final List<EntityRef> missingEntities;

    /**
     * 예외를 만듭니다.
     *
     * @param message         오류 메시지
     * @param missingEntities Atlas에 없는 참조 엔티티(변경 불가능한 복사본으로 보관합니다)
     */
    public LineageException(String message, List<EntityRef> missingEntities) {
        super(message);
        this.missingEntities = List.copyOf(missingEntities);
    }

    /**
     * 참조했지만 Atlas에 존재하지 않는 엔티티들을 돌려줍니다.
     *
     * @return 없는 엔티티 참조 목록(변경 불가능)
     */
    public List<EntityRef> getMissingEntities() {
        return missingEntities;
    }
}
