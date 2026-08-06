package com.oms.product.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis is only configured when app.cache.enabled=true.
 * When false (local dev default), no Redis connection is attempted —
 * the app runs without caching, functionally identical, just uncached.
 */
@Configuration
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true")
@Import(RedisAutoConfiguration.class)
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
