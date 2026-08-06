package com.oms.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.product.dto.ProductDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed cache with versioned cache-busting.
 *
 * Key insight: list/search/category caches include a version number in their key.
 * On any product write, the version counter is incremented — all old keys become
 * unreachable (nothing ever asks for v7 when version is v8). Old keys expire
 * naturally via TTL. This is O(1) invalidation regardless of how many cached
 * search queries exist.
 *
 * Single-product caches use direct DEL — one deterministic key, no versioning needed.
 */
@Service
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true")
public class RedisProductCacheService implements ProductCacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    private static final String VERSION_KEY = "products:cache-version";
    private static final Duration PRODUCT_TTL = Duration.ofMinutes(10);
    private static final Duration LIST_TTL = Duration.ofMinutes(5);
    private static final Duration SEARCH_TTL = Duration.ofMinutes(2);
    private static final Duration CATEGORY_TTL = Duration.ofMinutes(10);

    public RedisProductCacheService(StringRedisTemplate redis) {
        this.redis = redis;
        this.mapper = new ObjectMapper();
    }

    private long getVersion() {
        String val = redis.opsForValue().get(VERSION_KEY);
        if (val == null) {
            redis.opsForValue().set(VERSION_KEY, "1");
            return 1L;
        }
        return Long.parseLong(val);
    }

    // ---------- Single product cache ----------

    @Override
    public ProductDTO getProduct(String productId) {
        try {
            String json = redis.opsForValue().get("products:id:" + productId);
            return json != null ? mapper.readValue(json, ProductDTO.class) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void putProduct(String productId, ProductDTO product) {
        try {
            String json = mapper.writeValueAsString(product);
            redis.opsForValue().set("products:id:" + productId, json, PRODUCT_TTL);
        } catch (Exception e) {
            // Cache write failure is non-critical — just log and continue
            System.err.println("Cache put failed for product " + productId + ": " + e.getMessage());
        }
    }

    // ---------- Versioned list cache ----------

    @Override
    public String getList(String category, String sort, int page) {
        String key = "products:list:v" + getVersion() + ":" + category + ":" + sort + ":" + page;
        return redis.opsForValue().get(key);
    }

    @Override
    public void putList(String category, String sort, int page, String json) {
        String key = "products:list:v" + getVersion() + ":" + category + ":" + sort + ":" + page;
        redis.opsForValue().set(key, json, LIST_TTL);
    }

    // ---------- Versioned search cache ----------

    @Override
    public String getSearch(String query, String sort, int page) {
        String key = "products:search:v" + getVersion() + ":" + query + ":" + sort + ":" + page;
        return redis.opsForValue().get(key);
    }

    @Override
    public void putSearch(String query, String sort, int page, String json) {
        String key = "products:search:v" + getVersion() + ":" + query + ":" + sort + ":" + page;
        redis.opsForValue().set(key, json, SEARCH_TTL);
    }

    // ---------- Versioned category cache ----------

    @Override
    public String getCategories() {
        return redis.opsForValue().get("products:categories:v" + getVersion());
    }

    @Override
    public void putCategories(String json) {
        redis.opsForValue().set("products:categories:v" + getVersion(), json, CATEGORY_TTL);
    }

    // ---------- Invalidation ----------

    @Override
    public void invalidateOnWrite(String productId) {
        // Delete specific product cache
        redis.delete("products:id:" + productId);
        // Bump version — all list/search/category caches become unreachable
        redis.opsForValue().increment(VERSION_KEY);
    }
}
