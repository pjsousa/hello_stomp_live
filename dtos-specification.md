# Data Transfer Objects (DTOs)

## 1. Request DTOs (Client → Server)

### 1.1 RegisterUserRequest
**Endpoint**: `/app/user/register`  
**Purpose**: Register user with emoji identity

```java
{
  "emoji": "🐶",           // Selected animal emoji from predefined list
  "deviceId": "uuid-v4"    // Client-generated device identifier
}
```

**Validation**:
- `emoji`: Required, must be from predefined animal emoji list
- `deviceId`: Required, UUID format

---

### 1.2 UnregisterUserRequest
**Endpoint**: `/app/user/unregister`  
**Purpose**: Explicit disconnect

```java
{
  "sessionId": "session-uuid"  // Server-assigned session ID
}
```

**Validation**:
- `sessionId`: Required, must exist

---

### 1.3 SendMessageRequest
**Endpoint**: `/app/message/send`  
**Purpose**: Send chat message to everyone or specific user

```java
{
  "message": "🍕",              // Food emoji message
  "target": "EVERYONE",         // "EVERYONE" or specific animal emoji
  "sessionId": "session-uuid"   // Sender's session ID
}
```

**Validation**:
- `message`: Required, must be from predefined food emoji list
- `target`: Required, either "EVERYONE" or valid emoji from online users
- `sessionId`: Required, must be active session

---

### 1.4 UpdateSendUsRequest
**Endpoint**: `/app/preference/sendus`  
**Purpose**: Update system-wide broadcast preference

```java
{
  "emoji": "🍔",               // Food emoji for system broadcast
  "sessionId": "session-uuid"  // User who made the change
}
```

**Validation**:
- `emoji`: Required, must be from predefined food emoji list
- `sessionId`: Required for audit trail

---

### 1.5 UpdateSendMeRequest
**Endpoint**: `/app/preference/sendme`  
**Purpose**: Update user-specific scheduled message preference

```java
{
  "emoji": "🍕",               // Food emoji for user-specific messages
  "userId": "user-uuid",       // User identifier (across devices)
  "sessionId": "session-uuid"  // Current session making the change
}
```

**Validation**:
- `emoji`: Required, must be from predefined food emoji list
- `userId`: Required, identifies user across sessions
- `sessionId`: Required

---

### 1.6 UpdateSendHereRequest
**Endpoint**: `/app/preference/sendhere`  
**Purpose**: Update device-specific scheduled message preference

```java
{
  "emoji": "🍟",               // Food emoji for device-specific messages
  "deviceId": "device-uuid",   // Device identifier
  "sessionId": "session-uuid"  // Current session
}
```

**Validation**:
- `emoji`: Required, must be from predefined food emoji list
- `deviceId`: Required
- `sessionId`: Required

---

## 2. Response DTOs (Server → Client)

### 2.1 ChatMessageResponse
**Destinations**: `/topic/broadcast`, `/user/queue/messages`  
**Purpose**: Deliver chat messages

```java
{
  "timestamp": "14:23:45",     // HH:mm:ss format
  "sender": "🐶",              // Sender's animal emoji (or "SYSTEM")
  "message": "🍕",             // Food emoji message
  "type": "USER_MESSAGE",      // Enum: USER_MESSAGE, SYSTEM_BROADCAST, etc.
  "target": "EVERYONE"         // Shows if broadcast or direct
}
```

**Fields**:
- `timestamp`: Server-generated, formatted as HH:mm:ss
- `sender`: Animal emoji or "SYSTEM"
- `message`: Food emoji content
- `type`: Message type for UI styling
- `target`: Who the message was sent to

---

### 2.2 OnlineUsersResponse
**Destination**: `/topic/users`  
**Purpose**: Broadcast current online user list

```java
{
  "users": [
    {
      "emoji": "🐶",
      "sessionId": "session-uuid-1"
    },
    {
      "emoji": "🐱",
      "sessionId": "session-uuid-2"
    }
  ],
  "timestamp": 1699545825000   // Unix timestamp
}
```

**Fields**:
- `users`: Array of online users with their emoji and session ID
- `timestamp`: When this list was generated

---

### 2.3 MessageHistoryResponse
**Destination**: `/user/queue/history`  
**Purpose**: Send initial message history on connection

