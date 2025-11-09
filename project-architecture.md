# Project Architecture: Realtime Chat Application

## 1. Project Overview

A real-time chat web application built with Java Spring Boot and STOMP/WebSocket protocol. Users communicate via emoji-based identities, with system-scheduled broadcasts delivering customizable content at different scopes (global, user, device).

## 2. Technology Stack

### Backend
- **Framework**: Spring Boot 3.x
- **WebSocket**: Spring WebSocket + STOMP
- **Message Broker**: Spring's Simple Broker (in-memory) or RabbitMQ/ActiveMQ for production
- **Scheduler**: Spring's @Scheduled annotations
- **Build Tool**: Maven or Gradle
- **Java Version**: Java 17+

### Frontend
- **Static HTML/CSS/JS**: Served from Spring Boot resources
- **WebSocket Client**: SockJS + STOMP.js
- **Module System**: ES6 Modules

### Storage
- **In-Memory**: ConcurrentHashMap for user sessions, preferences
- **Message History**: Circular buffer or database (H2/PostgreSQL for persistence)

## 3. Architecture Layers

### 3.1 Presentation Layer
- Static web resources (HTML, CSS, JS modules)
- STOMP client connection management
- UI state management

### 3.2 WebSocket Layer
- STOMP endpoint configuration (`/ws`)
- Message broker relay
- Session management
- User destination resolver

### 3.3 Business Logic Layer
- Message routing service
- User management service
- Preference management service
- Message history service
- Scheduler service

### 3.4 Data Layer
- User session repository
- Message history repository
- User preferences repository
- Device preferences repository
- System preferences repository

## 4. Core Components

### 4.1 WebSocket Configuration
```
WebSocketConfig
├── STOMP Endpoint: /ws (with SockJS fallback)
├── Message Broker: /topic, /queue, /user
└── Application Destination Prefix: /app
```

### 4.2 Domain Models
- **UserSession**: Tracks online users with their emoji identities
- **ChatMessage**: Represents a chat message
- **MessageHistory**: Last 10 messages buffer
- **UserPreferences**: Per-user customization (SEND ME)
- **DevicePreferences**: Per-device customization (SEND HERE)
- **SystemPreferences**: Global settings (SEND US)

### 4.3 Services

#### UserManagementService
- Register user sessions
- Track online users
- Handle disconnections
- Broadcast online user list updates

#### MessageRoutingService
- Route messages to everyone
- Route messages to specific users
- Store message history
- Retrieve initial message history

#### PreferenceService
- Manage SEND US (system broadcast)
- Manage SEND ME (user-specific)
- Manage SEND HERE (device-specific)
- Synchronize preference updates

#### SchedulerService
- Broadcast schedule (SEND US) - e.g., every 30 seconds
- User-specific schedule (SEND ME) - e.g., every 45 seconds
- Device-specific schedule (SEND HERE) - e.g., every 60 seconds

## 5. Data Flow Patterns

### 5.1 User Connection Flow
1. Client connects to `/ws` endpoint
2. Client subscribes to personal queue `/user/queue/messages`
3. Client subscribes to broadcast topic `/topic/broadcast`
4. Client subscribes to online users topic `/topic/users`
5. Client sends identity selection to `/app/user/register`
6. Server broadcasts updated user list to `/topic/users`

### 5.2 Message Sending Flow
1. Client sends message to `/app/message/send`
2. Server validates sender identity
3. Server routes to destination (everyone or specific user)
4. Server stores in message history
5. Server broadcasts/unicasts message

### 5.3 Preference Update Flow
1. Client updates preference via `/app/preference/update`
2. Server stores preference
3. Server broadcasts preference change to relevant subscribers
4. Scheduled task uses updated preference on next execution

### 5.4 Scheduled Message Flow
1. Scheduler triggers at interval
2. Service retrieves current preference values
3. Service sends to appropriate destinations
4. Messages appear in UI with "SYSTEM" as sender

## 6. Subscription Architecture

### Global Subscriptions (All Users)
- `/topic/broadcast` - All broadcast messages
- `/topic/users` - Online user list updates
- `/topic/system/sendus` - SEND US preference updates

### User-Specific Subscriptions
- `/user/queue/messages` - Direct messages to this user
- `/user/queue/scheduled` - User-specific scheduled messages
- `/user/queue/preferences/sendme` - SEND ME sync across devices

### Device-Specific Subscriptions
- `/user/queue/device/scheduled` - Device-specific scheduled messages

## 7. Message Persistence Strategy

### In-Memory Approach (Simpler)
- ConcurrentHashMap for user sessions
- CircularFifoQueue (Apache Commons) for last 10 messages
- ConcurrentHashMap for preferences

