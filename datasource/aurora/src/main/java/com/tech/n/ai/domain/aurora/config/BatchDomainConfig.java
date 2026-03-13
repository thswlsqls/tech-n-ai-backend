package com.tech.n.ai.domain.aurora.config;


import jakarta.persistence.EntityManager;

import jakarta.persistence.PersistenceContext;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import lombok.RequiredArgsConstructor;


@Profile("batch-domain")
@Configuration
@EntityScan(value = {"com.tech.n.ai.domain.aurora.entity"})
@ComponentScan(basePackages = {"com.tech.n.ai.domain.aurora"})
@Import({
    BatchMetaDataSourceConfig.class,
    BatchBusinessDataSourceConfig.class,    
    BatchEntityManagerConfig.class,
    BatchJpaTransactionConfig.class,
    BatchMyBatisConfig.class
})
@RequiredArgsConstructor
public class BatchDomainConfig {

    @PersistenceContext
    private EntityManager entityManager;

//    @Bean
//    @Primary
//    public JPAQueryFactory jpaQueryFactory() {
//        return new JPAQueryFactory(entityManager);
//    }
//
//    @Bean(name="sacondaryJPAQueryFactory")
//    public JPAQueryFactory sacondaryJPAQueryFactory() {
//        return new JPAQueryFactory(entityManager);
//    }

}
