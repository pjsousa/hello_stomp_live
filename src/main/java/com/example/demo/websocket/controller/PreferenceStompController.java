package com.example.demo.websocket.controller;

import com.example.demo.websocket.dto.PreferenceScope;
import com.example.demo.websocket.dto.PreferenceType;
import com.example.demo.websocket.dto.request.UpdateSendHereRequest;
import com.example.demo.websocket.dto.request.UpdateSendMeRequest;
import com.example.demo.websocket.dto.request.UpdateSendUsRequest;
import com.example.demo.websocket.dto.response.ErrorResponse;
import com.example.demo.websocket.dto.response.PreferenceUpdateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class PreferenceStompController {

    private static final Logger log = LoggerFactory.getLogger(PreferenceStompController.class);

    private final SimpMessagingTemplate messagingTemplate;

    public PreferenceStompController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/preference/sendus")
    public void updateSendUs(UpdateSendUsRequest request, SimpMessageHeaderAccessor headerAccessor) {
        log.info("SEND_US update received from session={} value={}", headerAccessor.getSessionId(), request.emoji());
        PreferenceUpdateResponse response = new PreferenceUpdateResponse(
                PreferenceType.SEND_US,
                request.emoji(),
                request.sessionId(),
                System.currentTimeMillis(),
                PreferenceScope.GLOBAL);

        messagingTemplate.convertAndSend("/topic/system/sendus", response);
    }

    @MessageMapping("/preference/sendme")
    public void updateSendMe(UpdateSendMeRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("SEND_ME update received: userId={}, session={}", request.userId(), sessionId);

        PreferenceUpdateResponse response = new PreferenceUpdateResponse(
                PreferenceType.SEND_ME,
                request.emoji(),
                request.sessionId(),
                System.currentTimeMillis(),
                PreferenceScope.USER);

        messagingTemplate.convertAndSendToUser(sessionId, "/queue/preferences/sendme", response);
    }

    @MessageMapping("/preference/sendhere")
    public void updateSendHere(UpdateSendHereRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("SEND_HERE update received: deviceId={}, session={}", request.deviceId(), sessionId);

        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/errors",
                ErrorResponse.notImplemented("Device preference synchronization will be implemented in a future iteration."));
    }
}
