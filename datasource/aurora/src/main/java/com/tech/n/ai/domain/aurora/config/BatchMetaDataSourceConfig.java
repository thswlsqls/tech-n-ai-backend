package com.tech.n.ai.domain.aurora.config;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Profile("batch-domain")
@Configuration
@RequiredArgsConstructor
public class BatchMetaDataSourceConfig {

    private final Environment env;

    @Bean(name="batchMetaHikariConfig")
    @ConfigurationProperties(prefix = "spring.datasource.batch.meta.hikari")
    public HikariConfig batchMetaHikariConfig() {
        return new HikariConfig();
    }


    @Primary
    @Bean(name= "batchMetaDataSource")
    public DataSource batchMetaDataSource(@Qualifier("batchMetaHikariConfig") HikariConfig batchMetaHikariConfig) {

        // writer
        batchMetaHikariConfig.setPoolName("BATCH-META-WRITER");
        batchMetaHikariConfig.setJdbcUrl(env.getProperty("spring.datasource.writer.meta.url"));
        batchMetaHikariConfig.setUsername(env.getProperty("spring.datasource.writer.meta.username"));
        batchMetaHikariConfig.setPassword(env.getProperty("spring.datasource.writer.meta.password"));
        DataSource batchMetaDataSource = new HikariDataSource(batchMetaHikariConfig);
        log.warn("manage batch meta data only");
        log.warn("Will be WORK WITH WRITER");

        return batchMetaDataSource;
    }

}