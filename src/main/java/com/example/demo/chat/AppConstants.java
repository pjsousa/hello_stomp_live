package com.example.demo.chat;

import java.util.List;

public final class AppConstants {

    private AppConstants() {
    }

    public static final List<String> ANIMAL_EMOJIS = List.of(
            "🐶", "🐱", "🐭", "🐹", "🐰",
            "🦊", "🐻", "🐼", "🐨", "🐯"
    );

    public static final List<String> FOOD_EMOJIS = List.of(
            "🍎", "🍌", "🍇", "🍕", "🍔",
            "🍣", "🍩", "🍰", "🥐", "🍪"
    );

    public static final String DEFAULT_SEND_ME = FOOD_EMOJIS.get(0);
    public static final String DEFAULT_SEND_HERE = FOOD_EMOJIS.get(1);
    public static final String DEFAULT_SEND_US = FOOD_EMOJIS.get(2);

    public static final String EVERYONE = "EVERYONE";
}
