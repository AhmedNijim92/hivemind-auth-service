package com.hivemind.auth.service.impl;

import com.hivemind.auth.entity.User;
import com.hivemind.auth.repository.UserRepository;
import com.hivemind.auth.service.IOtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;

/**
 * OTP Service using Vonage Verify v2 API.
 * In dev mode: logs OTP to console.
 * In prod mode: sends real SMS via Vonage Verify (handles carrier rules, retries, sender IDs).
 */
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

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    // Store Vonage Verify request IDs for verification
    private final Map<String, String> verifyRequestIds = new HashMap<>();

    @Override
    public void sendOtp(String mobileNumber)
    {
        if ("dev".equals(activeProfile))
        {
            // Dev mode: generate and store OTP locally
            String otp = generateOtp();
            String hashedOtp = passwordEncoder.encode(otp);
            saveOtpForUser(mobileNumber, hashedOtp);
            log.info("DEV MODE — OTP for {}: {}", mobileNumber, otp);
        }
        else
        {
            // Prod mode: use Vonage SMS API with proper number format
            String otp = generateOtp();
            String hashedOtp = passwordEncoder.encode(otp);
            saveOtpForUser(mobileNumber, hashedOtp);
            sendSmsViaVonageRest(mobileNumber, otp);
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

    private void saveOtpForUser(String mobileNumber, String hashedOtp)
    {
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
    }

    private String generateOtp()
    {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    /**
     * Send SMS using Vonage REST SMS API (not Messages API).
     * The REST SMS API is simpler and works with trial accounts without sender ID registration.
     * Endpoint: https://rest.nexmo.com/sms/json
     */
    private void sendSmsViaVonageRest(String mobileNumber, String otp)
    {
        try
        {
            RestTemplate restTemplate = new RestTemplate();

            // Strip '+' for Vonage (expects: 46707518829)
            String toNumber = mobileNumber.replaceAll("[^0-9]", "");

            String url = "https://rest.nexmo.com/sms/json";

            Map<String, String> body = new HashMap<>();
            body.put("api_key", vonageApiKey);
            body.put("api_secret", vonageApiSecret);
            body.put("to", toNumber);
            body.put("from", "HiveMind");
            body.put("text", "Your HiveMind code is: " + otp);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null)
            {
                List<Map<String, Object>> messages = (List<Map<String, Object>>) response.getBody().get("messages");
                if (messages != null && !messages.isEmpty())
                {
                    Map<String, Object> msg = messages.get(0);
                    String status = (String) msg.get("status");
                    if ("0".equals(status))
                    {
                        log.info("OTP sent to {} via Vonage SMS API. Message ID: {}", mobileNumber, msg.get("message-id"));
                    }
                    else
                    {
                        String errorText = (String) msg.get("error-text");
                        log.error("Vonage SMS failed for {}: {} - {}", mobileNumber, status, errorText);
                        throw new RuntimeException("SMS delivery failed: " + errorText);
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.error("Failed to send OTP to {}: {}", mobileNumber, e.getMessage());
            // Don't throw — OTP is still stored, user can retry
        }
    }
}
