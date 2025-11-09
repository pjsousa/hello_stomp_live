package com.example.demo.websocket;

import com.example.demo.websocket.payload.Greeting;
import com.example.demo.websocket.payload.HelloMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;

@Controller
public class GreetingController {

    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public Greeting greeting(HelloMessage message) throws InterruptedException {
        Thread.sleep(1000);
        String escapedName = HtmlUtils.htmlEscape(message.getName());
        return new Greeting("Hello, " + escapedName + "!");
    }
}
