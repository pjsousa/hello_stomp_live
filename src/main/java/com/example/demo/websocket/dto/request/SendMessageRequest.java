package com.example.demo.websocket.dto.request;

public record SendMessageRequest(String message, String target, String sessionId) {
}
