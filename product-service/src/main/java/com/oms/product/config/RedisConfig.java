package com.oms.product.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Redis is auto-configured by Spring Boot when CACHE_ENABLED=true.
 * This class exists only as a marker — Spring Boot's RedisAutoConfiguration
 * handles StringRedisTemplate creation automatically.
 */
@Configuration
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true")
public class RedisConfig {
    // No custom beans needed — Spring Boot auto-configures StringRedisTemplate
}