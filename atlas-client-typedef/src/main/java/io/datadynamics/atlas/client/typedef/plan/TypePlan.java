package io.datadynamics.atlas.client.typedef.plan;

import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.typedef.TypeIssue;
import io.datadynamics.atlas.client.typedef.plan.TypeChange.Action;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Result of comparing desired type definitions with the server, ready to be reviewed
 * ({@link #render()}) and applied ({@link TypeDefManager#apply(TypePlan)}).
 */
public final class TypePlan {

    private final List<TypeChange> changes;
    private final List<TypeIssue> issues;

    public TypePlan(List<TypeChange> changes, List<TypeIssue> issues) {
        this.changes = List.copyOf(changes);
        this.issues = List.copyOf(issues);
    }

    public List<TypeChange> getChanges() {
        return changes;
    }

    public List<TypeChange> getChanges(Action action) {
        return changes.stream().filter(c -> c.action() == action).collect(Collectors.toList());
    }

    public List<TypeIssue> getIssues() {
        return issues;
    }

    public List<TypeIssue> getErrors() {
        return issues.stream().filter(TypeIssue::isError).collect(Collectors.toList());
    }

    public List<TypeIssue> getWarnings() {
        return issues.stream().filter(i -> !i.isError()).collect(Collectors.toList());
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(TypeIssue::isError);
    }

    /** {@code true} if applying the plan would not call Atlas at all. */
    public boolean isEmpty() {
        return changes.stream().allMatch(c -> c.action() == Action.UNCHANGED);
    }

    /** Payloads of the given action, grouped as Atlas expects them. */
    public AtlasTypesDef payload(Action action) {
        AtlasTypesDef typesDef = new AtlasTypesDef();
        getChanges(action).forEach(c -> typesDef.add(c.payload()));
        return typesDef;
    }

    /** Terraform-style summary, suitable for logs and review. */
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

    public String summary() {
        return String.format(Locale.ROOT, "Plan: %d to create, %d to update, %d to delete, %d unchanged; %d errors, %d warnings",
                getChanges(Action.CREATE).size(), getChanges(Action.UPDATE).size(),
                getChanges(Action.DELETE).size(), getChanges(Action.UNCHANGED).size(),
                getErrors().size(), getWarnings().size());
    }

    @Override
    public String toString() {
        return summary();
    }
}