```java
{
  "messages": [
    {
      "timestamp": "14:20:15",
      "sender": "🐶",
      "message": "🍕",
      "type": "USER_MESSAGE",
      "target": "EVERYONE"
    },
    // ... up to 10 messages
  ],
  "totalCount": 10
}
```

**Fields**:
- `messages`: Array of last 10 messages (ChatMessageResponse format)
- `totalCount`: Number of messages returned

---

### 2.4 PreferenceUpdateResponse
**Destinations**: `/topic/system/sendus`, `/user/queue/preferences/sendme`  
**Purpose**: Notify preference changes

```java
{
  "preferenceType": "SEND_US",      // Enum: SEND_US, SEND_ME, SEND_HERE
  "emoji": "🍔",                     // New emoji value
  "updatedBy": "🐶",                 // Who made the change
  "timestamp": 1699545825000,        // Unix timestamp
  "scope": "GLOBAL"                  // GLOBAL, USER, or DEVICE
}
```

**Fields**:
- `preferenceType`: Which preference was updated
- `emoji`: New value
- `updatedBy`: User emoji who made the change
- `timestamp`: When the change occurred
- `scope`: Scope of the preference

---

### 2.5 ScheduledBroadcastMessage
**Destination**: `/topic/scheduled/broadcast`  
**Purpose**: System-scheduled broadcast (SEND US)

```java
{
  "timestamp": "14:25:00",
  "sender": "SYSTEM",
  "message": "🍔",               // Current SEND US emoji
  "type": "SCHEDULED_BROADCAST",
  "target": "EVERYONE"
}
```

**Fields**: Same as ChatMessageResponse but sender is always "SYSTEM"

---

### 2.6 ScheduledUserMessage
**Destination**: `/user/queue/scheduled`  
**Purpose**: User-scheduled message (SEND ME)

```java
{
  "timestamp": "14:25:30",
  "sender": "SYSTEM",
  "message": "🍕",               // Current SEND ME emoji for this user
  "type": "SCHEDULED_USER",
  "target": "🐶"                 // User's emoji
}
```

**Fields**: Same as ChatMessageResponse but sender is always "SYSTEM"

---

### 2.7 ScheduledDeviceMessage
**Destination**: `/user/queue/device/scheduled`  
**Purpose**: Device-scheduled message (SEND HERE)

```java
{
  "timestamp": "14:26:00",
  "sender": "SYSTEM",
  "message": "🍟",               // Current SEND HERE emoji for this device
  "type": "SCHEDULED_DEVICE",
  "target": "🐶"                 // User's emoji
}
```

**Fields**: Same as ChatMessageResponse but sender is always "SYSTEM"

---

### 2.8 ErrorResponse
**Destination**: `/user/queue/errors`  
**Purpose**: Send error messages to client

```java
{
  "errorCode": "EMOJI_TAKEN",
  "message": "The selected emoji is already in use",
  "timestamp": 1699545825000,
  "field": "emoji"               // Optional: which field caused error
}
```

**Error Codes**:
- `EMOJI_TAKEN`: Selected emoji already in use
- `INVALID_EMOJI`: Emoji not in predefined list
- `INVALID_TARGET`: Target user not found
- `SESSION_EXPIRED`: Session no longer valid
- `VALIDATION_ERROR`: General validation failure

---

## 3. Internal DTOs (Server-Side Only)

### 3.1 UserSession
**Purpose**: Track active user sessions

```java
{
  "sessionId": "uuid",
  "userId": "user-uuid",         // Same across devices for same user
  "deviceId": "device-uuid",
  "emoji": "🐶",
  "connectedAt": 1699545825000,
  "lastActivity": 1699545825000,
  "stompSessionId": "stomp-session-id"
}
```

**Usage**: In-memory session management

---

### 3.2 UserPreference
**Purpose**: Store user-level preferences (SEND ME)

```java
{
  "userId": "user-uuid",
  "sendMeEmoji": "🍕",
  "updatedAt": 1699545825000
}
```

**Usage**: Preference repository

---

### 3.3 DevicePreference
**Purpose**: Store device-level preferences (SEND HERE)

