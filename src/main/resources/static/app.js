const EVERYONE = "EVERYONE";
const DEFAULT_ANIMALS = ["🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯"];
const DEFAULT_FOODS = ["🍎", "🍌", "🍇", "🍕", "🍔", "🍣", "🍩", "🍰", "🥐", "🍪"];

let animalOptions = [...DEFAULT_ANIMALS];
let foodOptions = [...DEFAULT_FOODS];

const elements = {
    meSelect: document.getElementById("meSelect"),
    sendToButton: document.getElementById("sendToButton"),
    sendMeSelect: document.getElementById("sendMeSelect"),
    sendHereSelect: document.getElementById("sendHereSelect"),
    sendUsSelect: document.getElementById("sendUsSelect"),
    messageButton: document.getElementById("messageButton"),
    sendButton: document.getElementById("sendButton"),
    messagesPane: document.getElementById("messagesPane"),
    errorBox: document.getElementById("errorBox"),
    connectionStatus: document.getElementById("connectionStatus")
};

let stompClient = null;
let sessionId = null;
let currentMe = animalOptions[0];
let messageIndex = 0;
let sendTargets = [EVERYONE];
let sendTargetIndex = 0;
let onlineUsers = [];
let messages = [];
const seenMessageIds = new Set();

let suppressSendMe = false;
let suppressSendHere = false;
let suppressSendUs = false;

const subscriptions = {
    broadcast: null,
    online: null,
    globalSettings: null,
    userMessages: null,
    userSettings: null,
    deviceMessages: null,
    deviceControl: null
};

function init() {
    populateSelect(elements.meSelect, animalOptions);
    populateSelect(elements.sendMeSelect, foodOptions);
    populateSelect(elements.sendHereSelect, foodOptions);
    populateSelect(elements.sendUsSelect, foodOptions);

    elements.meSelect.value = currentMe;
    elements.sendMeSelect.value = foodOptions[0];
    elements.sendHereSelect.value = foodOptions[1] ?? foodOptions[0];
    elements.sendUsSelect.value = foodOptions[2] ?? foodOptions[0];
    elements.messageButton.textContent = foodOptions[0];

    registerListeners();
    connect();
    updateSendToTargets();
    setConnectionStatus("Connecting…", "pending");
}

function populateSelect(selectElement, options) {
    selectElement.innerHTML = "";
    options.forEach(value => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        selectElement.appendChild(option);
    });
}

function registerListeners() {
    elements.meSelect.addEventListener("change", () => {
        currentMe = elements.meSelect.value;
        subscribeUserTopics();
        registerSession();
        updateSendToTargets();
    });

    elements.sendToButton.addEventListener("click", () => {
        if (sendTargets.length === 0) {
            return;
        }
        sendTargetIndex = (sendTargetIndex + 1) % sendTargets.length;
        elements.sendToButton.textContent = sendTargets[sendTargetIndex];
    });

    elements.sendMeSelect.addEventListener("change", () => {
        if (suppressSendMe) {
            return;
        }
        publishValue("/app/settings/send-me", elements.sendMeSelect.value);
    });

    elements.sendHereSelect.addEventListener("change", () => {
        if (suppressSendHere) {
            return;
        }
        publishValue("/app/settings/send-here", elements.sendHereSelect.value);
    });

    elements.sendUsSelect.addEventListener("change", () => {
        if (suppressSendUs) {
            return;
        }
        publishValue("/app/settings/send-us", elements.sendUsSelect.value);
    });

    elements.messageButton.addEventListener("click", () => {
        messageIndex = (messageIndex + 1) % foodOptions.length;
        elements.messageButton.textContent = foodOptions[messageIndex];
    });

    elements.sendButton.addEventListener("click", () => {
        sendChatMessage();
    });
}

function connect() {
    stompClient = new window.Stomp.Client({
        webSocketFactory: () => new SockJS("/ws"),
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        debug: () => {
        }
    });

    stompClient.onConnect = frame => {
        sessionId = frame.headers["session"];
        setConnectionStatus("Connected", "ok");
        subscribeStaticTopics();
        subscribeDeviceTopics();
        subscribeUserTopics();
        registerSession();
    };

    stompClient.onStompError = frame => {
        showError(`Broker error: ${frame.headers["message"] ?? "unknown"}`);
    };

    stompClient.onWebSocketClose = () => {
        setConnectionStatus("Disconnected – retrying…", "warn");
        sessionId = null;
        resetSubscriptions();
    };

    stompClient.activate();
}

function resetSubscriptions() {
    Object.keys(subscriptions).forEach(key => {
        if (subscriptions[key]) {
            try {
                subscriptions[key].unsubscribe();
            } catch (e) {
                console.warn("Failed to unsubscribe", key, e);
            }
            subscriptions[key] = null;
        }
    });
}

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

    unsubscribe("globalSettings");
    subscriptions.globalSettings = stompClient.subscribe("/topic/settings/global", messageFrame => {
        const payload = JSON.parse(messageFrame.body);
        if (payload.value) {
            applySendUs(payload.value);
        }
    });
}

function subscribeDeviceTopics() {
    if (!sessionId) {
        return;
    }
    unsubscribe("deviceMessages");
    subscriptions.deviceMessages = stompClient.subscribe(`/topic/device/${sessionId}/messages`, messageFrame => {
        const payload = JSON.parse(messageFrame.body);
        appendMessage(payload);
    });

    unsubscribe("deviceControl");
    subscriptions.deviceControl = stompClient.subscribe(`/topic/device/${sessionId}/control`, messageFrame => {
        const payload = JSON.parse(messageFrame.body);
        handleControlPayload(payload);
    });
}

