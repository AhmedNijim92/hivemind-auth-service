package com.hivemind.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest
{
    private String name;
    private String email;
    private String bio;
    private String profilePictureUrl;
    private String coverPictureUrl;
    private Boolean showContactInfo;
}
