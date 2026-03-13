package com.tech.n.ai.domain.aurora.config;


import com.querydsl.jpa.JPQLTemplates;
import com.querydsl.jpa.impl.JPAQueryFactory;

import com.tech.n.ai.domain.aurora.config.ApiDataSourceConfig;
import jakarta.persistence.EntityManager;

import jakarta.persistence.PersistenceContext;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import lombok.RequiredArgsConstructor;


@Configuration
@Profile("api-domain")
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = {"com.tech.n.ai.domain.aurora.repository"}
)
@EntityScan(value = {"com.tech.n.ai.domain.aurora.entity"})
@ComponentScan(basePackages = {"com.tech.n.ai.domain.aurora"})
@Import({
    ApiDataSourceConfig.class,
    com.tech.n.ai.domain.aurora.config.ApiMybatisConfig.class
})
@RequiredArgsConstructor
public class ApiDomainConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    @Primary
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }

    @Bean(name = "pastJpaQueryFactory")
    public JPAQueryFactory pastJpaQueryFactory() {
        return new JPAQueryFactory(JPQLTemplates.DEFAULT, entityManager);
    }

}
