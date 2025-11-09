# Realtime Emoji Chat

This Spring Boot application hosts the realtime emoji chat experience described in `requirements.md`.  
Iteration 1 focuses on bringing the WebSocket infrastructure to life together with a lightweight static UI that proves the broker, topics, and STOMP endpoints are wired correctly.

## Getting Started

- Java 17+
- Maven Wrapper (`./mvnw`)

### Run the application

```bash
./mvnw spring-boot:run
```

The static web app is available at [http://localhost:8080](http://localhost:8080).

## 1. WebSocket & STOMP Infrastructure

- **Task 1.1: WebSocketConfig Setup** – `/ws` endpoint with SockJS fallback, `/app` application prefix, and `/topic` / `/queue` / `/user` broker destinations. Heartbeats, basic executor sizing, and transport limits are configured to keep connections healthy during development.
- **Task 1.2: STOMP Controller Skeleton** – Initial controllers (`UserStompController`, `MessageStompController`, `PreferenceStompController`) accept the documented DTOs, log activity, and broadcast placeholder responses so clients can already exercise the messaging patterns. Minimal validation funnels errors through the shared `ErrorResponse`.

### What you can test right now

- **Broker connectivity**: open the UI, choose an emoji, and use _Connect & Register_. The message panes update with the registration broadcast and empty history delivered via `/topic/users` and `/user/queue/history`.
- **Broadcast messaging**: pick any food emoji and click _Send Message_. The skeleton controller publishes to `/topic/broadcast`; every tab connected to the app will display the message immediately.
- **Preference updates**: while connected, open your browser console and execute  
  `window.stompClient.send('/app/preference/sendus', {}, JSON.stringify({emoji:'🍔', sessionId: window.stompSessionId}))` to see the global preference notification dispatched to `/topic/system/sendus`.
- **Error propagation**: attempt to send a blank message or click _Disconnect_. The UI shows the standardized error responses pushed through `/user/queue/errors`.

### Manual test script

1. Start the server with `./mvnw spring-boot:run`.
2. Open two browser tabs pointing to `http://localhost:8080`.
3. In each tab select a different _ME_ emoji, then click _Connect & Register_.  
   - Observe the **Notifications** column for the online users broadcast.  
   - Confirm the **Messages** column loads an empty history.
4. In one tab choose any food emoji and press _Send Message_.  
   - Both tabs should receive the broadcast entry instantly.
5. From the same tab try a manual SEND US update (snippet above) and watch both tabs react.
6. Send a blank message (temporarily remove all characters using browser dev tools) to verify validation errors surface in the **Errors** column.
7. Click _Disconnect_ and observe the unregister flow placeholder error in **Errors**.

### Known gaps (next iterations)

- Targeted messaging (SEND TO specific user) and preference/device schedulers are intentionally stubbed; controllers currently emit `NOT_IMPLEMENTED` errors for these flows.
- Presence management is mocked with the registering user only. Future work will introduce a proper session registry and shared state.
- UI controls are simplified for this iteration; cycling buttons and synchronized dropdowns will arrive alongside the business logic phases.
