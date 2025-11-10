Global “Send Us” Broadcasts Walkthrough

SEND US is the community-wide preference: a single emoji the whole system should share during periodic broadcasts. Updating it triggers an immediate push to every connected client and influences scheduled tasks that keep activity flowing even when users are idle. This walkthrough explains how `/app/settings/send-us` works alongside the cron-like schedulers inside `ChatService`.

Unlike SEND ME and SEND HERE, no UI button cycles SEND US on the client; instead, SEND US updates typically originate from system controls or administrative tooling. Still, `app.js` consumes the resulting broadcasts through the global settings subscription, keeping the SEND US button in sync if one were present in the UI. Snapshot handling also applies the value during reconnects.

On the server, `ChatController.updateSendUs` maps directly to `ChatService.handleSendUsUpdate`. The method validates the incoming payload and, if it passes, writes it to `RealtimeStateService` before broadcasting the new value.

```112:123:src/main/java/com/example/demo/chat/service/ChatService.java
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
```

`RealtimeStateService.updateSendUs` ensures the emoji lives in `AppConstants.FOOD_EMOJIS` and stores it in a volatile field. Because SEND US has no per-user or per-session sharding, the value is cheap to read whenever scheduled jobs need it. `sendSendUsUpdate` then publishes a `SendUsPayload` to `/topic/settings/global`, a topic every client subscribed to during connection setup.

The default schedulers amplify SEND US. `emitBroadcastSchedule` wakes up on a configurable interval (5 seconds initial delay, 20 seconds cadence by default). When sessions exist, it constructs a `ChatMessage` of type `SYSTEM_BROADCAST` using the current SEND US emoji and pushes it to `/topic/messages`. That means all clients—even those who never explicitly requested the value—see the emoji flow through the main timeline. Similarly, `emitUserSchedule` and `emitDeviceSchedule` reuse SEND ME and SEND HERE, ensuring each preference has an automated showcase.

```164:199:src/main/java/com/example/demo/chat/service/ChatService.java
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
```

From the client side, consuming SEND US updates happens inside `subscribeStaticTopics()`. The browser listens to `/topic/settings/global` and, upon receiving a payload, calls `applySendUs` to update the SEND US button. `applySnapshot` invokes the same helper during session registration, guaranteeing reconnecting clients reflect the current global choice immediately.

```247:515:src/main/resources/static/app.js
subscriptions.globalSettings = stompClient.subscribe("/topic/settings/global", messageFrame => {
    const payload = JSON.parse(messageFrame.body);
    if (payload.value) {
        applySendUs(payload.value);
    }
});

function applySendUs(value) {
    const idx = foodOptions.indexOf(value);
    sendUsIndex = idx >= 0 ? idx : 0;
    elements.sendUsButton.textContent = foodOptions[sendUsIndex];
}
```

Because SEND US influences system broadcasts, its state also shows up in session snapshots. When a device registers, `SessionSnapshot.sendUs()` carries the canonical value, and `applySnapshot` applies it before any later updates arrive. The combination of immediate publish, scheduled reuse, and snapshot hydration ensures the community-wide emoji remains consistent across every participant and across time.

