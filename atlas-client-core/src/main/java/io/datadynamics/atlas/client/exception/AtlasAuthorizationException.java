package io.datadynamics.atlas.client.exception;

/**
 * HTTP 403: the authenticated user is not allowed to perform the operation.
 */
public class AtlasAuthorizationException extends AtlasServerException {

    public AtlasAuthorizationException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(statusCode, errorCode, errorMessage, method, url);
    }
}
