package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix;

public class SoftixClientException extends RuntimeException {
    private final Integer statusCode;
    private final String responseBody;

    public SoftixClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
        this.responseBody = null;
    }

    public SoftixClientException(String message) {
        super(message);
        this.statusCode = null;
        this.responseBody = null;
    }

    public SoftixClientException(String message, Integer statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
