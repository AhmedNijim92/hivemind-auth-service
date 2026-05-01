package com.hivemind.auth.repository;

import com.hivemind.auth.entity.User;
import org.springframework.data.cassandra.repository.AllowFiltering;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends CassandraRepository<User, UUID>
{
    @AllowFiltering
    Optional<User> findByMobileNumber(String mobileNumber);

    @AllowFiltering
    Optional<User> findByUserId(UUID userId);
}
