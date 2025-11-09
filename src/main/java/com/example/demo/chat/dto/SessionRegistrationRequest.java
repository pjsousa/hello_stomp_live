package com.example.demo.chat.dto;

public record SessionRegistrationRequest(
        String me,
        String sendMe,
        String sendHere
) {
}
