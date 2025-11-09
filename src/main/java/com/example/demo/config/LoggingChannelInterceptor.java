package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.Map;

class LoggingChannelInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingChannelInterceptor.class);
    private final String direction;

    LoggingChannelInterceptor(String direction) {
        this.direction = direction;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null) {
            StompCommand command = accessor.getCommand();
            if (command != null) {
                String sessionId = accessor.getSessionId();
                String destination = accessor.getDestination();
                Object nativeHeaders = message.getHeaders().get(SimpMessageHeaderAccessor.NATIVE_HEADERS);
                logger.info("STOMP {} command={} session={} destination={} nativeHeaders={}",
                        direction, command, sessionId, destination, nativeHeaders);
            }
        }
        return message;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null) {
            StompCommand command = accessor.getCommand();
            if (command != null && ex != null) {
                logger.warn("STOMP {} command={} session={} failed to send: {}", direction, accessor.getSessionId(), command, ex.getMessage(), ex);
            }
        }
    }
}
