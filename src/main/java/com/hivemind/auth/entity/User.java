package com.hivemind.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Data
@Table ("users")
@AllArgsConstructor
@NoArgsConstructor
public class User implements UserDetails
{

    @PrimaryKey ("user_id")
    private UUID userId;

    @Column ("mobile_number")
    private String mobileNumber;

    @Column ("email")
    private String email;

    @Column ("name")
    private String name;

    @Column ("role")
    private String role;

    @Column ("otp")
    private String otp;

    @Column ("created_at")
    private LocalDate createdAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities()
    {
        try
        {
            return Role.valueOf(role).getAuthorities();
        }
        catch (IllegalArgumentException e)
        {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }

    @Override
    public String getPassword()
    {
        return otp;
    }

    @Override
    public String getUsername()
    {
        return mobileNumber;
    }

    @Override
    public boolean isAccountNonExpired()
    {
        return true;
    }

    @Override
    public boolean isAccountNonLocked()
    {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired()
    {
        return true;
    }

    @Override
    public boolean isEnabled()
    {
        return true;
    }
}
