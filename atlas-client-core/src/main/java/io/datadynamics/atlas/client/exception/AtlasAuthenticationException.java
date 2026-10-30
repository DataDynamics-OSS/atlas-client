package io.datadynamics.atlas.client.exception;

/**
 * HTTP 401: credentials are missing or invalid.
 */
public class AtlasAuthenticationException extends AtlasServerException {

    public AtlasAuthenticationException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(statusCode, errorCode, errorMessage, method, url);
    }
}
