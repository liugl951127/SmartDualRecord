package com.minimax.dualrecord.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.text.SimpleDateFormat;

/**
 * Jackson 全局配置
 *
 * 解决问题:
 *  - FAIL_ON_EMPTY_BEANS: 防止 CGLIB 代理对象 (如 @ConfigurationProperties 的 $$beanFactory 字段) 抛错
 *  - 统一时间格式 ISO 8601
 *  - 忽略 null 字段减小响应体
 *  - 防止循环引用 (Hibernate 实体场景)
 *
 * @author MiniMax
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // ★ 关键: 不要因为 bean 没有 getter 就抛错
        //   这能避免 CGLIB 代理对象 (含 $$beanFactory 等字段) 序列化失败
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // 时间戳用 ISO 8601 字符串而非数字
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
        // 忽略 null 字段
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // 忽略循环引用
        mapper.disable(SerializationFeature.FAIL_ON_SELF_REFERENCES);
        return mapper;
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer customizer() {
        return builder -> builder
                .failOnUnknownProperties(false)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .featuresToDisable(
                        SerializationFeature.FAIL_ON_EMPTY_BEANS,
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        SerializationFeature.FAIL_ON_SELF_REFERENCES
                );
    }
}
