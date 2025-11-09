package com.example.demo.chat.service;

import com.example.demo.chat.AppConstants;
import com.example.demo.chat.model.ChatMessage;
import com.example.demo.chat.model.MessageAudience;
import com.example.demo.chat.model.MessageSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class RealtimeStateService {

    private static final int HISTORY_LIMIT = 200;

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final Map<String, UserState> users = new ConcurrentHashMap<>();
    private final ArrayDeque<ChatMessage> history = new ArrayDeque<>();
    private final ReadWriteLock historyLock = new ReentrantReadWriteLock();
    private volatile String sendUsValue = AppConstants.DEFAULT_SEND_US;

    public SessionState ensureSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> new SessionState(id, null, AppConstants.DEFAULT_SEND_HERE, Instant.now()));
    }

    public synchronized SessionState assignUser(String sessionId, String userEmoji) {
        validateAnimal(userEmoji);
        SessionState session = ensureSession(sessionId);
        String previousUser = session.userEmoji;
        if (Objects.equals(previousUser, userEmoji)) {
            return session;
        }

        if (previousUser != null) {
            UserState previous = users.get(previousUser);
            if (previous != null) {
                previous.sessionIds.remove(sessionId);
                if (previous.sessionIds.isEmpty()) {
                    users.remove(previousUser);
                }
            }
        }

        UserState userState = users.computeIfAbsent(userEmoji, key -> new UserState(key, AppConstants.DEFAULT_SEND_ME));
        userState.sessionIds.add(sessionId);
        session.userEmoji = userEmoji;
        if (session.sendHere == null) {
            session.sendHere = AppConstants.DEFAULT_SEND_HERE;
        }
        return session;
    }

    public synchronized SessionState updateSendHere(String sessionId, String value) {
        validateFood(value);
        SessionState session = ensureSession(sessionId);
        session.sendHere = value;
        return session;
    }

    public synchronized UserState updateSendMe(String userEmoji, String value) {
        validateAnimal(userEmoji);
        validateFood(value);
        UserState userState = users.computeIfAbsent(userEmoji, key -> new UserState(key, AppConstants.DEFAULT_SEND_ME));
        userState.sendMe = value;
        return userState;
    }

    public synchronized String updateSendUs(String value) {
        validateFood(value);
        sendUsValue = value;
        return sendUsValue;
    }

    public String currentSendUs() {
        return sendUsValue;
    }

    public Optional<SessionState> findSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public Optional<UserState> findUser(String userEmoji) {
        return Optional.ofNullable(users.get(userEmoji));
    }

    public synchronized void removeSession(String sessionId) {
        SessionState removed = sessions.remove(sessionId);
        if (removed != null && removed.userEmoji != null) {
            UserState userState = users.get(removed.userEmoji);
            if (userState != null) {
                userState.sessionIds.remove(sessionId);
                if (userState.sessionIds.isEmpty()) {
                    users.remove(removed.userEmoji);
                }
            }
        }
    }

    public List<String> onlineUsers() {
        List<String> result = new ArrayList<>();
        for (String emoji : AppConstants.ANIMAL_EMOJIS) {
            if (users.containsKey(emoji)) {
                result.add(emoji);
            }
        }
        return result;
    }

    public List<String> sessionsForUser(String userEmoji) {
        UserState state = users.get(userEmoji);
        if (state == null) {
            return List.of();
        }
        return List.copyOf(state.sessionIds);
    }

    public Collection<UserState> userStates() {
        return List.copyOf(users.values());
    }

    public Collection<SessionState> sessionStates() {
        return List.copyOf(sessions.values());
    }

    public void appendMessage(ChatMessage message) {
        historyLock.writeLock().lock();
        try {
            history.addLast(message);
            while (history.size() > HISTORY_LIMIT) {
                history.removeFirst();
            }
        } finally {
            historyLock.writeLock().unlock();
        }
    }

    public List<ChatMessage> recentMessagesFor(String userEmoji, String sessionId, int limit) {
        historyLock.readLock().lock();
        try {
            List<ChatMessage> result = new ArrayList<>();
            Iterator<ChatMessage> descending = history.descendingIterator();
            while (descending.hasNext() && result.size() < limit) {
                ChatMessage message = descending.next();
                if (isRelevant(message, userEmoji, sessionId)) {
                    result.add(message);
                }
            }
            Collections.reverse(result);
            return result;
        } finally {
            historyLock.readLock().unlock();
        }
    }

    private boolean isRelevant(ChatMessage message, String userEmoji, String sessionId) {
        if (message.audience() == MessageAudience.EVERYONE) {
            return true;
        }
        if (message.audience() == MessageAudience.USER) {
            if (userEmoji == null) {
                return false;
            }
            if (Objects.equals(message.targetUser(), userEmoji)) {
                return true;
            }
            return Objects.equals(message.sender(), userEmoji);
        }
        if (message.audience() == MessageAudience.DEVICE) {
            return Objects.equals(message.targetSession(), sessionId);
        }
        return false;
    }

    private void validateAnimal(String emoji) {
        if (emoji == null || !AppConstants.ANIMAL_EMOJIS.contains(emoji)) {
            throw new IllegalArgumentException("Unknown animal emoji: " + emoji);
        }
    }

    private void validateFood(String emoji) {
        if (emoji == null || !AppConstants.FOOD_EMOJIS.contains(emoji)) {
            throw new IllegalArgumentException("Unknown food emoji: " + emoji);
        }
    }

    public static final class SessionState {
        private final String sessionId;
        private final Instant connectedAt;
        private volatile String userEmoji;
        private volatile String sendHere;

        private SessionState(String sessionId, String userEmoji, String sendHere, Instant connectedAt) {
            this.sessionId = sessionId;
            this.userEmoji = userEmoji;
            this.sendHere = sendHere;
            this.connectedAt = connectedAt;
        }

        public String sessionId() {
            return sessionId;
        }

        public String userEmoji() {
            return userEmoji;
        }

        public String sendHere() {
            return sendHere;
        }

        public Instant connectedAt() {
            return connectedAt;
        }
    }

    public static final class UserState {
        private final String emoji;
        private volatile String sendMe;
        private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

        private UserState(String emoji, String sendMe) {
            this.emoji = emoji;
            this.sendMe = sendMe;
        }

        public String emoji() {
            return emoji;
        }

        public String sendMe() {
            return sendMe;
        }

        public Set<String> sessionIds() {
            return sessionIds;
        }
    }
}
