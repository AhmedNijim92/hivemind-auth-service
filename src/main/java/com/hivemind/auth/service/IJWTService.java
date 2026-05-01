package com.hivemind.auth.service;

import com.hivemind.auth.entity.User;

public interface IJWTService
{
    String generateToken(User user);

    boolean validateToken(String token);

    String extractUserId(String token);

    String extractRole(String token);
}
