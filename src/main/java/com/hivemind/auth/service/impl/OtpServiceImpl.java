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
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP Service using Vonage Verify v2 API for production SMS delivery.
 * 
 * Vonage Verify handles:
 * - Sender ID/number selection per country
 * - Carrier compliance
 * - Retry logic (SMS → Voice fallback)
 * - Rate limiting
 * 
 * In dev mode: logs OTP to console (no external calls).
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

    // Store Vonage Verify request IDs for each phone number
    private final ConcurrentHashMap<String, String> verifyRequests = new ConcurrentHashMap<>();

    @Override
    public void sendOtp(String mobileNumber)
    {
        if ("dev".equals(activeProfile))
        {
            String otp = generateOtp();
            String hashedOtp = passwordEncoder.encode(otp);
            saveOtpForUser(mobileNumber, hashedOtp);
            log.info("DEV MODE — OTP for {}: {}", mobileNumber, otp);
        }
        else
        {
            // Use Vonage Verify API — it generates & sends the OTP
            sendViaVonageVerify(mobileNumber);
        }
    }

    @Override
    public boolean verifyOtp(String mobileNumber, String otp)
    {
        if ("dev".equals(activeProfile))
        {
            // Dev mode: verify against locally stored hash
            Optional<User> userOpt = userRepository.findByMobileNumber(mobileNumber);
            if (userOpt.isEmpty()) return false;
            return passwordEncoder.matches(otp, userOpt.get().getOtp());
        }
        else
        {
            // Prod mode: verify via Vonage Verify API
            return verifyViaVonage(mobileNumber, otp);
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${otp.cleanup-interval-minutes:10}", timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void cleanupExpiredOtps()
    {
        log.info("Cleaning up expired OTPs");
        verifyRequests.clear();
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
     * Use Vonage Verify v2 API to send OTP.
     * POST https://api.nexmo.com/verify/json
     * Vonage handles sender selection, carrier compliance, and delivery.
     */
    private void sendViaVonageVerify(String mobileNumber)
    {
        try
        {
            RestTemplate restTemplate = new RestTemplate();

            // Strip '+' for Vonage format (expects: 46707518829)
            String toNumber = mobileNumber.replaceAll("[^0-9]", "");
            
            // Remove leading zeros after country code if present (e.g. 460707... -> 46707...)
            if (toNumber.startsWith("460")) {
                toNumber = "46" + toNumber.substring(3);
            }

            String url = "https://api.nexmo.com/verify/json";

            Map<String, String> body = new HashMap<>();
            body.put("api_key", vonageApiKey);
            body.put("api_secret", vonageApiSecret);
            body.put("number", toNumber);
            body.put("brand", "HiveMind");
            body.put("code_length", "6");

            log.info("Sending Vonage Verify to number: {} (original: {})", toNumber, mobileNumber);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null)
            {
                String status = (String) response.getBody().get("status");
                if ("0".equals(status))
                {
                    String requestId = (String) response.getBody().get("request_id");
                    verifyRequests.put(mobileNumber, requestId);
                    log.info("Vonage Verify sent to {}. Request ID: {}", mobileNumber, requestId);

                    // Also create/update user record so login flow works
                    ensureUserExists(mobileNumber);
                }
                else
                {
                    String errorText = (String) response.getBody().get("error_text");
                    log.error("Vonage Verify failed for {}: status={}, error={}", mobileNumber, status, errorText);
                    throw new RuntimeException("Failed to send verification code: " + errorText);
                }
            }
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("Failed to send OTP to {}: {}", mobileNumber, e.getMessage());
            throw new RuntimeException("Failed to send verification code. Please try again.");
        }
    }

    /**
     * Verify OTP via Vonage Verify check API.
     * POST https://api.nexmo.com/verify/check/json
     */
    private boolean verifyViaVonage(String mobileNumber, String otp)
    {
        String requestId = verifyRequests.get(mobileNumber);
        if (requestId == null)
        {
            log.warn("No Verify request found for {}. Falling back to local check.", mobileNumber);
            // Fallback to local hash check
            Optional<User> userOpt = userRepository.findByMobileNumber(mobileNumber);
            if (userOpt.isEmpty()) return false;
            return passwordEncoder.matches(otp, userOpt.get().getOtp());
        }

        try
        {
            RestTemplate restTemplate = new RestTemplate();

            String url = "https://api.nexmo.com/verify/check/json";

            Map<String, String> body = new HashMap<>();
            body.put("api_key", vonageApiKey);
            body.put("api_secret", vonageApiSecret);
            body.put("request_id", requestId);
            body.put("code", otp);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null)
            {
                String status = (String) response.getBody().get("status");
                if ("0".equals(status))
                {
                    log.info("OTP verified for {} via Vonage Verify", mobileNumber);
                    verifyRequests.remove(mobileNumber);
                    return true;
                }
                else
                {
                    log.warn("Vonage Verify check failed for {}: status={}", mobileNumber, status);
                    return false;
                }
            }
            return false;
        }
        catch (Exception e)
        {
            log.error("Vonage Verify check error for {}: {}", mobileNumber, e.getMessage());
            return false;
        }
    }

    private void ensureUserExists(String mobileNumber)
    {
        Optional<User> existingUser = userRepository.findByMobileNumber(mobileNumber);
        if (existingUser.isEmpty())
        {
            User newUser = new User();
            newUser.setUserId(UUID.randomUUID());
            newUser.setMobileNumber(mobileNumber);
            newUser.setRole("USER");
            newUser.setCreatedAt(LocalDate.now());
            userRepository.save(newUser);
        }
    }
}
