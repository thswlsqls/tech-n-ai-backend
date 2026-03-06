package com.tech.n.ai.common.core.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Jackson 전역 설정
 *
 * TSID(Long) 값이 JavaScript Number.MAX_SAFE_INTEGER(2^53-1)를 초과하여
 * 프론트엔드에서 정밀도 손실이 발생하는 문제를 방지하기 위해
 * Long 타입을 문자열로 직렬화합니다.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("LongToString");
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            builder.addModule(module);
        };
    }
}
