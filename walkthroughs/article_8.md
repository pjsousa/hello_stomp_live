app.js Client Orchestration Walkthrough

The static STOMP demo lives and breathes through `src/main/resources/static/app.js`. This single script initializes the UI, shepherds the WebSocket lifecycle, and keeps browser state in sync with the Spring backend. This walkthrough highlights the major responsibilities and the code paths that implement them.

Initialization starts with the `init()` function, invoked immediately at the bottom of the file. It seeds button labels, registers DOM listeners, kicks off the STOMP connection, and sets the initial connection status. By capturing references to all HTML elements up front, the script avoids repeated DOM lookups as state changes.

```66:144:src/main/resources/static/app.js
function init() {
    logDiagnostic("INIT", `Origin=${window.location.origin}`);
    currentMe = animalOptions[meIndex] ?? animalOptions[0];
    elements.meButton.textContent = currentMe;
    elements.sendMeButton.textContent = foodOptions[sendMeIndex] ?? foodOptions[0];
    elements.sendHereButton.textContent = foodOptions[sendHereIndex] ?? foodOptions[0];
    elements.sendUsButton.textContent = foodOptions[sendUsIndex] ?? foodOptions[0];
    elements.messageButton.textContent = foodOptions[0];

    registerListeners();
    connect();
    updateSendToTargets();
    setConnectionStatus("Connecting…", "pending");
}

function registerListeners() {
    elements.meButton.addEventListener("click", () => {
        meIndex = (meIndex + 1) % animalOptions.length;
        currentMe = animalOptions[meIndex];
        elements.meButton.textContent = currentMe;
        subscribeUserTopics();
        registerSession();
        updateSendToTargets();
        logDiagnostic("ME_CHANGED", currentMe);
    });
    // other button listeners cycle food emoji, publish settings, and send messages
}
```

`connect()` encapsulates the STOMP lifecycle. It dynamically picks `wss://` or `ws://`, enables automatic reconnects, and binds rich event handlers. On successful connection the browser stores the assigned session id, subscribes to shared and per-session topics, and immediately registers itself with the backend.

```147:211:src/main/resources/static/app.js
function connect() {
    const StompNS = window.StompJs ?? window.Stomp;
    if (!StompNS || !StompNS.Client) {
        logDiagnostic("STOMP_INIT_ERROR", "STOMP library not found on window");
        showError("STOMP library failed to load.");
        return;
    }
    stompClient = new StompNS.Client({
        brokerURL: `${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}/ws`,
        reconnectDelay: 5000,
        heartbeatIncoming: 0,
        heartbeatOutgoing: 0,
        debug: message => {
            if (message && !message.includes("PING")) {
                logDiagnostic("STOMP_DEBUG", message);
            }
        }
    });

    stompClient.onConnect = frame => {
        sessionId = frame.headers["session"];
        setConnectionStatus("Connected", "ok");
        logDiagnostic("STOMP_CONNECTED", JSON.stringify(frame.headers));
        subscribeStaticTopics();
        subscribeDeviceTopics();
        subscribeUserTopics();
        registerSession();
    };
    // error, disconnect, and unhandled handlers also log diagnostics
    stompClient.activate();
}
```

Subscriptions are organized into helper functions that manage specific categories of topics. Static topics cover global broadcasts, presence, and SEND US updates. Device topics rely on the negotiated session id to pipe control messages and device-targeted chat. User topics follow the currently selected avatar, so cycling ME tears down and re-establishes subscriptions automatically.

```229:298:src/main/resources/static/app.js
function subscribeStaticTopics() {
    unsubscribe("broadcast");
    subscriptions.broadcast = stompClient.subscribe("/topic/messages", messageFrame => {
        const payload = JSON.parse(messageFrame.body);
        appendMessage(payload);
    });
    unsubscribe("online");
    subscriptions.online = stompClient.subscribe("/topic/online", messageFrame => {
        const payload = JSON.parse(messageFrame.body);
        if (Array.isArray(payload.users)) {
            onlineUsers = payload.users;
            updateSendToTargets();
        }
    });
    subscriptions.globalSettings = stompClient.subscribe("/topic/settings/global", messageFrame => {
        const payload = JSON.parse(messageFrame.body);
        if (payload.value) {
            applySendUs(payload.value);
        }
    });
}
```

Publishing commands back to the server happens through `registerSession()`, `publishValue()`, and `sendChatMessage()`. Each guard against a disconnected client and log the exact payload sent. This design makes the diagnostics pane a faithful transcript of outbound STOMP frames and simplifies debugging.

```307:352:src/main/resources/static/app.js
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
// publishValue and sendChatMessage follow the same defensive pattern
```

Incoming control payloads flow through `handleControlPayload()`, which routes errors, per-session updates, and full `SessionSnapshot` objects. `applySnapshot()` synchronizes emoji options, button indices, presence, and cached messages, while the `applySend*` helpers adjust individual settings. Message delivery uses `appendMessage()` and `renderMessages()` to maintain a de-duplicated, capped list of recent events.

Finally, the diagnostics subsystem (`logDiagnostic()`, `setConnectionStatus()`) provides immediate feedback. Every noteworthy action—including STOMP debug events, status changes, and SEND TO recalculations—writes to both the on-page log and the developer console, making the client transparent during troubleshooting.

In summary, `app.js` is a compact client orchestrator: it initializes UI state, manages a resilient STOMP session, publishes user intent, reacts to server pushes, and keeps diagnostics flowing. The surrounding HTML and CSS supply structure and styling, but this script supplies the intelligence that turns the demo into a responsive real-time chat experience.

