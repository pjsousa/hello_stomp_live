package com.example.demo.chat.controller;

import com.example.demo.chat.dto.SendMessageRequest;
import com.example.demo.chat.dto.SessionRegistrationRequest;
import com.example.demo.chat.dto.ValueUpdateRequest;
import com.example.demo.chat.service.ChatService;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/session/register")
    public void register(@Header("simpSessionId") String sessionId, SessionRegistrationRequest request) {
        chatService.handleRegistration(sessionId, request);
    }

    @MessageMapping("/message/send")
    public void sendMessage(@Header("simpSessionId") String sessionId, SendMessageRequest request) {
        chatService.handleSendMessage(sessionId, request);
    }

    @MessageMapping("/settings/send-us")
    public void updateSendUs(@Header("simpSessionId") String sessionId, ValueUpdateRequest request) {
        chatService.handleSendUsUpdate(sessionId, request);
    }

    @MessageMapping("/settings/send-me")
    public void updateSendMe(@Header("simpSessionId") String sessionId, ValueUpdateRequest request) {
        chatService.handleSendMeUpdate(sessionId, request);
    }

    @MessageMapping("/settings/send-here")
    public void updateSendHere(@Header("simpSessionId") String sessionId, ValueUpdateRequest request) {
        chatService.handleSendHereUpdate(sessionId, request);
    }
}
