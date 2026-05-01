package com.hivemind.auth.entity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Role
{
    USER(List.of("user:read", "user:write")),
    ADMIN(List.of("user:read", "user:write", "admin:read", "admin:write")),
    SUPER_ADMIN(List.of("user:read", "user:write", "admin:read", "admin:write", "super_admin:all"));

    private final List<String> permissions;

    Role(List<String> permissions)
    {
        this.permissions = permissions;
    }

    public List<GrantedAuthority> getAuthorities()
    {
        List<GrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + this.name()));
        return authorities;
    }
}
