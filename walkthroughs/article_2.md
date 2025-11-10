Session Registration Snapshot Walkthrough

The connection lifecycle prepares the socket, but the app remains anonymous until the browser registers its session. That registration step wires the chosen “ME” emoji to a STOMP session, synchronizes personalized settings, and ships a full state snapshot back to the device. This walkthrough follows the `/app/session/register` flow from the JavaScript publisher, through `ChatService.handleRegistration`, and finally back to the client-side `applySnapshot`.

On the front end, the `registerSession()` helper collects the current avatar along with the SEND ME and SEND HERE emoji choices displayed on the buttons. The payload is published over STOMP as JSON. Logging is built in so the diagnostics pane proves exactly when the request left the browser.

```307:321:src/main/resources/static/app.js
function registerSession() {
    if (!stompClient || !stompClient.connected) {
        return;
    }
    const payload = {
        me: currentMe,
        sendMe: elements.sendMeButton.textContent,
        sendHere: elements.sendHereButton.textContent
    };
    stompClient.publish({
        destination: "/app/session/register",
        body: JSON.stringify(payload)
    });
    logDiagnostic("SESSION_REGISTER_SENT", JSON.stringify(payload));
}
```

The STOMP frame lands in `ChatController.register`, annotated with `@MessageMapping("/session/register")`. The controller extracts the Spring-generated `simpSessionId` header so downstream logic knows which device is talking. From there the heavy lifting happens in `ChatService.handleRegistration`.

```46:75:src/main/java/com/example/demo/chat/service/ChatService.java
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
```

`RealtimeStateService.assignUser` guarantees that the session id is bound to a valid animal emoji. If the device previously impersonated another animal, the method detaches it so that online presence stays accurate. Optional SEND HERE and SEND ME values are validated and persisted next. The `recentMessagesFor` helper plucks the last few broadcasts, user-targeted, and device-targeted messages pertinent to this participant, ensuring that a reconnecting browser instantly sees context.

`sendSnapshot` packages the consolidated state into a `SessionSnapshot` record and pushes it over the device control topic, `/topic/device/{sessionId}/control`. Because that topic name includes the volatile session id, only the registering browser receives the payload. The server also emits a `SendMePayload` over `/topic/settings/user/{emoji}` so any other active tabs with the same avatar refresh their button. Finally, `broadcastOnlineUsers` republishes the roster so everyone’s SEND TO picker reflects the new arrival.

Back on the client, `handleControlPayload` routes the snapshot into `applySnapshot`. This method replaces the animal and food option arrays, realigns button indices, refreshes the online user list, and rehydrates the message timeline. Crucially, it resubscribes user topics after loading the authoritative avatar to ensure subsequent messages land on the correct per-user channel.

```353:432:src/main/resources/static/app.js
function handleControlPayload(payload) {
    if (payload == null || typeof payload !== "object") {
        return;
    }

    if (payload.message) {
        showError(payload.message);
        return;
    }

    if (payload.sessionId && payload.value) {
        applySendHere(payload.value);
        return;
    }

    if (payload.sessionId && payload.me) {
        applySnapshot(payload);
    }
}

function applySnapshot(snapshot) {
    if (Array.isArray(snapshot.animalOptions) && snapshot.animalOptions.length) {
        animalOptions = snapshot.animalOptions;
        const idx = animalOptions.indexOf(currentMe);
        meIndex = idx >= 0 ? idx : 0;
        currentMe = animalOptions[meIndex];
        elements.meButton.textContent = currentMe;
        subscribeUserTopics();
    }
    // food option synchronization, send* updates, online user refresh, and recent message replay follow
}
```

With registration complete, the browser possesses a synchronized view of system state, the server associates the session with a user identity, and all other participants learn about the new presence. Every subsequent STOMP interaction assumes this snapshot succeeded, which is why it is the first application command emitted once the connection comes online.

