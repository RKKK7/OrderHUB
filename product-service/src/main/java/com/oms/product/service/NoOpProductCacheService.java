package com.oms.product.service;

import com.oms.product.dto.ProductDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * No-op implementation — active when app.cache.enabled=false (default).
 * Every read returns null (forces DB query), every write is ignored.
 * The app works identically, just without caching.
 */
@Service
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpProductCacheService implements ProductCacheService {

    @Override public ProductDTO getProduct(String productId) { return null; }
    @Override public void putProduct(String productId, ProductDTO product) { }
    @Override public String getList(String category, String sort, int page) { return null; }
    @Override public void putList(String category, String sort, int page, String json) { }
    @Override public String getSearch(String query, String sort, int page) { return null; }
    @Override public void putSearch(String query, String sort, int page, String json) { }
    @Override public String getCategories() { return null; }
    @Override public void putCategories(String json) { }
    @Override public void invalidateOnWrite(String productId) { }
}