### Database Approach (Production-Ready)
- H2/PostgreSQL for message history
- User session table (with expiry)
- Preferences table (user_id, device_id, preference_type, value)

**Recommendation**: Start with in-memory, add database persistence as enhancement.

## 8. Security Considerations

### Current Scope (Simplified)
- No authentication required
- Emoji-based identity only
- Session-based user tracking

### Future Enhancements
- JWT-based authentication
- User registration
- Message encryption
- Rate limiting

## 9. Error Handling Strategy

### Connection Errors
- Auto-reconnect logic on client
- Session timeout handling (5 minutes idle)
- Graceful degradation

### Message Delivery
- Best-effort delivery (STOMP guarantees)
- No message acknowledgment in current scope
- Lost messages acceptable for this use case

### Validation
- Validate emoji selection from predefined set
- Validate message format
- Prevent empty messages
- Prevent duplicate user sessions with same emoji

## 10. Scalability Considerations

### Current Limitations
- Single server instance
- In-memory storage
- Simple broker (not clustered)

### Future Scaling Options
- External message broker (RabbitMQ)
- Redis for session storage
- Database for message history
- Load balancer with sticky sessions

## 11. Project Structure

```
src/main/java/com/company/realtimechat/
├── config/
│   ├── WebSocketConfig.java
│   └── SchedulerConfig.java
├── controller/
│   ├── MessageController.java
│   ├── UserController.java
│   └── PreferenceController.java
├── model/
│   ├── ChatMessage.java
│   ├── UserSession.java
│   ├── OnlineUserList.java
│   ├── UserPreference.java
│   ├── DevicePreference.java
│   └── SystemPreference.java
├── dto/
│   ├── request/
│   │   ├── SendMessageRequest.java
│   │   ├── RegisterUserRequest.java
│   │   ├── UpdatePreferenceRequest.java
│   │   └── UpdateSendUsRequest.java
│   ├── response/
│   │   ├── ChatMessageResponse.java
│   │   ├── OnlineUsersResponse.java
│   │   ├── MessageHistoryResponse.java
│   │   └── PreferenceUpdateResponse.java
│   └── internal/
│       ├── ScheduledBroadcastMessage.java
│       ├── ScheduledUserMessage.java
│       └── ScheduledDeviceMessage.java
├── service/
│   ├── UserManagementService.java
│   ├── MessageRoutingService.java
│   ├── PreferenceService.java
│   ├── MessageHistoryService.java
│   └── SchedulerService.java
├── repository/
│   ├── UserSessionRepository.java
│   ├── MessageHistoryRepository.java
│   └── PreferenceRepository.java
├── listener/
│   └── WebSocketEventListener.java
└── util/
    ├── EmojiConstants.java
    └── SessionUtils.java

src/main/resources/
├── static/
│   ├── index.html
│   ├── css/
│   │   └── styles.css
│   └── js/
│       ├── main.js
│       ├── websocket.js
│       ├── ui.js
│       └── constants.js
└── application.properties
```

## 12. Development Phases

### Phase 1: Core Infrastructure
- WebSocket configuration
- Basic STOMP endpoints
- User registration and session management
- Online user list broadcasting

### Phase 2: Message System
- Message sending (broadcast and direct)
- Message history storage and retrieval
- Message display in UI

### Phase 3: Preferences System
- SEND US (system broadcast preference)
- SEND ME (user preference)
- SEND HERE (device preference)
- Preference synchronization

### Phase 4: Scheduler System
- Broadcast scheduler
- User scheduler
- Device scheduler
- Scheduled message delivery

### Phase 5: UI Polish
- Cycling buttons implementation
- Real-time user list updates
- Message formatting and display
- Error handling and reconnection

## 13. Testing Strategy

### Unit Tests
- Service layer logic
- Repository operations
- Utility functions

### Integration Tests
- WebSocket connection handling
- Message routing
- Preference updates
- Scheduler execution

### Manual Testing
- Multi-device testing
- Concurrent user scenarios
- Network interruption handling
- Browser compatibility

## 14. Configuration Parameters

### application.properties
```
# Server
server.port=8080

# WebSocket
spring.websocket.heartbeat.send-interval=25000
spring.websocket.heartbeat.receive-interval=25000

# Scheduler intervals (milliseconds)
scheduler.broadcast.interval=30000
scheduler.user.interval=45000
scheduler.device.interval=60000

# Message history
message.history.size=10

# Session timeout (minutes)
session.timeout=5
```

## 15. Monitoring & Logging

### Key Metrics
- Active WebSocket connections
- Messages sent per minute
- Scheduler execution timing
- Error rates

### Logging Points
- User connect/disconnect
- Message routing decisions
- Preference updates
- Scheduler executions
- Error conditions
