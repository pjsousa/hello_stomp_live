package com.example.demo.websocket.controller;

import com.example.demo.websocket.dto.MessageType;
import com.example.demo.websocket.dto.request.SendMessageRequest;
import com.example.demo.websocket.dto.response.ChatMessageResponse;
import com.example.demo.websocket.dto.response.ErrorResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

@Controller
public class MessageStompController {

    private static final Logger log = LoggerFactory.getLogger(MessageStompController.class);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    private final SimpMessagingTemplate messagingTemplate;

    public MessageStompController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/message/send")
    public void send(SendMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("Message send request received: target={}, session={}", request.target(), sessionId);

        if (!StringUtils.hasText(request.message())) {
            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/errors",
                    ErrorResponse.validation("Message payload must not be empty", "message"));
            return;
        }

        String sender = StringUtils.hasText(request.sessionId()) ? request.sessionId() : sessionId;
        String timestamp = TIME_FORMATTER.format(Instant.now());

        ChatMessageResponse response = new ChatMessageResponse(
                timestamp,
                sender,
                request.message(),
                MessageType.USER_MESSAGE,
                StringUtils.hasText(request.target()) ? request.target() : "EVERYONE");

        if ("EVERYONE".equalsIgnoreCase(request.target()) || !StringUtils.hasText(request.target())) {
            messagingTemplate.convertAndSend("/topic/broadcast", response);
        } else {
            log.info("Targeted messaging is not yet implemented for target={}", request.target());
            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/errors",
                    ErrorResponse.notImplemented("Targeted messaging will be delivered in a future iteration."));
        }
    }
}
