package com.hivemind.auth.service;

import com.hivemind.auth.dto.UpdateProfileRequest;
import com.hivemind.auth.dto.UserProfileDto;

import java.util.List;
import java.util.UUID;

public interface IUserService
{
    UserProfileDto getUserById(UUID userId);

    UserProfileDto updateProfile(UUID userId, UpdateProfileRequest request);

    void followUser(UUID followerId, UUID targetUserId);

    void unfollowUser(UUID followerId, UUID targetUserId);

    List<UserProfileDto> getFollowers(UUID userId);

    List<UserProfileDto> getFollowing(UUID userId);

    List<UserProfileDto> searchUsers(String query);

    void createUserProfile(UUID userId, String mobileNumber, String name, String email);
}
