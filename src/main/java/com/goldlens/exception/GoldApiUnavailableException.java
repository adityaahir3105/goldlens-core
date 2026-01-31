package com.goldlens.exception;

import org.springframework.http.HttpStatus;

public class GoldApiUnavailableException extends RuntimeException {

    private final int httpStatus;
    private final String errorType;
    private final String requestId;

    public GoldApiUnavailableException(String message, int httpStatus, String errorType, String requestId) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorType = errorType;
        this.requestId = requestId;
    }

    public GoldApiUnavailableException(String message, int httpStatus, String errorType, String requestId, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorType = errorType;
        this.requestId = requestId;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getRequestId() {
        return requestId;
    }

    public boolean isRateLimited() {
        return httpStatus == 429 || "RATE_LIMITED".equals(errorType);
    }

    public boolean isServerError() {
        return httpStatus >= 500;
    }

    public boolean isForbidden() {
        return httpStatus == 403;
    }

    public HttpStatus getRecommendedResponseStatus() {
        if (isRateLimited()) {
            return HttpStatus.SERVICE_UNAVAILABLE; // 503
        }
        return HttpStatus.BAD_GATEWAY; // 502 for 403, 5xx errors
    }
}