function subscribeUserTopics() {
    if (!stompClient || !stompClient.connected) {
        return;
    }
    unsubscribe("userMessages");
    unsubscribe("userSettings");
    subscriptions.userMessages = stompClient.subscribe(`/topic/user/${currentMe}/messages`, messageFrame => {
        const payload = JSON.parse(messageFrame.body);
        appendMessage(payload);
    });
    subscriptions.userSettings = stompClient.subscribe(`/topic/settings/user/${currentMe}`, messageFrame => {
        const payload = JSON.parse(messageFrame.body);
        if (payload.value) {
            applySendMe(payload.value);
        }
    });
}

function unsubscribe(key) {
    if (subscriptions[key]) {
        try {
            subscriptions[key].unsubscribe();
        } catch (e) {
            console.warn("Error unsubscribing", key, e);
        }
        subscriptions[key] = null;
    }
}

function registerSession() {
    if (!stompClient || !stompClient.connected) {
        return;
    }
    const payload = {
        me: currentMe,
        sendMe: elements.sendMeSelect.value,
        sendHere: elements.sendHereSelect.value
    };
    stompClient.publish({
        destination: "/app/session/register",
        body: JSON.stringify(payload)
    });
}

function publishValue(destination, value) {
    if (!stompClient || !stompClient.connected) {
        showError("Not connected to server.");
        return;
    }
    stompClient.publish({
        destination,
        body: JSON.stringify({value})
    });
}

function sendChatMessage() {
    if (!stompClient || !stompClient.connected) {
        showError("Cannot send message while disconnected.");
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
}

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
    clearError();
    if (Array.isArray(snapshot.animalOptions) && snapshot.animalOptions.length) {
        animalOptions = snapshot.animalOptions;
        populateSelect(elements.meSelect, animalOptions);
    }
    if (Array.isArray(snapshot.foodOptions) && snapshot.foodOptions.length) {
        foodOptions = snapshot.foodOptions;
        populateSelect(elements.sendMeSelect, foodOptions);
        populateSelect(elements.sendHereSelect, foodOptions);
        populateSelect(elements.sendUsSelect, foodOptions);
        messageIndex = 0;
        elements.messageButton.textContent = foodOptions[messageIndex];
    }

    if (snapshot.me) {
        currentMe = snapshot.me;
        elements.meSelect.value = currentMe;
        subscribeUserTopics();
    }
    if (snapshot.sendMe) {
        applySendMe(snapshot.sendMe);
    }
    if (snapshot.sendHere) {
        applySendHere(snapshot.sendHere);
    }
    if (snapshot.sendUs) {
        applySendUs(snapshot.sendUs);
    }
    if (Array.isArray(snapshot.onlineUsers)) {
        onlineUsers = snapshot.onlineUsers;
        updateSendToTargets();
    }
    if (Array.isArray(snapshot.recentMessages)) {
        messages = [];
        seenMessageIds.clear();
        snapshot.recentMessages.forEach(msg => appendMessage(msg));
    }
}

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

function renderMessages() {
    elements.messagesPane.innerHTML = "";
    messages.forEach(message => {
        const wrapper = document.createElement("div");
        wrapper.classList.add("message");
        if (message.source && message.source.startsWith("SYSTEM")) {
            wrapper.classList.add("system");
        }

        const meta = document.createElement("div");
        meta.classList.add("meta");
        const timestamp = formattedTime(message.timestamp);
        const sender = message.sender ?? "unknown";
        const target = message.audience === "EVERYONE" ? "🌐 Everyone" :
            message.audience === "DEVICE" ? "📱 This Device" : `🎯 ${message.target}`;
        meta.textContent = `${timestamp} • ${sender} • ${target}`;

        const content = document.createElement("div");
        content.classList.add("content");
        content.textContent = message.content ?? "";

        wrapper.appendChild(meta);
        wrapper.appendChild(content);
        elements.messagesPane.appendChild(wrapper);
    });
    elements.messagesPane.scrollTop = elements.messagesPane.scrollHeight;
}

function formattedTime(instantString) {
    if (!instantString) {
        return "--:--:--";
    }
    const date = new Date(instantString);
    if (Number.isNaN(date.getTime())) {
        return instantString;
    }
    return date.toLocaleTimeString([], {hour: "2-digit", minute: "2-digit", second: "2-digit"});
}

function updateSendToTargets() {
    const nextTargets = [EVERYONE, ...onlineUsers.filter(Boolean)];
    const currentTarget = sendTargets[sendTargetIndex] ?? EVERYONE;
    sendTargets = nextTargets.length ? nextTargets : [EVERYONE];
    sendTargetIndex = sendTargets.indexOf(currentTarget);
    if (sendTargetIndex === -1) {
        sendTargetIndex = 0;
    }
    elements.sendToButton.textContent = sendTargets[sendTargetIndex];
}

function applySendMe(value) {
    suppressSendMe = true;
    elements.sendMeSelect.value = value;
    suppressSendMe = false;
}

function applySendHere(value) {
    suppressSendHere = true;
    elements.sendHereSelect.value = value;
    suppressSendHere = false;
}

function applySendUs(value) {
    suppressSendUs = true;
    elements.sendUsSelect.value = value;
    suppressSendUs = false;
}

function showError(message) {
    elements.errorBox.textContent = message || "Unknown error";
}

function clearError() {
    elements.errorBox.textContent = "None";
}

function setConnectionStatus(text, status) {
    elements.connectionStatus.textContent = text;
    elements.connectionStatus.dataset.status = status;
}

init();
