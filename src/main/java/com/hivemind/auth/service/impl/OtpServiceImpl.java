package com.hivemind.auth.service.impl;

import com.hivemind.auth.entity.User;
import com.hivemind.auth.repository.UserRepository;
import com.hivemind.auth.service.IOtpService;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
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

    @Value ("${twilio.account.sid}")
    private String twilioAccountSid;

    @Value ("${twilio.auth.token}")
    private String twilioAuthToken;

    @Value ("${twilio.phone.number}")
    private String twilioPhoneNumber;

    @Value ("${spring.profiles.active:dev}")
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
            log.info("OTP for {}: {}", mobileNumber, otp);
        }
        else
        {
            sendSms(mobileNumber, otp);
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
    @Scheduled (fixedDelayString = "${otp.cleanup-interval-minutes:10}", timeUnit = java.util.concurrent.TimeUnit.MINUTES)
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

    private void sendSms(String mobileNumber, String otp)
    {
        try
        {
            Twilio.init(twilioAccountSid, twilioAuthToken);
            Message.creator(
                    new PhoneNumber(mobileNumber),
                    new PhoneNumber(twilioPhoneNumber),
                    "Your HiveMind OTP is: " + otp
            ).create();
            log.info("OTP sent to {}", mobileNumber);
        }
        catch (Exception e)
        {
            log.error("Failed to send OTP: {}", e.getMessage());
        }
    }
}
