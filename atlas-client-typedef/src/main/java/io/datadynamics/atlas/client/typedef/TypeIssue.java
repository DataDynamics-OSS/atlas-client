package io.datadynamics.atlas.client.typedef;

/**
 * A problem found while validating or planning type definitions.
 * <p>
 * {@code ERROR}s would be rejected by Atlas (or would silently do something else than intended)
 * and block {@code apply}; {@code WARNING}s are informational.
 */
public record TypeIssue(Severity severity, String typeName, String message) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public static TypeIssue error(String typeName, String message) {
        return new TypeIssue(Severity.ERROR, typeName, message);
    }

    public static TypeIssue warning(String typeName, String message) {
        return new TypeIssue(Severity.WARNING, typeName, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        return severity + " " + typeName + ": " + message;
    }
}
