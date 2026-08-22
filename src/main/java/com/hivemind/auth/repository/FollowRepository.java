package com.hivemind.auth.repository;

import com.hivemind.auth.entity.Follow;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FollowRepository extends CassandraRepository<Follow, Object>
{
    @Query("SELECT * FROM follows WHERE follower_id = ?0")
    List<Follow> findByFollowerId(UUID followerId);

    @Query("SELECT * FROM follows WHERE following_id = ?0 ALLOW FILTERING")
    List<Follow> findByFollowingId(UUID followingId);

    @Query("SELECT * FROM follows WHERE follower_id = ?0 AND following_id = ?1")
    Optional<Follow> findByFollowerIdAndFollowingId(UUID followerId, UUID followingId);
}
