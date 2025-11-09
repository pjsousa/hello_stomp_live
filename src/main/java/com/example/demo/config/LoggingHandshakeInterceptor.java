package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.server.ServletServerHttpRequest;

class LoggingHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingHandshakeInterceptor.class);
    private static final String HANDSHAKE_ID_ATTR = LoggingHandshakeInterceptor.class.getName() + ".HANDSHAKE_ID";
    private static final String HANDSHAKE_START_ATTR = LoggingHandshakeInterceptor.class.getName() + ".HANDSHAKE_START";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String handshakeId = UUID.randomUUID().toString();
        Instant start = Instant.now();
        attributes.put(HANDSHAKE_ID_ATTR, handshakeId);
        attributes.put(HANDSHAKE_START_ATTR, start);
        if (request instanceof ServletServerHttpRequest servletRequest) {
            servletRequest.getServletRequest().setAttribute(HANDSHAKE_ID_ATTR, handshakeId);
            servletRequest.getServletRequest().setAttribute(HANDSHAKE_START_ATTR, start);
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        logger.info("STOMP handshake started id={} remoteAddress={} uri={} headers={}",
                handshakeId,
                remoteAddress,
                request.getURI(),
                request.getHeaders());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, @Nullable Exception exception) {
        Object handshakeId = null;
        Object startAttr = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            handshakeId = servletRequest.getServletRequest().getAttribute(HANDSHAKE_ID_ATTR);
            startAttr = servletRequest.getServletRequest().getAttribute(HANDSHAKE_START_ATTR);
            servletRequest.getServletRequest().removeAttribute(HANDSHAKE_ID_ATTR);
            servletRequest.getServletRequest().removeAttribute(HANDSHAKE_START_ATTR);
        }
        Instant start = startAttr instanceof Instant instant ? instant : null;
        Duration duration = start != null ? Duration.between(start, Instant.now()) : null;
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        logger.info("STOMP handshake completed id={} uri={} remoteAddress={} durationMs={} exception={}",
                handshakeId == null ? "unknown" : handshakeId,
                request.getURI(),
                remoteAddress,
                duration == null ? "n/a" : duration.toMillis(),
                exception == null ? "none" : exception.getMessage());
    }
}
