package com.tech.n.ai.domain.aurora.annotation;

import com.tech.n.ai.domain.aurora.generator.TsidGenerator;
import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * TSID Primary Key를 위한 커스텀 어노테이션
 * Hibernate 6.5+ 방식으로 @IdGeneratorType 사용
 */
@IdGeneratorType(TsidGenerator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tsid {
}
