package com.goldlens.exception;

public class GoldPricezParseException extends RuntimeException {

    private final String requestId;
    private final String rawResponse;

    public GoldPricezParseException(String message, String requestId, String rawResponse) {
        super(message);
        this.requestId = requestId;
        this.rawResponse = rawResponse;
    }

    public GoldPricezParseException(String message, String requestId, String rawResponse, Throwable cause) {
        super(message, cause);
        this.requestId = requestId;
        this.rawResponse = rawResponse;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
