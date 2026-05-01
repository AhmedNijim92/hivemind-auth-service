package com.hivemind.auth.service;

public interface IOtpService
{
    void sendOtp(String mobileNumber);

    boolean verifyOtp(String mobileNumber, String otp);

    void cleanupExpiredOtps();
}
