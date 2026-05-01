package com.hivemind.auth.service;

import com.hivemind.auth.dto.JwtAuthenticationResponse;
import com.hivemind.auth.dto.SigninRequest;
import com.hivemind.common.dto.UserDto;

public interface IAuthenticationService
{
    void sendOtp(String mobileNumber);

    JwtAuthenticationResponse signin(SigninRequest request);

    JwtAuthenticationResponse createUser(UserDto userDto);

    JwtAuthenticationResponse createAdmin(UserDto userDto);
}
