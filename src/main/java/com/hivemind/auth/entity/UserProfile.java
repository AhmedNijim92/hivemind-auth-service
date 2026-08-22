package com.hivemind.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_profiles")
public class UserProfile
{
    @PrimaryKey("user_id")
    private UUID userId;

    @Column("mobile_number")
    private String mobileNumber;

    @Column("name")
    private String name;

    @Column("email")
    private String email;

    @Column("bio")
    private String bio;

    @Column("profile_picture_url")
    private String profilePictureUrl;

    @Column("cover_picture_url")
    private String coverPictureUrl;

    @Column("show_contact_info")
    private Boolean showContactInfo;

    @Column("created_at")
    private LocalDate createdAt;

    @Column("updated_at")
    private LocalDate updatedAt;
}
