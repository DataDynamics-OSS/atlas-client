package io.datadynamics.atlas.client.exception;

/**
 * HTTP 502/503/504: the server is unavailable, passive (HA) or overloaded.
 */
public class AtlasUnavailableException extends AtlasServerException {

    public AtlasUnavailableException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(statusCode, errorCode, errorMessage, method, url);
    }
}
