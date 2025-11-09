# STOMP Endpoints, Topics & Message Patterns

## 1. WebSocket Configuration

### Base Configuration
- **WebSocket Endpoint**: `/ws`
- **SockJS Fallback**: Enabled
- **Application Prefix**: `/app`
- **Broker Prefixes**: `/topic`, `/queue`, `/user`

---

## 2. Application Destinations (Client → Server)

### 2.1 User Management

#### `/app/user/register`
**Purpose**: Register user with emoji identity and subscribe to relevant channels  
**Method**: SEND  
**Request DTO**: `RegisterUserRequest`  
**Response**: Broadcasts to `/topic/users`  

**Flow**:
1. Client sends emoji selection
2. Server validates emoji uniqueness (or generates session ID)
3. Server creates UserSession
4. Server broadcasts updated online user list

---

#### `/app/user/unregister`
**Purpose**: Explicit user disconnect  
**Method**: SEND  
**Request DTO**: `UnregisterUserRequest`  
**Response**: Broadcasts to `/topic/users`  

**Flow**:
1. Client sends disconnect request
2. Server removes UserSession
3. Server broadcasts updated online user list

---

### 2.2 Message Operations

#### `/app/message/send`
**Purpose**: Send message to everyone or specific user  
**Method**: SEND  
**Request DTO**: `SendMessageRequest`  
**Response**: Broadcasts to `/topic/broadcast` OR sends to `/user/{username}/queue/messages`  

**Flow**:
1. Client sends message with target (EVERYONE or specific emoji)
2. Server validates sender session
3. Server creates ChatMessage with timestamp
4. Server routes based on target:
   - EVERYONE → `/topic/broadcast`
   - Specific user → `/user/{sessionId}/queue/messages`
5. Server stores in message history

---

### 2.3 Preference Management

#### `/app/preference/sendus`
**Purpose**: Update system-wide SEND US value  
**Method**: SEND  
**Request DTO**: `UpdateSendUsRequest`  
**Response**: Broadcasts to `/topic/system/sendus`  

**Flow**:
1. Any client sends new SEND US emoji
2. Server updates system preference
3. Server broadcasts new value to all users
4. All UIs update SEND US dropdown

---

#### `/app/preference/sendme`
**Purpose**: Update user-specific SEND ME value  
**Method**: SEND  
**Request DTO**: `UpdateSendMeRequest`  
**Response**: Sends to `/user/{userId}/queue/preferences/sendme` (all devices)  

**Flow**:
1. Client sends new SEND ME emoji
2. Server updates user preference (by user ID, not session)
3. Server sends update to all sessions of this user
4. All devices of this user update SEND ME dropdown

---

#### `/app/preference/sendhere`
**Purpose**: Update device-specific SEND HERE value  
**Method**: SEND  
**Request DTO**: `UpdateSendHereRequest`  
**Response**: No broadcast (device-local only)  

**Flow**:
1. Client sends new SEND HERE emoji
2. Server updates device preference (by session ID)
3. Only affects scheduled messages to this device

---

## 3. Subscription Destinations (Server → Client)

### 3.1 Global Broadcasts

#### `/topic/broadcast`
**Purpose**: Receive broadcast messages from all users  
**Subscription**: All connected clients  
**Message DTO**: `ChatMessageResponse`  

**Triggered by**:
- User sending message to EVERYONE
- Any broadcast message

---

#### `/topic/users`
**Purpose**: Receive real-time online user list updates  
**Subscription**: All connected clients  
**Message DTO**: `OnlineUsersResponse`  

**Triggered by**:
- User connects (registers)
- User disconnects
- User changes emoji

---

#### `/topic/system/sendus`
**Purpose**: Receive SEND US preference updates  
**Subscription**: All connected clients  
**Message DTO**: `PreferenceUpdateResponse`  

**Triggered by**:
- Any user updating SEND US value
- System initialization

---

#### `/topic/scheduled/broadcast`
**Purpose**: Receive scheduled system broadcasts (SEND US)  
**Subscription**: All connected clients  
**Message DTO**: `ScheduledBroadcastMessage`  

**Triggered by**:
- Broadcast scheduler (every 30 seconds)
- Contains current SEND US emoji value

---

### 3.2 User-Specific Queues

#### `/user/queue/messages`
**Purpose**: Receive direct messages sent to this user  
**Subscription**: Individual user  
**Message DTO**: `ChatMessageResponse`  

**Triggered by**:
- Another user sending message to this specific emoji
- SEND TO = [specific user]

---

#### `/user/queue/scheduled`
**Purpose**: Receive user-specific scheduled messages (SEND ME)  
**Subscription**: Individual user (all devices)  
**Message DTO**: `ScheduledUserMessage`  

**Triggered by**:
- User scheduler (every 45 seconds)
- Contains current SEND ME emoji value for this user

---

#### `/user/queue/preferences/sendme`
**Purpose**: Receive SEND ME preference sync across devices  
**Subscription**: Individual user (all devices)  
**Message DTO**: `PreferenceUpdateResponse`  

**Triggered by**:
- User updating SEND ME on any device
- Synchronizes dropdown across all their devices

---

#### `/user/queue/device/scheduled`
**Purpose**: Receive device-specific scheduled messages (SEND HERE)  
**Subscription**: Individual device/session  
**Message DTO**: `ScheduledDeviceMessage`  

**Triggered by**:
- Device scheduler (every 60 seconds)
- Contains current SEND HERE emoji value for this device

---

#### `/user/queue/history`
**Purpose**: Receive initial message history on connection  
**Subscription**: Individual user (on connect)  
**Message DTO**: `MessageHistoryResponse`  

**Triggered by**:
- User registration/connection
- Contains last 10 messages

---

## 4. Message Pattern Summary

