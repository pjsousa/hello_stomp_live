Device-Level “Send Here” Flow Walkthrough

While SEND ME is a user preference shared across all sessions for an emoji, SEND HERE is scoped to a specific device connection. It governs which food the system should deliver when addressing the browser’s session id (for example, in scheduled device messages). This article explores how the `/app/settings/send-here` command keeps each tab’s device state aligned.

The SEND HERE button lives alongside the other controls in `app.js`. Clicking it cycles through available food emoji, updates the visible label, logs the event, and publishes the new value to `/app/settings/send-here`. Unlike SEND ME, this value is tied to the session id because multiple devices can impersonate the same animal but express different device-specific preferences.

```115:134:src/main/resources/static/app.js
elements.sendHereButton.addEventListener("click", () => {
    if (!foodOptions.length) {
        return;
    }
    sendHereIndex = (sendHereIndex + 1) % foodOptions.length;
    const value = foodOptions[sendHereIndex];
    elements.sendHereButton.textContent = value;
    logDiagnostic("SEND_HERE_CHANGE", value);
    publishValue("/app/settings/send-here", value);
});
```

On the server, `ChatController.updateSendHere` steers the request to `ChatService.handleSendHereUpdate`. Because SEND HERE is evaluated per session, the method does not need to look up the registered user emoji before acting; instead, it validates the payload and updates the calling session record in `RealtimeStateService`.

```146:157:src/main/java/com/example/demo/chat/service/ChatService.java
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
```

`RealtimeStateService.updateSendHere` asserts the chosen food belongs to the curated list and records it on the caller’s `SessionState`. The state object is keyed by the raw WebSocket session id, allowing the service to differentiate concurrent tabs even when they share the same avatar. Updating the state returns the canonical value (which may differ if validation coerces it), and the service immediately publishes a `SendHerePayload` back over the device control topic.

That device control channel (`/topic/device/{sessionId}/control`) is the same path used for session snapshots and error messages. Every browser listens to this topic using its current `sessionId`, so the payload is guaranteed to reach the originator even if it reconnects quickly. The payload includes the session id and the normalized value so the UI can keep its button text in sync with the server’s source of truth.

On the client, `handleControlPayload` checks for `payload.sessionId && payload.value` and invokes `applySendHere`. That method recalculates the index into the food options list and repaints the button. Because SEND HERE is session-local, there is no fan-out to other clients; only the originating tab needs to understand the update.

```418:510:src/main/resources/static/app.js
function applySnapshot(snapshot) {
    if (snapshot.sendHere) {
        applySendHere(snapshot.sendHere);
    }
    // other snapshot handling omitted
}

function applySendHere(value) {
    const idx = foodOptions.indexOf(value);
    sendHereIndex = idx >= 0 ? idx : 0;
    elements.sendHereButton.textContent = foodOptions[sendHereIndex];
}
```

The snapshot path above ensures reconnecting sessions recover the persisted SEND HERE value immediately, while the direct payload path catches live updates. The same mechanism powers server-driven changes: when scheduled jobs emit device-targeted messages, they still honor the most recent `sessionState.sendHere()` retrieved from `RealtimeStateService`. In short, `/app/settings/send-here` maintains a tight feedback loop between a tab’s UI control, its session-scoped state on the server, and any system behavior that references that preference.

