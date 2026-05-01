package com.hivemind.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.config.SchemaAction;
import org.springframework.data.cassandra.core.cql.keyspace.CreateKeyspaceSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.KeyspaceOption;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

import java.util.Collections;
import java.util.List;

@Configuration
@EnableCassandraRepositories(basePackages = "com.hivemind.auth.repository")
public class CassandraConfig extends AbstractCassandraConfiguration
{
    @Value("${spring.data.cassandra.keyspace-name:auth_keyspace}")
    private String keyspaceName;

    @Value("${spring.data.cassandra.contact-points:localhost}")
    private String contactPoints;

    @Value("${spring.data.cassandra.port:9042}")
    private int port;

    @Value("${spring.data.cassandra.local-datacenter:datacenter1}")
    private String localDatacenter;

    @Override
    protected String getKeyspaceName() { return keyspaceName; }

    @Override
    protected String getContactPoints() { return contactPoints; }

    @Override
    protected int getPort() { return port; }

    @Override
    protected String getLocalDataCenter() { return localDatacenter; }

    @Override
    public SchemaAction getSchemaAction() { return SchemaAction.CREATE_IF_NOT_EXISTS; }

    @Override
    public String[] getEntityBasePackages() { return new String[]{"com.hivemind.auth.entity"}; }

    @Override
    protected List<CreateKeyspaceSpecification> getKeyspaceCreations()
    {
        return Collections.singletonList(
            CreateKeyspaceSpecification.createKeyspace(keyspaceName)
                .ifNotExists()
                .with(KeyspaceOption.DURABLE_WRITES, true)
                .withSimpleReplication(1)
        );
    }
}
