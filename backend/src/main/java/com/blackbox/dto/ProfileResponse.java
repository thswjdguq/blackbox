package com.blackbox.dto;

import com.blackbox.entity.User;
import java.util.UUID;

public record ProfileResponse(UUID id, String name, String email, String role) {
    public static ProfileResponse from(User u) {
        return new ProfileResponse(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }
}
