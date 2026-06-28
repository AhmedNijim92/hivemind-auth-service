package com.hivemind.auth.service.impl;

import com.hivemind.auth.entity.User;
import com.hivemind.auth.repository.UserRepository;
import com.hivemind.auth.service.IOtpService;
import com.vonage.client.VonageClient;
import com.vonage.client.messages.sms.SmsTextRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements IOtpService
{
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${vonage.api.key}")
    private String vonageApiKey;

    @Value("${vonage.api.secret}")
    private String vonageApiSecret;

    @Value("${vonage.from:HiveMind}")
    private String vonageFrom;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Override
    public void sendOtp(String mobileNumber)
    {
        String otp = generateOtp();
        String hashedOtp = passwordEncoder.encode(otp);

        Optional<User> existingUser = userRepository.findByMobileNumber(mobileNumber);
        if (existingUser.isPresent())
        {
            User user = existingUser.get();
            user.setOtp(hashedOtp);
            userRepository.save(user);
        }
        else
        {
            User newUser = new User();
            newUser.setUserId(UUID.randomUUID());
            newUser.setMobileNumber(mobileNumber);
            newUser.setOtp(hashedOtp);
            newUser.setRole("USER");
            newUser.setCreatedAt(LocalDate.now());
            userRepository.save(newUser);
        }

        if ("dev".equals(activeProfile))
        {
            log.info("DEV MODE — OTP for {}: {}", mobileNumber, otp);
        }
        else
        {
            sendSmsViaVonage(mobileNumber, otp);
        }
    }

    @Override
    public boolean verifyOtp(String mobileNumber, String otp)
    {
        Optional<User> userOpt = userRepository.findByMobileNumber(mobileNumber);
        if (userOpt.isEmpty())
        {
            return false;
        }
        return passwordEncoder.matches(otp, userOpt.get().getOtp());
    }

    @Override
    @Scheduled(fixedDelayString = "${otp.cleanup-interval-minutes:10}", timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void cleanupExpiredOtps()
    {
        log.info("Cleaning up expired OTPs");
    }

    private String generateOtp()
    {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    private void sendSmsViaVonage(String mobileNumber, String otp)
    {
        try
        {
            VonageClient client = VonageClient.builder()
                    .apiKey(vonageApiKey)
                    .apiSecret(vonageApiSecret)
                    .build();

            // Strip '+' from number for Vonage (expects digits only e.g. 46707518829)
            String toNumber = mobileNumber.replace("+", "");

            var response = client.getMessagesClient().sendMessage(
                    SmsTextRequest.builder()
                            .from(vonageFrom)
                            .to(toNumber)
                            .text("Your HiveMind verification code is: " + otp + "\n\nDo not share this code.")
                            .build()
            );

            log.info("OTP sent to {} via Vonage. Message ID: {}", mobileNumber, response.getMessageUuid());
        }
        catch (Exception e)
        {
            log.error("Failed to send OTP via Vonage to {}: {}", mobileNumber, e.getMessage());
            throw new RuntimeException("Failed to send verification code. Please try again.");
        }
    }
}
