package com.hivemind.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SigninRequest
{

    @NotBlank (message = "Mobile number is required")
    private String mobileNumber;

    @NotBlank (message = "OTP is required")
    private String otp;
}
