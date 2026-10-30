package io.datadynamics.atlas.client.exception;

/**
 * Atlas answered with a non-successful HTTP status.
 * <p>
 * Atlas error bodies look like {@code {"errorCode":"ATLAS-404-00-005","errorMessage":"..."}};
 * both values are exposed when present.
 */
public class AtlasServerException extends AtlasClientException {

    private final int statusCode;
    private final String errorCode;
    private final String errorMessage;
    private final String method;
    private final String url;

    public AtlasServerException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(buildMessage(statusCode, errorCode, errorMessage, method, url));
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.method = method;
        this.url = url;
    }

    private static String buildMessage(int statusCode, String errorCode, String errorMessage, String method, String url) {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(' ').append(url).append(" -> HTTP ").append(statusCode);
        if (errorCode != null) {
            sb.append(" [").append(errorCode).append(']');
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            sb.append(": ").append(errorMessage);
        }
        return sb.toString();
    }

    /**
     * Creates the most specific exception type for the given HTTP status.
     */
    public static AtlasServerException of(int statusCode, String errorCode, String errorMessage, String method, String url) {
        switch (statusCode) {
            case 400:
                return new AtlasBadRequestException(statusCode, errorCode, errorMessage, method, url);
            case 401:
                return new AtlasAuthenticationException(statusCode, errorCode, errorMessage, method, url);
            case 403:
                return new AtlasAuthorizationException(statusCode, errorCode, errorMessage, method, url);
            case 404:
                return new AtlasNotFoundException(statusCode, errorCode, errorMessage, method, url);
            case 409:
                return new AtlasConflictException(statusCode, errorCode, errorMessage, method, url);
            case 502:
            case 503:
            case 504:
                return new AtlasUnavailableException(statusCode, errorCode, errorMessage, method, url);
            default:
                return new AtlasServerException(statusCode, errorCode, errorMessage, method, url);
        }
    }

    public int getStatusCode() {
        return statusCode;
    }

    /** Atlas error code such as {@code ATLAS-404-00-005}, or {@code null} if the body had none. */
    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public boolean hasErrorCode(String code) {
        return code != null && code.equals(errorCode);
    }
}
