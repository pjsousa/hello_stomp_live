package com.example.demo.websocket.dto.response;

import com.example.demo.websocket.dto.PreferenceScope;
import com.example.demo.websocket.dto.PreferenceType;

public record PreferenceUpdateResponse(
        PreferenceType preferenceType,
        String emoji,
        String updatedBy,
        long timestamp,
        PreferenceScope scope) {
}
