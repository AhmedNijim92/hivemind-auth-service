package com.hivemind.auth.controller;

import com.hivemind.auth.dto.JwtAuthenticationResponse;
import com.hivemind.auth.dto.SendOtpDto;
import com.hivemind.auth.dto.SigninRequest;
import com.hivemind.auth.service.IAuthenticationService;
import com.hivemind.common.dto.ApiResponse;
import com.hivemind.common.dto.UserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping ("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController
{

    private final IAuthenticationService authenticationService;

    @PostMapping ("/sendOtp")
    public ResponseEntity<ApiResponse> sendOtp(@Valid @RequestBody SendOtpDto dto)
    {
        authenticationService.sendOtp(dto.getMobileNumber());
        return ResponseEntity.ok(new ApiResponse("OTP sent successfully"));
    }

    @PostMapping ("/signin")
    public ResponseEntity<JwtAuthenticationResponse> signin(@Valid @RequestBody SigninRequest request)
    {
        return ResponseEntity.ok(authenticationService.signin(request));
    }

    @PostMapping ("/createUser")
    public ResponseEntity<JwtAuthenticationResponse> createUser(@Valid @RequestBody UserDto userDto)
    {
        return ResponseEntity.ok(authenticationService.createUser(userDto));
    }

    @PostMapping ("/createAdmin")
    @PreAuthorize ("hasRole('ADMIN')")
    public ResponseEntity<JwtAuthenticationResponse> createAdmin(@Valid @RequestBody UserDto userDto)
    {
        return ResponseEntity.ok(authenticationService.createAdmin(userDto));
    }
}
