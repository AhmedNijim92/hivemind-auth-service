package com.hivemind.auth.service.impl;

import com.hivemind.auth.dto.JwtAuthenticationResponse;
import com.hivemind.auth.dto.SigninRequest;
import com.hivemind.auth.entity.User;
import com.hivemind.auth.repository.UserRepository;
import com.hivemind.auth.service.IAuthenticationService;
import com.hivemind.auth.service.IJWTService;
import com.hivemind.auth.service.IOtpService;
import com.hivemind.common.dto.UserDto;
import com.hivemind.common.event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements IAuthenticationService
{

    private final UserRepository userRepository;
    private final IOtpService otpService;
    private final IJWTService jwtService;
    private final KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    @Override
    public void sendOtp(String mobileNumber)
    {
        otpService.sendOtp(mobileNumber);
    }

    @Override
    public JwtAuthenticationResponse signin(SigninRequest request)
    {
        if (!otpService.verifyOtp(request.getMobileNumber(), request.getOtp()))
        {
            throw new RuntimeException("Invalid OTP");
        }

        User user = userRepository.findByMobileNumber(request.getMobileNumber())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtService.generateToken(user);

        return JwtAuthenticationResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .role(user.getRole())
                .build();
    }

    @Override
    public JwtAuthenticationResponse createUser(UserDto userDto)
    {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setMobileNumber(userDto.getMobileNumber());
        user.setName(userDto.getName());
        user.setEmail(userDto.getEmail());
        user.setRole("USER");
        user.setCreatedAt(LocalDate.now());

        userRepository.save(user);

        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId(user.getUserId())
                .mobileNumber(user.getMobileNumber())
                .name(user.getName())
                .email(user.getEmail())
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send("user-created-topic", event);

        String token = jwtService.generateToken(user);

        return JwtAuthenticationResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .role(user.getRole())
                .build();
    }

    @Override
    public JwtAuthenticationResponse createAdmin(UserDto userDto)
    {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setMobileNumber(userDto.getMobileNumber());
        user.setName(userDto.getName());
        user.setEmail(userDto.getEmail());
        user.setRole("ADMIN");
        user.setCreatedAt(LocalDate.now());

        userRepository.save(user);

        String token = jwtService.generateToken(user);

        return JwtAuthenticationResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .role(user.getRole())
                .build();
    }
}
