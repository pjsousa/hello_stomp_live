package com.example.demo.chat.dto;

import java.util.List;

public record SessionSnapshot(
        String sessionId,
        String me,
        String sendMe,
        String sendHere,
        String sendUs,
        List<String> onlineUsers,
        List<ChatMessagePayload> recentMessages,
        List<String> animalOptions,
        List<String> foodOptions
) {
}
