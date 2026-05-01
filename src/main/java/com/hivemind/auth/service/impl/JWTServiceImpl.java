package com.hivemind.auth.service.impl;

import com.hivemind.auth.entity.User;
import com.hivemind.auth.service.IJWTService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JWTServiceImpl implements IJWTService
{

    @Value ("${jwt.secret}")
    private String jwtSecret;

    @Value ("${jwt.expiration}")
    private long jwtExpiration;

    @Override
    public String generateToken(User user)
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId().toString());
        claims.put("role", user.getRole());
        claims.put("email", user.getEmail());

        return Jwts.builder()
                .claims(claims)
                .subject(user.getUserId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public boolean validateToken(String token)
    {
        try
        {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    @Override
    public String extractUserId(String token)
    {
        return extractClaims(token).getSubject();
    }

    @Override
    public String extractRole(String token)
    {
        return extractClaims(token).get("role", String.class);
    }

    private Claims extractClaims(String token)
    {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey()
    {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
