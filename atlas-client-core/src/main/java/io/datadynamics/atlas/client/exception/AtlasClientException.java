package io.datadynamics.atlas.client.exception;

/**
 * Base class of every exception thrown by the Atlas client.
 * <p>
 * Unchecked on purpose: Atlas calls fail for reasons callers rarely can recover from locally,
 * and forcing {@code try/catch} around every call is one of the things that makes the
 * official client painful to use.
 */
public class AtlasClientException extends RuntimeException {

    public AtlasClientException(String message) {
        super(message);
    }

    public AtlasClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
