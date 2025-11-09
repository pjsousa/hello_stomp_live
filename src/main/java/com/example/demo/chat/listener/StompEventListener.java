package com.example.demo.chat.listener;

import com.example.demo.chat.service.ChatService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class StompEventListener {

    private final ChatService chatService;

    public StompEventListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId != null) {
            chatService.handleDisconnect(sessionId);
        }
    }
}
