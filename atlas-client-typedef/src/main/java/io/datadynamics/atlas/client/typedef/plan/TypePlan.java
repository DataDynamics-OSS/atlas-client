package io.datadynamics.atlas.client.typedef.plan;

import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.typedef.TypeIssue;
import io.datadynamics.atlas.client.typedef.plan.TypeChange.Action;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 원하는 타입 정의를 서버의 현재 정의와 비교한 결과입니다.
 * <p>
 * {@link #render()}로 내용을 검토한 뒤 {@link TypeDefManager#apply(TypePlan)}로 적용합니다.
 * 변경 목록과 문제 목록은 생성 시점에 복사된 불변 목록이므로 여러 스레드에서 읽어도 안전합니다.
 */
public final class TypePlan {

    private final List<TypeChange> changes;
    private final List<TypeIssue> issues;

    /**
     * plan을 만듭니다. 보통은 직접 만들지 않고 {@link TypePlanner#plan}이나 {@link TypeDefManager}가 만듭니다.
     *
     * @param changes 타입별 변경 목록(복사되어 보관됩니다)
     * @param issues  발견된 오류와 경고(복사되어 보관됩니다)
     */
    public TypePlan(List<TypeChange> changes, List<TypeIssue> issues) {
        this.changes = List.copyOf(changes);
        this.issues = List.copyOf(issues);
    }

    /**
     * 모든 변경을 돌려줍니다. 바뀌지 않는 타입({@link Action#UNCHANGED})도 포함합니다.
     *
     * @return 불변 변경 목록
     */
    public List<TypeChange> getChanges() {
        return changes;
    }

    /**
     * 주어진 동작의 변경만 골라 돌려줍니다.
     *
     * @param action 고를 동작
     * @return 해당 동작의 변경 목록(새 목록)
     */
    public List<TypeChange> getChanges(Action action) {
        return changes.stream().filter(c -> c.action() == action).collect(Collectors.toList());
    }

    /**
     * 오류와 경고를 모두 돌려줍니다.
     *
     * @return 불변 문제 목록
     */
    public List<TypeIssue> getIssues() {
        return issues;
    }

    /**
     * 오류만 돌려줍니다. 하나라도 있으면 {@link TypeDefManager#apply(TypePlan)}가 거부합니다.
     *
     * @return 오류 목록
     */
    public List<TypeIssue> getErrors() {
        return issues.stream().filter(TypeIssue::isError).collect(Collectors.toList());
    }

    /**
     * 경고만 돌려줍니다.
     *
     * @return 경고 목록
     */
    public List<TypeIssue> getWarnings() {
        return issues.stream().filter(i -> !i.isError()).collect(Collectors.toList());
    }

    /**
     * 오류가 하나라도 있는지 알려 줍니다.
     *
     * @return 오류가 있으면 {@code true}
     */
    public boolean hasErrors() {
        return issues.stream().anyMatch(TypeIssue::isError);
    }

    /**
     * plan을 적용해도 Atlas를 전혀 호출하지 않는지(모든 변경이 {@link Action#UNCHANGED}인지) 알려 줍니다.
     *
     * @return 바꿀 것이 없으면 {@code true}
     */
    public boolean isEmpty() {
        return changes.stream().allMatch(c -> c.action() == Action.UNCHANGED);
    }

    /**
     * 주어진 동작에 해당하는 변경들의 payload를 Atlas가 요구하는 형태({@link AtlasTypesDef})로 묶어 돌려줍니다.
     *
     * @param action 묶을 동작
     * @return 해당 payload를 담은 새 {@link AtlasTypesDef}
     */
    public AtlasTypesDef payload(Action action) {
        AtlasTypesDef typesDef = new AtlasTypesDef();
        getChanges(action).forEach(c -> typesDef.add(c.payload()));
        return typesDef;
    }

    /**
     * Terraform 스타일의 요약 문자열을 만듭니다. 로그에 남기거나 적용 전에 검토할 때 씁니다.
     * <p>
     * 타입마다 기호, 동작, 범주, 이름을 한 줄에 쓰고 그 아래에 세부 변경을 들여 씁니다.
     * 문제가 있으면 "Issues:" 단락을 덧붙이고, 마지막 줄에 {@link #summary()}를 씁니다.
     *
     * @return 여러 줄로 된 요약 문자열
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        for (TypeChange change : changes) {
            sb.append(String.format(Locale.ROOT, "%s %-9s %-17s %s%n", change.action().symbol(),
                    change.action().label(), change.category(), change.name()));
            for (String detail : change.details()) {
                sb.append("      ").append(detail).append(System.lineSeparator());
            }
        }
        if (!issues.isEmpty()) {
            sb.append(System.lineSeparator()).append("Issues:").append(System.lineSeparator());
            for (TypeIssue issue : issues) {
                sb.append(String.format(Locale.ROOT, "  %-7s %s: %s%n", issue.severity(), issue.typeName(),
                        issue.message()));
            }
        }
        sb.append(System.lineSeparator()).append(summary());
        return sb.toString();
    }

    /**
     * 동작별 개수와 오류/경고 개수를 한 줄로 요약합니다.
     * 예: {@code Plan: 1 to create, 0 to update, 0 to delete, 3 unchanged; 0 errors, 1 warnings}
     *
     * @return 한 줄 요약
     */
    public String summary() {
        return String.format(Locale.ROOT, "Plan: %d to create, %d to update, %d to delete, %d unchanged; %d errors, %d warnings",
                getChanges(Action.CREATE).size(), getChanges(Action.UPDATE).size(),
                getChanges(Action.DELETE).size(), getChanges(Action.UNCHANGED).size(),
                getErrors().size(), getWarnings().size());
    }

    /** {@link #summary()}와 같은 문자열을 돌려줍니다. */
    @Override
    public String toString() {
        return summary();
    }
}
