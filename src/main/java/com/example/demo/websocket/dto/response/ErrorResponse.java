package com.example.demo.websocket.dto.response;

public record ErrorResponse(
        String errorCode,
        String message,
        long timestamp,
        String field) {

    public static ErrorResponse notImplemented(String message) {
        return new ErrorResponse("NOT_IMPLEMENTED", message, System.currentTimeMillis(), null);
    }

    public static ErrorResponse validation(String message, String field) {
        return new ErrorResponse("VALIDATION_ERROR", message, System.currentTimeMillis(), field);
    }
}
