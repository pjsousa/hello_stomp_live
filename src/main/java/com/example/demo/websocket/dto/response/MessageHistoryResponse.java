package com.example.demo.websocket.dto.response;

import java.util.List;

public record MessageHistoryResponse(List<ChatMessageResponse> messages, int totalCount) {
}
