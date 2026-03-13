package com.tech.n.ai.domain.aurora.config;


import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;


@Profile("batch-domain")
@Configuration
public class BatchMyBatisConfig {

    @Value("${mybatis.config-location}")
    private String configLocation;

//    @Qualifier(value = "batchBusinessWriterDataSource")
    private final DataSource batchBusinessWriterDataSource;

//    @Qualifier(value = "batchBusinessReaderDataSource")
    private final DataSource batchBusinessReaderDataSource;


    public BatchMyBatisConfig(@Qualifier("batchBusinessWriterDataSource") DataSource batchBusinessWriterDataSource
                            , @Qualifier("batchBusinessReaderDataSource") DataSource batchBusinessReaderDataSource) {
        this.batchBusinessWriterDataSource = batchBusinessWriterDataSource;
        this.batchBusinessReaderDataSource = batchBusinessReaderDataSource;
    }


    @Bean
    @Primary
    public SqlSessionFactory batchSqlSessionWriterFactory(
        @Qualifier("batchBusinessWriterDataSource") DataSource batchBusinessWriterDataSource
        , ApplicationContext applicationContext)
        throws Exception {
        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(batchBusinessWriterDataSource);

        sqlSessionFactoryBean.setConfigLocation(applicationContext.getResource(configLocation));

        return sqlSessionFactoryBean.getObject();
    }

    @Bean
    @Primary
    public SqlSessionTemplate batchSqlSessionWriterTemplate(SqlSessionFactory batchSqlSessionWriterFactory)
        throws Exception {
        return new SqlSessionTemplate(batchSqlSessionWriterFactory);
    }

    @Bean(name = "batchSqlSessionReaderFactory")
    public SqlSessionFactory batchSqlSessionReaderFactory(
        @Qualifier("batchBusinessReaderDataSource") DataSource batchBusinessReaderDataSource
        , ApplicationContext applicationContext)
        throws Exception {
        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(batchBusinessReaderDataSource);

        sqlSessionFactoryBean.setConfigLocation(applicationContext.getResource(configLocation));

        return sqlSessionFactoryBean.getObject();
    }

    @Bean(name = "batchSqlSessionReaderTemplate")
    public SqlSessionTemplate batchSqlSessionReaderTemplate(
        @Qualifier("batchSqlSessionReaderFactory") SqlSessionFactory batchSqlSessionReaderFactory)
        throws Exception {
        return new SqlSessionTemplate(batchSqlSessionReaderFactory);
    }

}