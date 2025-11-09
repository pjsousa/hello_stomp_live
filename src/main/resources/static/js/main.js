const animalEmojis = ["🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯"];
const foodEmojis = [
    "🍕", "🍔", "🍟", "🌭", "🍿",
    "🥓", "🥚", "🧇", "🥞", "🧈",
    "🍞", "🥐", "🥖", "🥨", "🥯",
    "🧀", "🍖", "🍗", "🥩", "🌮"
];

const meSelect = document.getElementById("meSelect");
const messageSelect = document.getElementById("messageSelect");
const sendToSelect = document.getElementById("sendToSelect");
const connectBtn = document.getElementById("connectBtn");
const disconnectBtn = document.getElementById("disconnectBtn");
const sendBtn = document.getElementById("sendBtn");
const statusLabel = document.getElementById("statusLabel");
const deviceIdInput = document.getElementById("deviceIdInput");
const messageList = document.getElementById("messageList");
const notificationList = document.getElementById("notificationList");
const errorList = document.getElementById("errorList");

let stompClient = null;
let stompSessionId = null;
let deviceId = null;

function populateSelectors() {
    animalEmojis.forEach((emoji, index) => {
        const option = document.createElement("option");
        option.value = emoji;
        option.textContent = `${emoji} Animal ${index + 1}`;
        meSelect.appendChild(option);
    });

    foodEmojis.forEach(emoji => {
        const option = document.createElement("option");
        option.value = emoji;
        option.textContent = emoji;
        messageSelect.appendChild(option);
    });
}

function generateDeviceId() {
    if (window.crypto && typeof window.crypto.randomUUID === "function") {
        return window.crypto.randomUUID();
    }
    return `device-${Math.random().toString(36).slice(2, 10)}`;
}

function setStatus(connected) {
    if (connected) {
        statusLabel.textContent = `Connected · session ${stompSessionId ?? "unknown"}`;
        statusLabel.classList.remove("disconnected");
        statusLabel.classList.add("connected");
        connectBtn.disabled = true;
        disconnectBtn.disabled = false;
        sendBtn.disabled = false;
    } else {
        statusLabel.textContent = "Disconnected";
        statusLabel.classList.remove("connected");
        statusLabel.classList.add("disconnected");
        connectBtn.disabled = false;
        disconnectBtn.disabled = true;
        sendBtn.disabled = true;
    }
}

function appendListItem(list, content, cssClass = "") {
    const li = document.createElement("li");
    li.textContent = content;
    if (cssClass) {
        li.classList.add(cssClass);
    }
    list.prepend(li);
    while (list.children.length > 12) {
        list.removeChild(list.lastChild);
    }
}

function renderChatMessage(message) {
    const li = document.createElement("li");
    const timestamp = document.createElement("span");
    timestamp.className = "timestamp";
    timestamp.textContent = message.timestamp ?? new Date().toLocaleTimeString();

    const sender = document.createElement("span");
    sender.className = "sender";
    sender.textContent = message.sender ?? "unknown";

    const payload = document.createElement("span");
    payload.textContent = `→ ${message.target ?? "EVERYONE"} · ${message.message ?? "?"} (${message.type})`;

    li.appendChild(timestamp);
    li.appendChild(sender);
    li.appendChild(payload);

    messageList.prepend(li);
    while (messageList.children.length > 25) {
        messageList.removeChild(messageList.lastChild);
    }
}

function updateOnlineUsers(users) {
    const previousSelection = sendToSelect.value;
    sendToSelect.innerHTML = "";

    const everyoneOption = document.createElement("option");
    everyoneOption.value = "EVERYONE";
    everyoneOption.textContent = "EVERYONE";
    sendToSelect.appendChild(everyoneOption);

    users.forEach(user => {
        if (!user.emoji) {
            return;
        }
        const option = document.createElement("option");
        option.value = user.emoji;
        option.textContent = `${user.emoji} (${user.sessionId?.slice(-6) ?? "session"})`;
        sendToSelect.appendChild(option);
    });

    if ([...sendToSelect.options].some(option => option.value === previousSelection)) {
        sendToSelect.value = previousSelection;
    }
}

function subscribeToDestinations() {
    stompClient.subscribe("/topic/broadcast", payload => {
        const message = JSON.parse(payload.body);
        renderChatMessage(message);
    });

    stompClient.subscribe("/topic/users", payload => {
        const body = JSON.parse(payload.body);
        updateOnlineUsers(body.users ?? []);
        appendListItem(notificationList, `User list updated (${(body.users ?? []).length} online)`);
    });

    stompClient.subscribe("/topic/system/sendus", payload => {
        const body = JSON.parse(payload.body);
        appendListItem(notificationList, `SEND US is now ${body.emoji}`, "notification");
    });

    stompClient.subscribe("/user/queue/history", payload => {
        const body = JSON.parse(payload.body);
        appendListItem(notificationList, `History loaded (${body.totalCount})`);
        (body.messages ?? []).forEach(renderChatMessage);
    });

    stompClient.subscribe("/user/queue/errors", payload => {
        const body = JSON.parse(payload.body);
        appendListItem(errorList, `${body.errorCode}: ${body.message}`);
    });

    stompClient.subscribe("/user/queue/preferences/sendme", payload => {
        const body = JSON.parse(payload.body);
        appendListItem(notificationList, `SEND ME synced to ${body.emoji}`);
    });
}

function registerUser() {
    const payload = {
        emoji: meSelect.value,
        deviceId
    };
    stompClient.send("/app/user/register", {}, JSON.stringify(payload));
    appendListItem(notificationList, `Registering as ${payload.emoji} (${deviceId})`);
}

function connect() {
    if (stompClient) {
        return;
    }

    const socket = new SockJS("/ws");
    stompClient = Stomp.over(socket);
    stompClient.debug = () => {};
    stompClient.heartbeat.outgoing = 25000;
    stompClient.heartbeat.incoming = 25000;

    stompClient.connect(
        {},
        frame => {
            stompSessionId = frame.headers["session"] ?? frame.headers["simp-session-id"] ?? "n/a";
            setStatus(true);
            subscribeToDestinations();
            registerUser();
        },
        error => {
            appendListItem(errorList, `Connection error: ${error.body ?? error}`);
            disconnect();
        }
    );
}

function disconnect() {
    if (!stompClient) {
        return;
    }

    if (stompSessionId) {
        stompClient.send("/app/user/unregister", {}, JSON.stringify({ sessionId: stompSessionId }));
    }

    stompClient.disconnect(() => {
        appendListItem(notificationList, "Disconnected from broker");
        cleanupConnection();
    });
}

function cleanupConnection() {
    stompClient = null;
    stompSessionId = null;
    setStatus(false);
}

function sendMessage() {
    if (!stompClient) {
        appendListItem(errorList, "Connect before sending messages");
        return;
    }

    const payload = {
        message: messageSelect.value,
        target: sendToSelect.value,
        sessionId: stompSessionId
    };

    stompClient.send("/app/message/send", {}, JSON.stringify(payload));
    appendListItem(notificationList, `Sent ${payload.message} to ${payload.target}`);
}

connectBtn.addEventListener("click", connect);
disconnectBtn.addEventListener("click", disconnect);
sendBtn.addEventListener("click", sendMessage);

populateSelectors();
deviceId = generateDeviceId();
deviceIdInput.value = deviceId;
setStatus(false);
