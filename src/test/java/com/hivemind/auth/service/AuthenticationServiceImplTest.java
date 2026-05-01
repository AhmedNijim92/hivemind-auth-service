package com.hivemind.auth.service;

import com.hivemind.auth.dto.JwtAuthenticationResponse;
import com.hivemind.auth.dto.SigninRequest;
import com.hivemind.auth.entity.User;
import com.hivemind.auth.repository.UserRepository;
import com.hivemind.auth.service.impl.AuthenticationServiceImpl;
import com.hivemind.common.dto.UserDto;
import com.hivemind.common.event.UserCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith (MockitoExtension.class)
class AuthenticationServiceImplTest
{

    @Mock
    private UserRepository userRepository;

    @Mock
    private IOtpService otpService;

    @Mock
    private IJWTService jwtService;

    @Mock
    private KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    private User testUser;

    @BeforeEach
    void setUp()
    {
        testUser = new User();
        testUser.setUserId(UUID.randomUUID());
        testUser.setMobileNumber("+1234567890");
        testUser.setName("Test User");
        testUser.setRole("USER");
    }

    @Test
    void testSendOtp()
    {
        doNothing().when(otpService).sendOtp(anyString());

        authenticationService.sendOtp("+1234567890");

        verify(otpService, times(1)).sendOtp("+1234567890");
    }

    @Test
    void testSigninWithValidOtp()
    {
        SigninRequest request = new SigninRequest("+1234567890", "123456");
        when(otpService.verifyOtp(anyString(), anyString())).thenReturn(true);
        when(userRepository.findByMobileNumber(anyString())).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any(User.class))).thenReturn("test-token");

        JwtAuthenticationResponse response = authenticationService.signin(request);

        assertNotNull(response);
        assertEquals("test-token", response.getToken());
        assertEquals(testUser.getUserId(), response.getUserId());
    }

    @Test
    void testSigninWithInvalidOtp()
    {
        SigninRequest request = new SigninRequest("+1234567890", "wrong-otp");
        when(otpService.verifyOtp(anyString(), anyString())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authenticationService.signin(request));
    }

    @Test
    void testCreateUser()
    {
        UserDto userDto = UserDto.builder()
                .mobileNumber("+1234567890")
                .name("New User")
                .email("test@example.com")
                .build();

        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(any(User.class))).thenReturn("test-token");
        when(kafkaTemplate.send(anyString(), any(UserCreatedEvent.class))).thenReturn(null);

        JwtAuthenticationResponse response = authenticationService.createUser(userDto);

        assertNotNull(response);
        assertEquals("test-token", response.getToken());
        verify(kafkaTemplate, times(1)).send(eq("user-created-topic"), any(UserCreatedEvent.class));
    }
}
