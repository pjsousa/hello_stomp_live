# Task Breakdown for Development

## Overview

Below is a detailed task breakdown with short overviews for key deliverables. Each task can be picked up independently by junior developers, with context provided for smooth onboarding.

---

## 1. WebSocket & STOMP Infrastructure

### Task 1.1: WebSocketConfig Setup
Setup the Spring WebSocket configuration to expose a STOMP endpoint `/ws` and enable SockJS fallback. Configure broker prefixes `/app`, `/topic`, `/queue`, and `/user`. Ensure heartbeats and session timeout settings are present.

### Task 1.2: STOMP Controller Skeleton
Create skeleton STOMP controllers for routing messages to relevant destinations. Implement basic logging and error-handling with custom ErrorResponse DTO.

---

## 2. User Identity & Presence Management

### Task 2.1: UserSession Domain Model
Develop the UserSession class, storing sessionId, emoji, deviceId, and timestamps. Ensure each session is tracked in a thread-safe map. Handle connection and disconnection events.

### Task 2.2: Online Users List Broadcasting
Implement logic for maintaining a real-time online users list and broadcasting updates to `/topic/users` using OnlineUsersResponse DTO. Trigger updates on user connect, disconnect, or emoji change.

---

## 3. Messaging System

### Task 3.1: Message Sending Flow
Implement message sending from client to `/app/message/send` and routing to either `/topic/broadcast` (for EVERYONE) or `/user/queue/messages` (for targeted delivery). Store new messages in a bounded message history buffer.

### Task 3.2: Message History Retrieval
On user registration, ensure the last 10 messages are sent to the client via `/user/queue/history` using MessageHistoryResponse DTO. Implement a circular buffer for efficient storage.

---

## 4. Preferences & Scheduled Messaging

### Task 4.1: Preference DTOs & Repository
Implement preference management classes for system-wide (SEND US), user-wide (SEND ME), and device-wide (SEND HERE) settings. Use food emojis only; validate selection. Store preferences in thread-safe maps.

### Task 4.2: Preference Update Endpoints
Implement endpoints `/app/preference/sendus`, `/app/preference/sendme`, and `/app/preference/sendhere` for updating preferences. On change, broadcast/sync updated values to relevant destinations and UIs.

### Task 4.3: Scheduled Message Delivery
Implement the three scheduled tasks for system (SEND US / `/topic/scheduled/broadcast`), user (`/user/queue/scheduled`), and device (`/user/queue/device/scheduled`). Ensure each scheduled message uses the current emoji(s) and sender as SYSTEM.

---

## 5. UI Communication Patterns

### Task 5.1: Dropdowns, Buttons, and Cycling Logic
Work with the UI team to ensure dropdowns for ME, SEND ME, SEND HERE, and SEND US are correctly synchronized with backend state. Implement cycling logic for SEND TO and MESSAGE buttons in JS.

### Task 5.2: Online User List Live Sync
Implement front-end subscription to `/topic/users` for live updates. Ensure SEND TO cycles through only current online user emojis and EVERYONE.

### Task 5.3: Initial UI Message History
Make sure frontend requests the last 10 messages on connect and displays them correctly with timestamp, sender, and emoji message.

---

## 6. Error Handling

### Task 6.1: ErrorResponse Flow
Implement standardized validation errors and deliver them through `/user/queue/errors`. Document all error codes and ensure clear mapping to client UI notifications.

---

## 7. Testing & Quality Assurance

### Task 7.1: Unit Tests
Write unit tests for key domain classes: UserSession, ChatMessage, Preferences. Cover basic insert/retrieve/validation logic.

### Task 7.2: Integration Tests
Test message delivery correctness: broadcasting, targeted delivery, scheduled messaging, and preference sync flows.

### Task 7.3: Manual Testing Scripts
Create manual testing scripts for multi-device scenarios, scheduled messages, and emoji cycling in UI.

---

## 8. Documentation

### Task 8.1: API and DTO Documentation
Document each endpoint, DTO, and their expected behavior in README or OpenAPI spec. Ensure every team member can pick up a task with a clear context reference.

---

## 9. Future Enhancements (Backlog)

- Database persistence for sessions, messages, and preferences
- Production-grade message broker integration
- Improved reconnection logic and client resilience
- System announcements: user join/leave events
- Authentication layer
