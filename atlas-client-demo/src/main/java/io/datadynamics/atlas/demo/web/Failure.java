package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.client.exception.AtlasServerException;
import io.datadynamics.atlas.client.lineage.EntityRef;
import io.datadynamics.atlas.client.lineage.LineageException;
import io.datadynamics.atlas.client.typedef.TypeIssue;
import io.datadynamics.atlas.client.typedef.plan.TypePlanException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An exception as shown to the user: the client exception type matters, since the demo exists
 * to show how the client reports problems.
 *
 * @param status    HTTP status returned by Atlas, if any
 * @param errorCode Atlas error code such as {@code ATLAS-404-00-005}, if any
 * @param details   extra lines: missing lineage entities, type plan errors, ...
 */
public record Failure(String type, Integer status, String errorCode, String message, List<String> details) {

    public static Failure of(Throwable e) {
        List<String> details = List.of();
        if (e instanceof LineageException lineage && lineage.getMissingEntities() != null) {
            details = lineage.getMissingEntities().stream().map(EntityRef::toString).collect(Collectors.toList());
        } else if (e instanceof TypePlanException plan) {
            details = plan.getPlan().getIssues().stream().map(TypeIssue::toString).collect(Collectors.toList());
        }
        if (e instanceof AtlasServerException server) {
            return new Failure(e.getClass().getName(), server.getStatusCode(), server.getErrorCode(),
                    server.getErrorMessage() != null ? server.getErrorMessage() : e.getMessage(), details);
        }
        String message = e.getMessage() != null ? e.getMessage() : e.toString();
        return new Failure(e.getClass().getName(), null, null, message, details);
    }

    public String simpleType() {
        return type.substring(type.lastIndexOf('.') + 1);
    }
}
