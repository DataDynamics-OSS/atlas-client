package io.datadynamics.atlas.client.exception;

/**
 * The request could not be completed at the network level (connection refused, timeout,
 * no active server in an HA setup, ...).
 */
public class AtlasConnectionException extends AtlasClientException {

    public AtlasConnectionException(String message) {
        super(message);
    }

    public AtlasConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
