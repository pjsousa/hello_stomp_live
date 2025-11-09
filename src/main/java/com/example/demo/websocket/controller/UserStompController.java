package com.example.demo.websocket.controller;

import com.example.demo.websocket.dto.request.RegisterUserRequest;
import com.example.demo.websocket.dto.request.UnregisterUserRequest;
import com.example.demo.websocket.dto.response.MessageHistoryResponse;
import com.example.demo.websocket.dto.response.OnlineUsersResponse;
import com.example.demo.websocket.dto.response.OnlineUsersResponse.OnlineUserSummary;
import com.example.demo.websocket.dto.response.ErrorResponse;
import java.time.Instant;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class UserStompController {

    private static final Logger log = LoggerFactory.getLogger(UserStompController.class);

    private final SimpMessagingTemplate messagingTemplate;

    public UserStompController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/user/register")
    public void register(RegisterUserRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("Register request received: emoji={}, deviceId={}, session={}",
                request.emoji(), request.deviceId(), sessionId);

        OnlineUserSummary summary = new OnlineUserSummary(request.emoji(), sessionId);
        OnlineUsersResponse usersResponse = new OnlineUsersResponse(
                Collections.singletonList(summary),
                Instant.now().toEpochMilli());
        messagingTemplate.convertAndSend("/topic/users", usersResponse);

        MessageHistoryResponse historyResponse = new MessageHistoryResponse(Collections.emptyList(), 0);
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/history", historyResponse);
    }

    @MessageMapping("/user/unregister")
    public void unregister(UnregisterUserRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("Unregister request received: sessionId={}, payloadSessionId={}", sessionId, request.sessionId());

        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/errors",
                ErrorResponse.notImplemented("Explicit unregister flow will be implemented in a future iteration."));
    }
}
