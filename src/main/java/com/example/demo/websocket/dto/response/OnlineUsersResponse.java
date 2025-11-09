package com.example.demo.websocket.dto.response;

import java.util.List;

public record OnlineUsersResponse(List<OnlineUserSummary> users, long timestamp) {

    public record OnlineUserSummary(String emoji, String sessionId) {
    }
}
