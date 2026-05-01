package com.hivemind.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.stereotype.Component;

/**
 * Creates secondary indexes on Cassandra tables at startup.
 * Spring Data Cassandra's CREATE_IF_NOT_EXISTS creates tables but not indexes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CassandraIndexInitializer implements CommandLineRunner
{
    private final CassandraOperations cassandraOperations;

    @Override
    public void run(String... args)
    {
        try
        {
            cassandraOperations.getCqlOperations().execute(
                "CREATE INDEX IF NOT EXISTS users_mobile_idx ON users (mobile_number)"
            );
            log.info("Cassandra index users_mobile_idx created or already exists");
        }
        catch (Exception e)
        {
            log.warn("Could not create Cassandra index: {}", e.getMessage());
        }
    }
}
