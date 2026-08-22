package com.hivemind.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("follows")
public class Follow
{
    @PrimaryKeyColumn(name = "follower_id", type = PrimaryKeyType.PARTITIONED)
    private UUID followerId;

    @PrimaryKeyColumn(name = "following_id", type = PrimaryKeyType.CLUSTERED)
    private UUID followingId;

    @Column("created_at")
    private LocalDateTime createdAt;
}