| Pattern | Destination | Use Case |
|---------|-------------|----------|
| **Broadcast to All** | `/topic/broadcast` | Messages sent to EVERYONE |
| **Broadcast to All** | `/topic/users` | Online user list updates |
| **Broadcast to All** | `/topic/system/sendus` | SEND US preference changes |
| **Broadcast to All** | `/topic/scheduled/broadcast` | Scheduled system broadcasts |
| **User-Specific** | `/user/queue/messages` | Direct messages to one user |
| **User-Specific** | `/user/queue/scheduled` | Scheduled messages to user (all devices) |
| **User-Specific** | `/user/queue/preferences/sendme` | SEND ME sync across devices |
| **Device-Specific** | `/user/queue/device/scheduled` | Scheduled messages to one device |
| **One-Time** | `/user/queue/history` | Initial history on connect |

---

## 5. Scheduler Destinations

### Scheduler 1: Broadcast Scheduler
- **Interval**: Every 30 seconds
- **Destination**: `/topic/scheduled/broadcast`
- **Content**: Current SEND US emoji value
- **Sender**: "SYSTEM"

### Scheduler 2: User Scheduler
- **Interval**: Every 45 seconds
- **Destination**: `/user/queue/scheduled` (for each user)
- **Content**: Current SEND ME emoji value for that user
- **Sender**: "SYSTEM"

### Scheduler 3: Device Scheduler
- **Interval**: Every 60 seconds
- **Destination**: `/user/queue/device/scheduled` (for each device/session)
- **Content**: Current SEND HERE emoji value for that device
- **Sender**: "SYSTEM"

---

## 6. Connection Flow Sequence

### Initial Connection
```
1. Client connects to /ws
2. Client SEND to /app/user/register with emoji
3. Server broadcasts to /topic/users (new user online)
4. Server sends to /user/queue/history (last 10 messages)
5. Client subscribes to:
   - /topic/broadcast
   - /topic/users
   - /topic/system/sendus
   - /topic/scheduled/broadcast
   - /user/queue/messages
   - /user/queue/scheduled
   - /user/queue/preferences/sendme
   - /user/queue/device/scheduled
```

### Disconnection
```
1. WebSocket disconnect event triggers
2. Server removes user session
3. Server broadcasts to /topic/users (user offline)
```

---

## 7. Message Routing Logic

### Broadcast Message Routing
```
If (target == "EVERYONE"):
    destination = /topic/broadcast
    send to all clients
```

### Direct Message Routing
```
If (target == specific emoji):
    lookup user sessions by emoji
    for each session:
        destination = /user/{sessionId}/queue/messages
        send to that session
```

### Preference Update Routing

#### SEND US (System-Wide)
```
destination = /topic/system/sendus
send to all clients
```

#### SEND ME (User-Wide, All Devices)
```
lookup all sessions by userId
for each session:
    destination = /user/{sessionId}/queue/preferences/sendme
    send to that session
```

#### SEND HERE (Device-Only)
```
No broadcasting needed
Store in device preference
Used only by device scheduler
```

---

## 8. Error Handling Destinations

### `/user/queue/errors`
**Purpose**: Send error messages to specific user  
**Message DTO**: `ErrorResponse`  

**Example errors**:
- Invalid emoji selection
- Emoji already taken
- Message validation failed
- Target user not found

---

## 9. Heartbeat & Keep-Alive

### Configuration
- **Client Heartbeat**: 25 seconds
- **Server Heartbeat**: 25 seconds
- **Connection Timeout**: 60 seconds idle

### Auto-Disconnect
- Sessions idle for 5 minutes are automatically removed
- Server broadcasts user list update

---

## 10. Message Ordering Guarantees

### STOMP Guarantees
- Messages to same destination are ordered
- Messages across destinations may arrive out of order

### Application-Level
- Timestamps on all messages for client-side sorting
- Message history ordered by timestamp
- UI should sort messages by timestamp if needed

---

## 11. Scalability Considerations

### Current Design (Single Server)
- Simple broker (in-memory)
- All subscriptions handled by single server

### Future Multi-Server Design
- External broker (RabbitMQ/ActiveMQ)
- User affinity via load balancer
- Session replication
- Shared preference store (Redis)

---

## 12. Complete Endpoint Reference

| Endpoint | Type | Direction | Purpose |
|----------|------|-----------|---------|
| `/ws` | WebSocket | Client→Server | Initial connection |
| `/app/user/register` | SEND | Client→Server | Register user |
| `/app/user/unregister` | SEND | Client→Server | Unregister user |
| `/app/message/send` | SEND | Client→Server | Send chat message |
| `/app/preference/sendus` | SEND | Client→Server | Update SEND US |
| `/app/preference/sendme` | SEND | Client→Server | Update SEND ME |
| `/app/preference/sendhere` | SEND | Client→Server | Update SEND HERE |
| `/topic/broadcast` | SUBSCRIBE | Server→Client | Broadcast messages |
| `/topic/users` | SUBSCRIBE | Server→Client | Online user list |
| `/topic/system/sendus` | SUBSCRIBE | Server→Client | SEND US updates |
| `/topic/scheduled/broadcast` | SUBSCRIBE | Server→Client | Scheduled broadcasts |
| `/user/queue/messages` | SUBSCRIBE | Server→Client | Direct messages |
| `/user/queue/scheduled` | SUBSCRIBE | Server→Client | User scheduled msgs |
| `/user/queue/preferences/sendme` | SUBSCRIBE | Server→Client | SEND ME sync |
| `/user/queue/device/scheduled` | SUBSCRIBE | Server→Client | Device scheduled msgs |
| `/user/queue/history` | SUBSCRIBE | Server→Client | Initial message history |
| `/user/queue/errors` | SUBSCRIBE | Server→Client | Error messages |
