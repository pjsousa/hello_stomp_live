package com.example.demo.chat.dto;

public record SendMessageRequest(
        String target,
        String content
) {
}
