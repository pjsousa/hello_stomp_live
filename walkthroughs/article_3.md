Chat Message Routing Walkthrough

After a session is registered, the core activity is sending emoji messages. Each click on the SEND button launches a STOMP frame that the backend routes either to the global broadcast channel or to user-specific topics. This article examines how the client constructs outgoing messages, how the server validates and distributes them, and how the UI renders the conversation history.

On the front end, `sendChatMessage()` guards against disconnected sockets, gathers the current SEND TO target and message content (the selected food emoji), and publishes to `/app/message/send`. Because `updateSendToTargets()` always keeps the target array fresh with online users, this payload either points to the literal `"EVERYONE"` sentinel or to an animal emoji to be treated as a direct message.

```336:350:src/main/resources/static/app.js
function sendChatMessage() {
    if (!stompClient || !stompClient.connected) {
        showError("Cannot send message while disconnected.");
        logDiagnostic("SEND_FAILED", "Client not connected");
        return;
    }
    const payload = {
        target: sendTargets[sendTargetIndex] ?? EVERYONE,
        content: elements.messageButton.textContent
    };
    stompClient.publish({
        destination: "/app/message/send",
        body: JSON.stringify(payload)
    });
    logDiagnostic("MESSAGE_SENT", JSON.stringify(payload));
}
```

`ChatController.sendMessage` forwards the request to `ChatService.handleSendMessage`, passing along the `simpSessionId` header so the server can resolve the sender’s registered emoji. The service method performs three lines of defense before delivering anything: it verifies the session has already selected a ME identity, asserts that the content is one of the approved food emoji, and confirms that the target (if not EVERYONE) is a recognized animal.

```77:110:src/main/java/com/example/demo/chat/service/ChatService.java
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
```

Broadcast messages push to `/topic/messages`, the shared channel all clients subscribe to during connection setup. Targeted messages, however, fan out to two destinations: the recipient’s per-user feed and the sender’s own feed. The dual publish ensures both sides see the direct message in their timeline. Every outgoing message is persisted via `RealtimeStateService.appendMessage`, giving the service a rolling history that supports session snapshots and scheduled replays.

Under the hood, message construction leverages `ChatMessage`, a compact record with helpers for each audience type. Each helper sets the `MessageAudience` enum appropriately and fills either `targetUser` or `targetSession` so downstream logic can decide relevance.

```17:53:src/main/java/com/example/demo/chat/model/ChatMessage.java
public static ChatMessage broadcast(String sender, String content, MessageSource source) {
    return new ChatMessage(
            UUID.randomUUID().toString(),
            Instant.now(),
            sender,
            MessageAudience.EVERYONE,
            source,
            content,
            null,
            null
    );
}
// userTargeted and deviceTargeted variants follow the same pattern
```

When a `ChatMessagePayload` arrives in the browser—whether from the shared `/topic/messages`, a device feed, or the user-specific subscription—`appendMessage()` de-duplicates it using the message id, pushes it onto an in-memory array capped at 200 entries, and triggers a re-render. Rendering is straightforward but effective: the sender and emoji are shown on the left, a timestamp on the right, and system-originated messages get a CSS class for styling.

```435:475:src/main/resources/static/app.js
function appendMessage(payload) {
    if (!payload || !payload.id) {
        return;
    }
    if (seenMessageIds.has(payload.id)) {
        return;
    }
    seenMessageIds.add(payload.id);
    messages.push(payload);
    if (messages.length > 200) {
        const removed = messages.splice(0, messages.length - 200);
        removed.forEach(msg => seenMessageIds.delete(msg.id));
    }
    renderMessages();
}
```

The result is a messaging loop that enforces emoji hygiene, keeps history pruned, and ensures direct conversations are private but still visible to the sender. Because the same routing infrastructure powers system broadcasts and scheduled jobs, the chat timeline consistently displays activity regardless of origin—a foundation for the settings flows explored in the next articles.

