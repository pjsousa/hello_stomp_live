package com.example.demo.websocket.dto.response;

import com.example.demo.websocket.dto.MessageType;

public record ChatMessageResponse(
        String timestamp,
        String sender,
        String message,
        MessageType type,
        String target) {
}
