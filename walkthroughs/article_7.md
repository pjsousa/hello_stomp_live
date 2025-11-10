Online Users Synchronization Walkthrough

Keeping the SEND TO picker accurate requires real-time awareness of which animal emoji are currently online. The application accomplishes this with a dedicated `/topic/online` channel that the server updates whenever sessions register, disconnect, or have their identity reassigned. This walkthrough traces how online presence is computed, published, and consumed.

Presence updates begin in `ChatService`. Whenever a session registers successfully, `handleRegistration` calls `broadcastOnlineUsers()`. Likewise, `handleDisconnect` removes the session from `RealtimeStateService` and reuses the same broadcaster. Because the service handles SEND ME and SEND HERE updates without changing the roster, only events that could alter the set of active emojis trigger the broadcast.

```69:162:src/main/java/com/example/demo/chat/service/ChatService.java
private void broadcastOnlineUsers() {
    messagingTemplate.convertAndSend(TOPIC_ONLINE, new OnlineUsersPayload(stateService.onlineUsers()));
}

public void handleDisconnect(String sessionId) {
    stateService.removeSession(sessionId);
    broadcastOnlineUsers();
}
```

`RealtimeStateService.onlineUsers()` iterates through the curated animal list and includes only entries that currently have a `UserState`. Because each `UserState` maintains a set of session ids, a single emoji remains “online” as long as at least one tab is active. When the last session disconnects, `removeSession` prunes the `UserState`, and the next broadcast omits that emoji. This design prevents flickering if multiple tabs share the same identity.

On the client, `subscribeStaticTopics()` hooks into `/topic/online` during the connection lifecycle. When a payload arrives, `updateSendToTargets()` merges the received users with the local `EVERYONE` sentinel and refreshes the SEND TO button. The method also preserves the currently highlighted target whenever possible so users are not forced back to `EVERYONE` during roster churn.

```237:498:src/main/resources/static/app.js
subscriptions.online = stompClient.subscribe("/topic/online", messageFrame => {
    const payload = JSON.parse(messageFrame.body);
    if (Array.isArray(payload.users)) {
        onlineUsers = payload.users;
        updateSendToTargets();
    }
});

function updateSendToTargets() {
    const nextTargets = [EVERYONE, ...onlineUsers.filter(Boolean)];
    const currentTarget = sendTargets[sendTargetIndex] ?? EVERYONE;
    sendTargets = nextTargets.length ? nextTargets : [EVERYONE];
    sendTargetIndex = sendTargets.indexOf(currentTarget);
    if (sendTargetIndex === -1) {
        sendTargetIndex = 0;
    }
    elements.sendToButton.textContent = sendTargets[sendTargetIndex];
    logDiagnostic("SEND_TO_UPDATED", sendTargets.join(","));
}
```

Because STOMP subscriptions are re-established whenever the user changes their ME identity, the client always listens to a roster that matches its current avatar. If a user switches animals, `registerSession()` triggers a re-registration, the server removes the old emoji from the roster if no other sessions are attached, and a fresh broadcast advertises the new availability. Disconnects are similarly graceful: when a tab closes, Spring’s session lifecycle ultimately invokes `ChatService.handleDisconnect`, pruning the session and the associated user if necessary.

Presence information also appears in session snapshots. `SessionSnapshot.onlineUsers()` carries the latest roster so reconnecting clients can seed their UI before the next broadcast arrives. `applySnapshot` copies the array into `onlineUsers` and calls `updateSendToTargets()`, ensuring the SEND TO picker is accurate immediately after registration.

The net effect is a simple yet reliable presence system: the server maintains a deterministic list of active avatars, publishes it whenever meaningful changes occur, and the client recalculates its target list while preserving user intent. This synchronization makes targeted messaging intuitive—users only see the emojis who are actually online, and they can continue directing messages to the same recipient even as others join or leave the conversation.

