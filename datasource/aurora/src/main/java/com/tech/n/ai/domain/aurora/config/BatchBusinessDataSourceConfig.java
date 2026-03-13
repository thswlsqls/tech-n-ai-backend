package com.tech.n.ai.domain.aurora.config;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Profile("batch-domain")
@Configuration
@RequiredArgsConstructor
public class BatchBusinessDataSourceConfig {

    private final Environment env;

    @Bean(name="batchWriterHikariConfig")
    @ConfigurationProperties(prefix = "spring.datasource.batch.writer.hikari")
    public HikariConfig batchWriterHikariConfig() {
        return new HikariConfig();
    }

    @Bean(name="batchReaderHikariConfig")
    @ConfigurationProperties(prefix = "spring.datasource.batch.reader.hikari")
    public HikariConfig batchReaderHikariConfig() {
        return new HikariConfig();
    }


    @Bean(name= "batchBusinessWriterDataSource")
    public DataSource batchBusinessWriterDataSource(@Qualifier("batchWriterHikariConfig") HikariConfig batchWriterHikariConfig) {

        // writer
        batchWriterHikariConfig.setPoolName("BATCH-WRITER");
        batchWriterHikariConfig.setJdbcUrl(env.getProperty("spring.datasource.writer.business.url"));
        batchWriterHikariConfig.setUsername(env.getProperty("spring.datasource.writer.business.username"));
        batchWriterHikariConfig.setPassword(env.getProperty("spring.datasource.writer.business.password"));
        DataSource writerDataSource = new HikariDataSource(batchWriterHikariConfig);
        log.warn("Will be WORK WITH WRITER");

        return writerDataSource;
    }

    @Bean(name= "batchBusinessReaderDataSource")
    public DataSource batchBusinessReaderDataSource(@Qualifier("batchReaderHikariConfig") HikariConfig batchReaderHikariConfig) {

        // reader
        batchReaderHikariConfig.setReadOnly(true);
        batchReaderHikariConfig.setPoolName("BATCH-READER");
        batchReaderHikariConfig.setJdbcUrl(env.getProperty("spring.datasource.reader.business.url"));
        batchReaderHikariConfig.setUsername(env.getProperty("spring.datasource.reader.business.username"));
        batchReaderHikariConfig.setPassword(env.getProperty("spring.datasource.reader.business.password"));
        DataSource readerDataSource = new HikariDataSource(batchReaderHikariConfig);
        log.warn("Will be WORK WITH READER");

        return readerDataSource;
    }
}
