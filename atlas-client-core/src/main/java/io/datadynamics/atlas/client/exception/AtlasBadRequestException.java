package io.datadynamics.atlas.client.exception;

/**
 * HTTP 400: the request was rejected, typically an invalid type definition or attribute value.
 */
public class AtlasBadRequestException extends AtlasServerException {

    public AtlasBadRequestException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(statusCode, errorCode, errorMessage, method, url);
    }
}
