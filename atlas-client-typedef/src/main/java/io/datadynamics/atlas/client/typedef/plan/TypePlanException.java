package io.datadynamics.atlas.client.typedef.plan;

import io.datadynamics.atlas.client.exception.AtlasClientException;
import io.datadynamics.atlas.client.typedef.TypeIssue;

import java.util.stream.Collectors;

/**
 * Thrown when applying a plan that contains errors. Nothing has been sent to Atlas.
 */
public class TypePlanException extends AtlasClientException {

    private final transient TypePlan plan;

    public TypePlanException(TypePlan plan) {
        super("Type plan has errors, nothing was applied:\n" + plan.getErrors().stream()
                .map(TypeIssue::toString)
                .collect(Collectors.joining("\n")));
        this.plan = plan;
    }

    public TypePlan getPlan() {
        return plan;
    }
}
