package io.datadynamics.atlas.client.exception;

/**
 * HTTP 404: the requested type, entity or resource does not exist.
 */
public class AtlasNotFoundException extends AtlasServerException {

    public AtlasNotFoundException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(statusCode, errorCode, errorMessage, method, url);
    }
}
