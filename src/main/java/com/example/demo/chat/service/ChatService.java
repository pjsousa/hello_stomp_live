package com.example.demo.chat.service;

import com.example.demo.chat.AppConstants;
import com.example.demo.chat.dto.ChatMessagePayload;
import com.example.demo.chat.dto.ErrorPayload;
import com.example.demo.chat.dto.OnlineUsersPayload;
import com.example.demo.chat.dto.SendHerePayload;
import com.example.demo.chat.dto.SendMePayload;
import com.example.demo.chat.dto.SendMessageRequest;
import com.example.demo.chat.dto.SendUsPayload;
import com.example.demo.chat.dto.SessionRegistrationRequest;
import com.example.demo.chat.dto.SessionSnapshot;
import com.example.demo.chat.dto.ValueUpdateRequest;
import com.example.demo.chat.model.ChatMessage;
import com.example.demo.chat.model.MessageSource;
import com.example.demo.chat.service.RealtimeStateService.SessionState;
import com.example.demo.chat.service.RealtimeStateService.UserState;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class ChatService {

    private static final int RECENT_MESSAGE_LIMIT = 10;
    private static final String TOPIC_MESSAGES = "/topic/messages";
    private static final String TOPIC_ONLINE = "/topic/online";
    private static final String TOPIC_GLOBAL_SETTINGS = "/topic/settings/global";
    private static final String TEMPLATE_USER_MESSAGES = "/topic/user/%s/messages";
    private static final String TEMPLATE_USER_SETTINGS = "/topic/settings/user/%s";
    private static final String TEMPLATE_DEVICE_MESSAGES = "/topic/device/%s/messages";
    private static final String TEMPLATE_DEVICE_CONTROL = "/topic/device/%s/control";

    private final RealtimeStateService stateService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatService(RealtimeStateService stateService, SimpMessagingTemplate messagingTemplate) {
        this.stateService = stateService;
        this.messagingTemplate = messagingTemplate;
    }

    public void handleRegistration(String sessionId, SessionRegistrationRequest request) {
        try {
            if (!StringUtils.hasText(request.me())) {
                throw new IllegalArgumentException("ME selection is required");
            }
            SessionState sessionState = stateService.assignUser(sessionId, request.me());

            if (StringUtils.hasText(request.sendHere())) {
                stateService.updateSendHere(sessionId, request.sendHere());
            }
            if (StringUtils.hasText(request.sendMe())) {
                stateService.updateSendMe(sessionState.userEmoji(), request.sendMe());
            }

            SessionState updatedSession = stateService.findSession(sessionId).orElseThrow();
            UserState userState = stateService.findUser(updatedSession.userEmoji())
                    .orElseThrow(() -> new IllegalStateException("User state missing for emoji " + updatedSession.userEmoji()));

            List<ChatMessagePayload> recentMessages = stateService.recentMessagesFor(userState.emoji(), sessionId, RECENT_MESSAGE_LIMIT)
                    .stream()
                    .map(this::mapToPayload)
                    .toList();

            sendSnapshot(sessionId, updatedSession, userState, recentMessages);
            sendSendMeUpdate(userState.emoji(), userState.sendMe());
            broadcastOnlineUsers();
        } catch (IllegalArgumentException ex) {
            sendError(sessionId, ex.getMessage());
        }
    }

    public void handleSendMessage(String sessionId, SendMessageRequest request) {
        Optional<SessionState> sessionOpt = stateService.findSession(sessionId);
        if (sessionOpt.isEmpty() || !StringUtils.hasText(sessionOpt.get().userEmoji())) {
            sendError(sessionId, "Select a ME identity before sending messages.");
            return;
        }

        String sender = sessionOpt.get().userEmoji();
        String content = request.content();
        if (!AppConstants.FOOD_EMOJIS.contains(content)) {
            sendError(sessionId, "Message must be one of the available food emojis.");
            return;
        }

        String target = StringUtils.hasText(request.target()) ? request.target() : AppConstants.EVERYONE;

        if (AppConstants.EVERYONE.equals(target)) {
            ChatMessage broadcast = ChatMessage.broadcast(sender, content, MessageSource.USER_MESSAGE);
            stateService.appendMessage(broadcast);
            messagingTemplate.convertAndSend(TOPIC_MESSAGES, mapToPayload(broadcast));
            return;
        }

        if (!AppConstants.ANIMAL_EMOJIS.contains(target)) {
            sendError(sessionId, "Unknown SEND TO target: " + target);
            return;
        }

        ChatMessage message = ChatMessage.userTargeted(sender, target, content, MessageSource.USER_MESSAGE);
        stateService.appendMessage(message);

        messagingTemplate.convertAndSend(userMessagesDestination(target), mapToPayload(message));
        messagingTemplate.convertAndSend(userMessagesDestination(sender), mapToPayload(message));
    }

    public void handleSendUsUpdate(String sessionId, ValueUpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.value())) {
            sendError(sessionId, "SEND US value is required.");
            return;
        }
        try {
            String newValue = stateService.updateSendUs(request.value());
            sendSendUsUpdate(newValue);
        } catch (IllegalArgumentException ex) {
            sendError(sessionId, ex.getMessage());
        }
    }

    public void handleSendMeUpdate(String sessionId, ValueUpdateRequest request) {
        Optional<SessionState> sessionOpt = stateService.findSession(sessionId);
        if (sessionOpt.isEmpty() || !StringUtils.hasText(sessionOpt.get().userEmoji())) {
            sendError(sessionId, "Select a ME identity before updating SEND ME.");
            return;
        }

        if (request == null || !StringUtils.hasText(request.value())) {
            sendError(sessionId, "SEND ME value is required.");
            return;
        }

        String userEmoji = sessionOpt.get().userEmoji();
        try {
            UserState updated = stateService.updateSendMe(userEmoji, request.value());
            sendSendMeUpdate(updated.emoji(), updated.sendMe());
        } catch (IllegalArgumentException ex) {
            sendError(sessionId, ex.getMessage());
        }
    }

    public void handleSendHereUpdate(String sessionId, ValueUpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.value())) {
            sendError(sessionId, "SEND HERE value is required.");
            return;
        }
        try {
            SessionState sessionState = stateService.updateSendHere(sessionId, request.value());
            messagingTemplate.convertAndSend(deviceControlDestination(sessionId), new SendHerePayload(sessionId, sessionState.sendHere()));
        } catch (IllegalArgumentException ex) {
            sendError(sessionId, ex.getMessage());
        }
    }

    public void handleDisconnect(String sessionId) {
        stateService.removeSession(sessionId);
        broadcastOnlineUsers();
    }

    @Scheduled(initialDelayString = "${app.schedule.broadcast-initial-ms:5000}", fixedRateString = "${app.schedule.broadcast-ms:20000}")
    public void emitBroadcastSchedule() {
        if (stateService.sessionStates().isEmpty()) {
            return;
        }
        String content = stateService.currentSendUs();
        ChatMessage message = ChatMessage.broadcast("SYSTEM", content, MessageSource.SYSTEM_BROADCAST);
        stateService.appendMessage(message);
        messagingTemplate.convertAndSend(TOPIC_MESSAGES, mapToPayload(message));
    }

    @Scheduled(initialDelayString = "${app.schedule.user-initial-ms:7000}", fixedRateString = "${app.schedule.user-ms:25000}")
    public void emitUserSchedule() {
        var users = stateService.userStates();
        if (users.isEmpty()) {
            return;
        }
        for (UserState user : users) {
            ChatMessage message = ChatMessage.userTargeted("SYSTEM", user.emoji(), user.sendMe(), MessageSource.SYSTEM_USER_SCHEDULE);
            stateService.appendMessage(message);
            messagingTemplate.convertAndSend(userMessagesDestination(user.emoji()), mapToPayload(message));
        }
    }

    @Scheduled(initialDelayString = "${app.schedule.device-initial-ms:9000}", fixedRateString = "${app.schedule.device-ms:30000}")
    public void emitDeviceSchedule() {
        var sessions = stateService.sessionStates();
        if (sessions.isEmpty()) {
            return;
        }

        for (SessionState session : sessions) {
            ChatMessage message = ChatMessage.deviceTargeted("SYSTEM", session.sessionId(), session.sendHere(), MessageSource.SYSTEM_DEVICE_SCHEDULE);
            stateService.appendMessage(message);
            messagingTemplate.convertAndSend(deviceMessagesDestination(session.sessionId()), mapToPayload(message));
        }
    }

    private void sendSnapshot(String sessionId, SessionState sessionState, UserState userState, List<ChatMessagePayload> recentMessages) {
        SessionSnapshot snapshot = new SessionSnapshot(
                sessionId,
                userState.emoji(),
                userState.sendMe(),
                sessionState.sendHere(),
                stateService.currentSendUs(),
                stateService.onlineUsers(),
                recentMessages,
                AppConstants.ANIMAL_EMOJIS,
                AppConstants.FOOD_EMOJIS
        );
        messagingTemplate.convertAndSend(deviceControlDestination(sessionId), snapshot);
    }

    private void sendSendMeUpdate(String userEmoji, String value) {
        messagingTemplate.convertAndSend(userSettingsDestination(userEmoji), new SendMePayload(userEmoji, value));
    }

    private void sendSendUsUpdate(String value) {
        messagingTemplate.convertAndSend(TOPIC_GLOBAL_SETTINGS, new SendUsPayload(value));
    }

    private void broadcastOnlineUsers() {
        messagingTemplate.convertAndSend(TOPIC_ONLINE, new OnlineUsersPayload(stateService.onlineUsers()));
    }

    private void sendError(String sessionId, String message) {
        messagingTemplate.convertAndSend(deviceControlDestination(sessionId), new ErrorPayload(message));
    }

    private ChatMessagePayload mapToPayload(ChatMessage message) {
        String target = switch (message.audience()) {
            case EVERYONE -> AppConstants.EVERYONE;
            case USER -> message.targetUser();
            case DEVICE -> message.targetSession();
        };
        return new ChatMessagePayload(
                message.id(),
                message.timestamp().toString(),
                message.sender(),
                target,
                message.audience().name(),
                message.source().name(),
                message.content()
        );
    }

    private String userMessagesDestination(String userEmoji) {
        return TEMPLATE_USER_MESSAGES.formatted(userEmoji);
    }

    private String userSettingsDestination(String userEmoji) {
        return TEMPLATE_USER_SETTINGS.formatted(userEmoji);
    }

    private String deviceMessagesDestination(String sessionId) {
        return TEMPLATE_DEVICE_MESSAGES.formatted(sessionId);
    }

    private String deviceControlDestination(String sessionId) {
        return TEMPLATE_DEVICE_CONTROL.formatted(sessionId);
    }
}