```java
{
  "deviceId": "device-uuid",
  "sessionId": "session-uuid",
  "sendHereEmoji": "🍟",
  "updatedAt": 1699545825000
}
```

**Usage**: Preference repository

---

### 3.4 SystemPreference
**Purpose**: Store system-level preferences (SEND US)

```java
{
  "id": "SEND_US",
  "emoji": "🍔",
  "updatedAt": 1699545825000,
  "updatedBy": "🐶"
}
```

**Usage**: Single global preference

---

### 3.5 ChatMessage (Domain Model)
**Purpose**: Internal message representation

```java
{
  "id": "uuid",
  "timestamp": 1699545825000,
  "sender": "🐶",
  "message": "🍕",
  "target": "EVERYONE",
  "type": "USER_MESSAGE",
  "sessionId": "sender-session-id"
}
```

**Usage**: Message history storage

---

## 4. Enum Definitions

### MessageType
```java
enum MessageType {
  USER_MESSAGE,           // Regular user-to-user message
  SCHEDULED_BROADCAST,    // System broadcast (SEND US)
  SCHEDULED_USER,         // User-specific scheduled (SEND ME)
  SCHEDULED_DEVICE,       // Device-specific scheduled (SEND HERE)
  SYSTEM_ANNOUNCEMENT     // System messages (user joined, etc.)
}
```

---

### PreferenceType
```java
enum PreferenceType {
  SEND_US,      // Global system broadcast
  SEND_ME,      // User-specific
  SEND_HERE     // Device-specific
}
```

---

### PreferenceScope
```java
enum PreferenceScope {
  GLOBAL,    // All users
  USER,      // All devices of one user
  DEVICE     // Single device
}
```

---

## 5. Constants

### Animal Emojis (User Identity)
```java
String[] ANIMAL_EMOJIS = {
  "🐶", "🐱", "🐭", "🐹", "🐰",
  "🦊", "🐻", "🐼", "🐨", "🐯"
};
```

---

### Food Emojis (Messages & Preferences)
```java
String[] FOOD_EMOJIS = {
  "🍕", "🍔", "🍟", "🌭", "🍿",
  "🥓", "🥚", "🧇", "🥞", "🧈",
  "🍞", "🥐", "🥖", "🥨", "🥯",
  "🧀", "🍖", "🍗", "🥩", "🌮"
};
```

---

## 6. DTO Validation Rules

### Common Validations
- All emoji fields must be from predefined lists
- All timestamps are UTC
- Session IDs must be valid UUIDs
- Required fields cannot be null or empty

### Field-Specific
- `emoji` (animal): Must be one of 10 animal emojis
- `emoji` (food): Must be one of 20 food emojis
- `target`: Must be "EVERYONE" or valid online user emoji
- `timestamp` (string): Format "HH:mm:ss"
- `timestamp` (long): Unix timestamp in milliseconds

---

## 7. JSON Serialization Notes

### Date/Time Handling
- Server generates timestamps
- Display format: "HH:mm:ss" (24-hour)
- Storage format: Unix timestamp (long)

### Emoji Handling
- UTF-8 encoding required
- No emoji validation on JSON layer (handled in service layer)
- Emojis stored as strings

### Null Handling
- Optional fields can be null
- Required fields throw validation error if null
- Empty strings treated as invalid for required fields

---

## 8. DTO Mapping Examples

### Registration Flow
```
Client sends RegisterUserRequest
Server creates UserSession
Server creates ChatMessageResponse (system announcement)
Server creates OnlineUsersResponse
Server sends MessageHistoryResponse
```

### Message Sending Flow
```
Client sends SendMessageRequest
Server creates ChatMessage (domain)
Server creates ChatMessageResponse
Server routes to appropriate destination
```

### Preference Update Flow
```
Client sends UpdateSendUsRequest
Server updates SystemPreference
Server creates PreferenceUpdateResponse
Server broadcasts to /topic/system/sendus
```

---

## 9. Performance Considerations

### DTO Size
- Keep DTOs lightweight
- Avoid nested objects where possible
- Use enums for type fields

### Serialization
- Jackson for JSON serialization
- Register custom serializers for timestamps
- UTF-8 encoding for emojis

### Validation
- Validate at controller level before service
- Return ErrorResponse for validation failures
- Use JSR-303 annotations where applicable
