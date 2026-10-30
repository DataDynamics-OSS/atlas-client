package io.datadynamics.atlas.client.exception;

/**
 * HTTP 409: the resource already exists (for example creating a type that is already defined).
 */
public class AtlasConflictException extends AtlasServerException {

    public AtlasConflictException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(statusCode, errorCode, errorMessage, method, url);
    }
}
