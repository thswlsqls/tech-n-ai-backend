package com.tech.n.ai.domain.aurora.config;


import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Profile("batch-domain")
@Configuration
public class BatchJpaTransactionConfig {

    private final DataSource batchMetaDataSource;
    private final DataSource batchBusinessReaderDataSource;
    private final DataSource batchBusinessWriterDataSource;
    private final LocalContainerEntityManagerFactoryBean primaryEMF;
    private final LocalContainerEntityManagerFactoryBean secondaryEMF;

    public BatchJpaTransactionConfig(@Qualifier("batchMetaDataSource") DataSource batchMetaDataSource
                                    , @Qualifier("batchBusinessReaderDataSource") DataSource batchBusinessReaderDataSource
                                    , @Qualifier("batchBusinessWriterDataSource") DataSource batchBusinessWriterDataSource
                                    , @Qualifier("primaryEMF") LocalContainerEntityManagerFactoryBean primaryEMF
                                    , @Qualifier("secondaryEMF") LocalContainerEntityManagerFactoryBean secondaryEMF
                                    ) {
        this.batchMetaDataSource = batchMetaDataSource;
        this.batchBusinessReaderDataSource = batchBusinessReaderDataSource;
        this.batchBusinessWriterDataSource = batchBusinessWriterDataSource;
        this.primaryEMF = primaryEMF;
        this.secondaryEMF = secondaryEMF;
    }


    @Primary
    @Bean(name="primaryPlatformTransactionManager")
    PlatformTransactionManager primaryPlatformTransactionManager() {
        DataSourceTransactionManager manager = new DataSourceTransactionManager();
        manager.setDataSource(batchMetaDataSource);
        manager.setNestedTransactionAllowed(true);
        return manager;
    }

    @Bean(name="businessWriterTransactionManager")
    PlatformTransactionManager secondaryWriterTransactionManager() {
        return new DataSourceTransactionManager(batchBusinessWriterDataSource);
    }

    @Bean(name="businessReaderTransactionManager")
    PlatformTransactionManager secondaryReaderTransactionManager() {
        return new DataSourceTransactionManager(batchBusinessReaderDataSource);
    }

    @Bean(name= "primaryJpaWriterTransactionManager")
    JpaTransactionManager primaryJpaWriterTransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setDataSource(batchBusinessWriterDataSource);
        transactionManager.setEntityManagerFactory(primaryEMF.getObject());
        transactionManager.setNestedTransactionAllowed(true);

        return transactionManager;
    }

    @Bean(name= "secondaryJpaReaderTransactionManager")
    JpaTransactionManager secondaryJpaReaderTransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setDataSource(batchBusinessReaderDataSource);
        transactionManager.setEntityManagerFactory(secondaryEMF.getObject());
        transactionManager.setNestedTransactionAllowed(true);

        return transactionManager;
    }

}
