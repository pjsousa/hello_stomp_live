package com.example.demo.chat.dto;

public record ChatMessagePayload(
        String id,
        String timestamp,
        String sender,
        String target,
        String audience,
        String source,
        String content
) {
}
