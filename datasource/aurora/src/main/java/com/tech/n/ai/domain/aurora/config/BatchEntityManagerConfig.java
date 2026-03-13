package com.tech.n.ai.domain.aurora.config;


import java.util.Properties;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Profile("batch-domain")
@Configuration
@EnableJpaRepositories(basePackages = {
    "com.tech.n.ai.domain.aurora.repository",
}
    , transactionManagerRef = "jpaTransactionManagerAutoCommitF"
    , entityManagerFactoryRef = "secondaryEMF")
@EntityScan(basePackages = {
    "com.tech.n.ai.domain.aurora.entity"
})
@ComponentScan(basePackages = {
    "com.tech.n.ai.domain.aurora",
})
public class BatchEntityManagerConfig {

    private final Environment env;

    @Qualifier(value = "batchBusinessWriterDataSource")
    DataSource batchBusinessWriterDataSource;

    @Qualifier(value = "batchBusinessReaderDataSource")
    DataSource batchBusinessReaderDataSource;

    public BatchEntityManagerConfig(Environment env
        , @Qualifier("batchBusinessWriterDataSource") DataSource batchBusinessWriterDataSource
        , @Qualifier("batchBusinessReaderDataSource") DataSource batchBusinessReaderDataSource)
    {
        this.env = env;
        this.batchBusinessWriterDataSource = batchBusinessWriterDataSource;
        this.batchBusinessReaderDataSource = batchBusinessReaderDataSource;
    }

    @Bean(name = "primaryEMF")
    @Primary
    public LocalContainerEntityManagerFactoryBean primaryEMF() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(batchBusinessWriterDataSource);
        em.setPackagesToScan(new String[] {
            "com.tech.n.ai.domain.aurora.entity",
        });
        em.setPersistenceUnitName("batch-writer-primary");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        // Hibernate 6.x 자동 Dialect 감지 사용 - 명시적 설정 제거
        vendorAdapter.setShowSql(false);
        em.setJpaVendorAdapter(vendorAdapter);

        Properties properties = new Properties();
        properties = setProperties(properties, true);

        em.setJpaProperties(properties);
        em.afterPropertiesSet();

        return em;
    }

    @Bean(name = "secondaryEMF")
    public LocalContainerEntityManagerFactoryBean secondaryEMF() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(batchBusinessWriterDataSource);
        em.setPackagesToScan(new String[] {
           "com.tech.n.ai.domain.aurora.entity",
            });
        em.setPersistenceUnitName("batch-writer-secondary");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        // Hibernate 6.x 자동 Dialect 감지 사용 - 명시적 설정 제거
        vendorAdapter.setShowSql(false);
        em.setJpaVendorAdapter(vendorAdapter);

        Properties properties = new Properties();
        properties = setProperties(properties, true);

        em.setJpaProperties(properties);
        em.afterPropertiesSet();

        return em;
    }
    

    public Properties setProperties(Properties properties, boolean isAutoCommit) {
        properties.setProperty("spring.jpa.open-in-view", env.getProperty("spring.jpa.batch.open-in-view"));
        properties.setProperty("spring.jpa.generate-ddl", env.getProperty("spring.jpa.batch.generate-ddl"));
        properties.setProperty("spring.jpa.database", env.getProperty("spring.jpa.batch.database"));
        // spring.jpa.database-platform 제거 - Hibernate 6.x 자동 감지 사용

        properties.setProperty("hibernate.ddl-auto", env.getProperty("spring.jpa.batch.hibernate.ddl-auto"));

        properties.setProperty("hibernate.implicit_naming_strategy", env.getProperty("spring.jpa.batch.properties.hibernate.implicit_naming_strategy"));
        properties.setProperty("hibernate.physical_naming_strategy", env.getProperty("spring.jpa.batch.properties.hibernate.physical_naming_strategy"));

        // hibernate.dialect 제거 - Hibernate 6.x 자동 감지 사용
        properties.setProperty("hibernate.hbm2ddl.auto", env.getProperty("spring.jpa.batch.hibernate.ddl-auto"));

        properties.setProperty("hibernate.storage_engine", env.getProperty("spring.jpa.batch.properties.hibernate.storage_engine"));
        properties.setProperty("hibernate.hbm2ddl.import_files_sql_extractor", env.getProperty("spring.jpa.batch.properties.hibernate.hbm2ddl.import_files_sql_extractor"));
        properties.setProperty("hibernate.default_batch_fetch_size", env.getProperty("spring.jpa.batch.properties.hibernate.default_batch_fetch_size"));
        properties.setProperty("hibernate.format_sql", env.getProperty("spring.jpa.batch.properties.hibernate.format_sql"));
        properties.setProperty("hibernate.highlight_sql", env.getProperty("spring.jpa.batch.properties.hibernate.highlight_sql"));
        properties.setProperty("hibernate.use_sql_comments", env.getProperty("spring.jpa.batch.properties.hibernate.use_sql_comments"));

        properties.setProperty("hibernate.connection.provider_disables_autocommit", isAutoCommit ? "true" : "false");

        properties.setProperty("hibernate.globally_quoted_identifiers", env.getProperty("spring.jpa.batch.properties.hibernate.globally_quoted_identifiers"));
        properties.setProperty("hibernate.order_inserts", env.getProperty("spring.jpa.batch.properties.hibernate.order_inserts"));
        properties.setProperty("hibernate.order_updates", env.getProperty("spring.jpa.batch.properties.hibernate.order_updates"));
        properties.setProperty("hibernate.jdbc.batch_size", env.getProperty("spring.jpa.batch.properties.hibernate.jdbc.batch_size"));
        properties.setProperty("hibernate.jdbc.time_zone", env.getProperty("spring.jpa.batch.properties.hibernate.jdbc.time_zone"));

        properties.setProperty("spring.jpa.show-sql", env.getProperty("spring.jpa.batch.show-sql"));

        return properties;
    }
}
