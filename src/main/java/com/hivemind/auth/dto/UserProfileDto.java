package com.hivemind.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto
{
    private UUID userId;
    private String mobileNumber;
    private String name;
    private String email;
    private String bio;
    private String profilePictureUrl;
    private String coverPictureUrl;
    private Boolean showContactInfo;
    private LocalDate createdAt;
}
