package com.tech.n.ai.domain.aurora.config;

import javax.sql.DataSource;

import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;


@Configuration
@Profile("api-domain")
@RequiredArgsConstructor
public class ApiMybatisConfig {

    @Value("${mybatis.config-location}")
    private String configLocation;

    @Qualifier(value = "apiWriterDataSource")
    DataSource apiWriterDataSource;

    @Qualifier(value = "apiReaderDataSource")
    DataSource apiReaderDataSource;


    public ApiMybatisConfig(@Qualifier("apiWriterDataSource") DataSource apiWriterDataSource
                          , @Qualifier("apiReaderDataSource") DataSource apiReaderDataSource) {
        this.apiWriterDataSource = apiWriterDataSource;
        this.apiReaderDataSource = apiReaderDataSource;
    }

    @Bean(name = "apiSqlSessionWriterFactory")
    public SqlSessionFactory apiSqlSessionWriterFactory(
        @Qualifier("apiWriterDataSource") DataSource apiWriterDataSource
        , ApplicationContext applicationContext)
        throws Exception {
        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(apiWriterDataSource);

        sqlSessionFactoryBean.setConfigLocation(applicationContext.getResource(configLocation));

        return sqlSessionFactoryBean.getObject();
    }

    @Bean(name = "apiSqlSessionWriterTemplate")
    public SqlSessionTemplate apiSqlSessionWriterTemplate(SqlSessionFactory apiSqlSessionWriterFactory)
        throws Exception {
        return new SqlSessionTemplate(apiSqlSessionWriterFactory);
    }

    @Bean(name = "apiSqlSessionReaderFactory")
    public SqlSessionFactory apiSqlSessionReaderFactory(
        @Qualifier("apiReaderDataSource") DataSource apiReaderDataSource
        , ApplicationContext applicationContext)
        throws Exception {
        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(apiReaderDataSource);

        sqlSessionFactoryBean.setConfigLocation(applicationContext.getResource(configLocation));

        return sqlSessionFactoryBean.getObject();
    }

    @Bean(name = "apiSqlSessionReaderTemplate")
    public SqlSessionTemplate apiSqlSessionReaderTemplate(
        @Qualifier("apiSqlSessionReaderFactory") SqlSessionFactory apiSqlSessionReaderFactory)
        throws Exception {
        return new SqlSessionTemplate(apiSqlSessionReaderFactory);
    }

}