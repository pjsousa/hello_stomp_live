package com.example.demo.chat.model;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(
        String id,
        Instant timestamp,
        String sender,
        MessageAudience audience,
        MessageSource source,
        String content,
        String targetUser,
        String targetSession
) {

    public static ChatMessage broadcast(String sender, String content, MessageSource source) {
        return new ChatMessage(
                UUID.randomUUID().toString(),
                Instant.now(),
                sender,
                MessageAudience.EVERYONE,
                source,
                content,
                null,
                null
        );
    }

    public static ChatMessage userTargeted(String sender, String targetUser, String content, MessageSource source) {
        return new ChatMessage(
                UUID.randomUUID().toString(),
                Instant.now(),
                sender,
                MessageAudience.USER,
                source,
                content,
                targetUser,
                null
        );
    }

    public static ChatMessage deviceTargeted(String sender, String targetSession, String content, MessageSource source) {
        return new ChatMessage(
                UUID.randomUUID().toString(),
                Instant.now(),
                sender,
                MessageAudience.DEVICE,
                source,
                content,
                null,
                targetSession
        );
    }
}
