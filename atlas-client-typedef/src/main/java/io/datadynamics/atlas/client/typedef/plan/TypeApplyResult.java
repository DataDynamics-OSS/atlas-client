package io.datadynamics.atlas.client.typedef.plan;

import java.util.List;

/**
 * {@link TypeDefManager#apply(TypePlan)}가 처리한 타입 이름을 동작별로 모은 결과입니다.
 *
 * @param created   생성된 타입 이름
 * @param updated   수정된 타입 이름
 * @param deleted   삭제된 타입 이름
 * @param unchanged 바뀌지 않은 타입 이름
 */
public record TypeApplyResult(List<String> created, List<String> updated, List<String> deleted,
                              List<String> unchanged) {

    /** 모든 목록을 불변 목록으로 복사합니다. */
    public TypeApplyResult {
        created = List.copyOf(created);
        updated = List.copyOf(updated);
        deleted = List.copyOf(deleted);
        unchanged = List.copyOf(unchanged);
    }

    /**
     * 생성, 수정, 삭제 중 하나라도 있었는지 알려 줍니다.
     *
     * @return 실제로 바뀐 타입이 있으면 {@code true}
     */
    public boolean hasChanges() {
        return !created.isEmpty() || !updated.isEmpty() || !deleted.isEmpty();
    }

    /** 바뀌지 않은 타입은 개수만 보여 주어 로그가 길어지지 않게 합니다. */
    @Override
    public String toString() {
        return "created=" + created + ", updated=" + updated + ", deleted=" + deleted + ", unchanged=" + unchanged.size();
    }
}
