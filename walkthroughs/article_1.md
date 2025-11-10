Connection Lifecycle Walkthrough

When the browser loads the hello_stomp_live demo, `src/main/resources/static/app.js` immediately runs `init()`. That bootstraps the emoji buttons, registers UI listeners, and, most importantly, calls `connect()` to bring up a full STOMP-over-WebSocket session. This article traces that connection lifecycle from the first JavaScript call through the Spring backend that receives and brokers each frame.

Client bootstrap begins by discovering which STOMP implementation is on the window (the project bundles the official `@stomp/stompjs` build) and constructing a `Client` instance with a dynamic `brokerURL`. The `ws` vs `wss` pick happens in the browser so the single-page app works on HTTP and HTTPS deployments without configuration drift. The client also requests automatic reconnects and wires a verbose debug hook into the built-in diagnostics panel so that developers can read low-level STOMP traffic without opening DevTools.

```147:213:src/main/resources/static/app.js
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
    // onStompError, onWebSocketClose, and other handlers omitted for brevity
    try {
        stompClient.activate();
    } catch (error) {
        logDiagnostic("STOMP_ACTIVATE_ERROR", error instanceof Error ? error.message : String(error));
    }
}
```

Once `activate()` runs, the STOMP library performs the WebSocket handshake against the `/ws` endpoint. On success, Spring’s STOMP bridge assigns a session id, which the client persists so that it can subscribe to per-device topics (`/topic/device/{sessionId}/messages` and `/control`). Immediately after connecting, `subscribeStaticTopics()` wires the browser into the broadcast, online-user, and global-settings feeds. `subscribeUserTopics()` defers until the user picks an avatar, but each invocation rebinds the subscriptions so the browser only listens to the active animal emoji. Finally, `registerSession()` publishes the initial identity payload, kicking off the server-side snapshot sequence covered in Article 2.

The server’s handshake and broker setup happens in `WebSocketConfig`. Enabling the simple broker under `/topic` and setting the application prefix to `/app` creates the contract the JavaScript assumes. The `LoggingHandshakeInterceptor` and `LoggingChannelInterceptor` pair provide request-level observability: every CONNECT, SUBSCRIBE, and SEND frame (inbound and outbound) is traced with session ids and native headers so you can reconstruct what happened in production from your logs.

```19:39:src/main/java/com/example/demo/config/WebSocketConfig.java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final LoggingChannelInterceptor inboundLoggingInterceptor = new LoggingChannelInterceptor("inbound");
    private final LoggingChannelInterceptor outboundLoggingInterceptor = new LoggingChannelInterceptor("outbound");
    private final LoggingHandshakeInterceptor loggingHandshakeInterceptor = new LoggingHandshakeInterceptor();

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(loggingHandshakeInterceptor)
                .setAllowedOrigins("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(inboundLoggingInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(outboundLoggingInterceptor);
    }
}
```

As soon as a WebSocket upgrade request arrives, the handshake interceptor stamps it with an identifier and logs timing metrics. When STOMP frames begin to flow, the channel interceptors echo the command, destination, and headers for every message. This scaffolding is invaluable when validating that subscriptions are registered as expected or that application destinations (`/app/...`) route to the correct `@MessageMapping` method.

Taken together, the client and server layers create a resilient STOMP connection: the browser retries automatically when the socket drops, while the backend documents every phase of the conversation. Subsequent articles will build on this foundation, showing how session registration, messaging, settings, and online user synchronization all leverage the same connection lifecycle you’ve just explored.

