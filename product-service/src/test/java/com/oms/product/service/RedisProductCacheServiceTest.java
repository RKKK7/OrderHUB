package com.oms.product.service;

import com.oms.product.dto.ProductDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisProductCacheServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private RedisProductCacheService cacheService;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        cacheService = new RedisProductCacheService(redis);
    }

    // ---- getProduct / putProduct ----

    @Test
    void getProduct_cacheHit_returnsDeserializedDTO() {
        String json = "{\"id\":\"p1\",\"name\":\"Widget\",\"price\":9.99}";
        when(valueOps.get("products:id:p1")).thenReturn(json);

        ProductDTO result = cacheService.getProduct("p1");

        assertNotNull(result);
        assertEquals("p1", result.getId());
        assertEquals("Widget", result.getName());
    }

    @Test
    void getProduct_cacheMiss_returnsNull() {
        when(valueOps.get("products:id:p1")).thenReturn(null);

        assertNull(cacheService.getProduct("p1"));
    }

    @Test
    void putProduct_serializesAndStoresWithTTL() {
        ProductDTO dto = new ProductDTO();
        dto.setId("p1");
        dto.setName("Widget");

        cacheService.putProduct("p1", dto);

        verify(valueOps).set(eq("products:id:p1"), anyString(), eq(Duration.ofMinutes(10)));
    }

    // ---- Versioned list cache ----

    @Test
    void getList_includesVersionInKey() {
        when(valueOps.get("products:cache-version")).thenReturn("5");
        when(valueOps.get("products:list:v5:all:name:1")).thenReturn("{\"products\":[]}");

        String result = cacheService.getList("all", "name", 1);

        assertNotNull(result);
    }

    @Test
    void getList_noVersionYet_initializesVersionTo1() {
        when(valueOps.get("products:cache-version")).thenReturn(null);

        cacheService.getList("all", "name", 1);

        verify(valueOps).set("products:cache-version", "1");
        verify(valueOps).get("products:list:v1:all:name:1");
    }

    // ---- invalidateOnWrite ----

    @Test
    void invalidateOnWrite_deletesProductKeyAndBumpsVersion() {
        cacheService.invalidateOnWrite("p1");

        verify(redis).delete("products:id:p1");
        verify(valueOps).increment("products:cache-version");
    }

    // ---- Search cache ----

    @Test
    void getSearch_usesVersionedKey() {
        when(valueOps.get("products:cache-version")).thenReturn("3");
        when(valueOps.get("products:search:v3:widget:name:1")).thenReturn(null);

        String result = cacheService.getSearch("widget", "name", 1);

        assertNull(result);
    }

    // ---- Category cache ----

    @Test
    void putCategories_storesWithVersionedKeyAndTTL() {
        when(valueOps.get("products:cache-version")).thenReturn("2");

        cacheService.putCategories("[\"Electronics\",\"Books\"]");

        verify(valueOps).set(eq("products:categories:v2"), eq("[\"Electronics\",\"Books\"]"),
                eq(Duration.ofMinutes(10)));
    }
}
