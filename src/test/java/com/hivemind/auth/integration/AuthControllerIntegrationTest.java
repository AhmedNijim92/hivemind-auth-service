package com.hivemind.auth.integration;

import com.hivemind.auth.dto.JwtAuthenticationResponse;
import com.hivemind.common.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for auth endpoints.
 * Tests run against the full Spring context in dev profile (OTP logged, not sent).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class AuthControllerIntegrationTest
{
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createUser_shouldReturnJwtResponse()
    {
        UserDto request = new UserDto();
        request.setMobileNumber("+1234567890");
        request.setName("Test User");
        request.setEmail("test@example.com");

        ResponseEntity<JwtAuthenticationResponse> response = restTemplate.postForEntity(
                url("/api/v1/auth/createUser"), request, JwtAuthenticationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotBlank();
        assertThat(response.getBody().getUserId()).isNotNull();
        assertThat(response.getBody().getRole()).isEqualTo("USER");
        assertThat(response.getBody().getName()).isEqualTo("Test User");
    }

    @Test
    void sendOtp_shouldReturn200()
    {
        // First create a user
        UserDto createReq = new UserDto();
        createReq.setMobileNumber("+9876543210");
        createReq.setName("OTP Test");
        createReq.setEmail("otp@test.com");
        restTemplate.postForEntity(url("/api/v1/auth/createUser"), createReq, JwtAuthenticationResponse.class);

        // Now send OTP
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"mobileNumber\": \"+9876543210\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/v1/auth/sendOtp"), entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void signin_withInvalidOtp_shouldReturn500()
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"mobileNumber\": \"+1111111111\", \"otp\": \"000000\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/v1/auth/signin"), entity, String.class);

        // Should fail — user doesn't exist or OTP is wrong
        assertThat(response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError()).isTrue();
    }

    private String url(String path)
    {
        return "http://localhost:" + port + path;
    }
}
