package com.hivemind.auth.repository;

import com.hivemind.auth.entity.UserProfile;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends CassandraRepository<UserProfile, UUID>
{
    @Query("SELECT * FROM user_profiles WHERE mobile_number = ?0 ALLOW FILTERING")
    Optional<UserProfile> findByMobileNumber(String mobileNumber);
}
