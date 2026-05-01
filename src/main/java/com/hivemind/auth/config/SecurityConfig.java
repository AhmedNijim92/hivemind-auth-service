package com.hivemind.auth.config;

import com.hivemind.auth.repository.UserRepository;
import com.hivemind.auth.service.IJWTService;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/**
 * Security configuration adapted from the original monolith SecurityConfiguration.
 * Includes JWT filter, AuthenticationProvider, CORS, and public/protected URL patterns.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig
{
    private static final String[] PUBLIC_URLS = {
            "/api/v1/auth/signin",
            "/api/v1/auth/sendOtp",
            "/api/v1/auth/createUser",
            "/actuator/**"
    };

    private static final String[] ADMIN_URLS = {
            "/api/v1/auth/createAdmin"
    };

    private final JwtAuthFilter jwtAuthFilter;
    private final UserRepository userRepository;

    @Bean
    public UserDetailsService userDetailsService()
    {
        return userId -> userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception
    {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request -> request
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .requestMatchers(ADMIN_URLS).hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .anyRequest().authenticated())
                .sessionManagement(manager -> manager.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider()
    {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception
    {
        return config.getAuthenticationManager();
    }

    // ─── JWT Authentication Filter (inner component) ─────────────────────────

    @Slf4j
    @Component
    @RequiredArgsConstructor
    static class JwtAuthFilter extends OncePerRequestFilter
    {
        private final IJWTService jwtService;
        private final UserRepository userRepository;

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException
        {
            final String authHeader = request.getHeader("Authorization");

            if (StringUtils.isEmpty(authHeader) || !authHeader.startsWith("Bearer "))
            {
                filterChain.doFilter(request, response);
                return;
            }

            try
            {
                final String jwt = authHeader.substring(7);
                final String userId = jwtService.extractUserId(jwt);

                if (!StringUtils.isEmpty(userId) && SecurityContextHolder.getContext().getAuthentication() == null)
                {
                    UserDetails userDetails = userRepository.findById(java.util.UUID.fromString(userId))
                            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

                    if (jwtService.validateToken(jwt))
                    {
                        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();

                        var authToken = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                userId, null, userDetails.getAuthorities());

                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        securityContext.setAuthentication(authToken);
                        SecurityContextHolder.setContext(securityContext);
                    }
                }
            }
            catch (Exception e)
            {
                log.debug("JWT authentication failed: {}", e.getMessage());
                // Continue without authentication — public endpoints will still work
            }

            filterChain.doFilter(request, response);
        }
    }
}
