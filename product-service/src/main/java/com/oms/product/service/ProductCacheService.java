package com.oms.product.service;

import com.oms.product.dto.ProductDTO;

import java.util.List;

/**
 * Cache abstraction for product data.
 * Two implementations:
 *   - RedisProductCacheService (when app.cache.enabled=true)
 *   - NoOpProductCacheService  (when app.cache.enabled=false, local dev default)
 *
 * ProductService calls this interface — it doesn't know or care which
 * implementation is active. Swapping is fully transparent.
 */
public interface ProductCacheService {

    /** Get cached product by ID. Returns null on miss. */
    ProductDTO getProduct(String productId);

    /** Cache a single product by ID. */
    void putProduct(String productId, ProductDTO product);

    /** Get cached product list. Returns null on miss. */
    String getList(String category, String sort, int page);

    /** Cache a product list (serialized as JSON string). */
    void putList(String category, String sort, int page, String json);

    /** Get cached search results. Returns null on miss. */
    String getSearch(String query, String sort, int page);

    /** Cache search results. */
    void putSearch(String query, String sort, int page, String json);

    /** Get cached category list. Returns null on miss. */
    String getCategories();

    /** Cache category list. */
    void putCategories(String json);

    /**
     * Invalidate all caches related to a specific product write.
     * - Deletes the product's individual cache entry
     * - Bumps the global version counter (invalidates ALL list/search/category caches)
     */
    void invalidateOnWrite(String productId);
}
