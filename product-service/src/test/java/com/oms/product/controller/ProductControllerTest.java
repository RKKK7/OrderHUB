package com.oms.product.controller;

import com.oms.product.dto.ApiException;
import com.oms.product.dto.ProductDTO;
import com.oms.product.event.StockEventPublisher;
import com.oms.product.model.Product;
import com.oms.product.repository.ProductRepository;
import com.oms.product.service.ProductCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock private ProductRepository productRepo;
    @Mock private ProductCacheService cache;
    @Mock private StockEventPublisher stockEventPublisher;

    @InjectMocks private ProductController controller;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product();
        sampleProduct.setId("prod-1");
        sampleProduct.setName("Widget");
        sampleProduct.setCategory("Electronics");
        sampleProduct.setPrice(new BigDecimal("29.99"));
        sampleProduct.setStockQuantity(50);
        sampleProduct.setLowStockThreshold(10);
        sampleProduct.setActive(true);
    }

    // ---- PUBLIC: listProducts ----

    @Test
    void listProducts_cacheMiss_queriesDbAndCaches() {
        when(cache.getList("all", "name", 1)).thenReturn(null);
        when(productRepo.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(sampleProduct));

        Map<String, Object> result = controller.listProducts("all", null, "name", 1, 12);

        assertEquals(1L, result.get("total"));
        assertEquals(1, result.get("page"));
        assertNotNull(result.get("products"));
        verify(cache).putList(eq("all"), eq("name"), eq(1), anyString());
    }

    @Test
    void listProducts_cacheHit_returnsWithoutDbQuery() {
        String json = "{\"products\":[],\"total\":0,\"pages\":0,\"page\":1}";
        when(cache.getList("all", "name", 1)).thenReturn(json);

        Map<String, Object> result = controller.listProducts("all", null, "name", 1, 12);

        assertEquals(0, result.get("total"));
        verify(productRepo, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void listProducts_withSearchParam_usesSearchCache() {
        when(cache.getSearch("widget", "name", 1)).thenReturn(null);
        when(productRepo.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(sampleProduct));

        Map<String, Object> result = controller.listProducts("all", "Widget", "name", 1, 12);

        verify(cache).getSearch("widget", "name", 1);
        verify(cache).putSearch(eq("widget"), eq("name"), eq(1), anyString());
    }

    // ---- PUBLIC: getCategories ----

    @Test
    void getCategories_cacheMiss_queriesDbAndCaches() {
        when(cache.getCategories()).thenReturn(null);
        when(productRepo.findDistinctCategories()).thenReturn(List.of("Electronics", "Books"));

        List<String> result = controller.getCategories();

        assertEquals(2, result.size());
        verify(cache).putCategories(anyString());
    }

    // ---- PUBLIC: getProduct ----

    @Test
    void getProduct_found_returnsUserDTO() {
        when(cache.getProduct("prod-1")).thenReturn(null);
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(sampleProduct));

        ProductDTO result = controller.getProduct("prod-1");

        assertEquals("Widget", result.getName());
        assertNull(result.getStockQuantity()); // user DTO hides exact stock
        verify(cache).putProduct(eq("prod-1"), any(ProductDTO.class));
    }

    @Test
    void getProduct_notFound_throws404() {
        when(cache.getProduct("missing")).thenReturn(null);
        when(productRepo.findById("missing")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> controller.getProduct("missing"));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getProduct_inactiveProduct_throws404() {
        sampleProduct.setActive(false);
        when(cache.getProduct("prod-1")).thenReturn(null);
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(sampleProduct));

        ApiException ex = assertThrows(ApiException.class, () -> controller.getProduct("prod-1"));
        assertEquals(404, ex.getStatus());
    }

    // ---- ADMIN: createProduct ----

    @Test
    void createProduct_nonAdmin_throws403() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.createProduct("USER", Map.of("name", "X", "category", "Y", "price", "10")));
        assertEquals(403, ex.getStatus());
    }

    @Test
    void createProduct_missingRequiredFields_throws400() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.createProduct("ADMIN", Map.of("name", "X")));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void createProduct_validAdmin_savesAndInvalidatesCache() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "New Widget");
        body.put("category", "Electronics");
        body.put("price", "19.99");
        body.put("stockQuantity", 100);

        when(productRepo.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId("new-id");
            return p;
        });

        ProductDTO result = controller.createProduct("ADMIN", body);

        assertEquals("New Widget", result.getName());
        verify(cache).invalidateOnWrite("new-id");
    }

    // ---- ADMIN: updateStock ----

    @Test
    void updateStock_setsStockAndPublishesStockUpdatedEvent() {
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(sampleProduct));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of("stockQuantity", 40);
        controller.updateStock("ADMIN", "prod-1", body);

        verify(stockEventPublisher).publish(eq("STOCK_UPDATED"), eq("prod-1"), eq("Widget"),
                eq(-10), eq(40), eq(10));
        // stock 40 > threshold 10, so no LOW_STOCK_ALERT
        verify(stockEventPublisher, never()).publish(eq("LOW_STOCK_ALERT"),
                anyString(), anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void updateStock_belowThreshold_publishesBothEvents() {
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(sampleProduct));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of("stockQuantity", 5);
        controller.updateStock("ADMIN", "prod-1", body);

        verify(stockEventPublisher).publish(eq("STOCK_UPDATED"), eq("prod-1"), eq("Widget"),
                eq(-45), eq(5), eq(10));
        verify(stockEventPublisher).publish(eq("LOW_STOCK_ALERT"), eq("prod-1"), eq("Widget"),
                eq(-45), eq(5), eq(10));
    }

    @Test
    void updateStock_negativeQuantity_throws400() {
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(sampleProduct));

        Map<String, Object> body = Map.of("stockQuantity", -1);
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.updateStock("ADMIN", "prod-1", body));
        assertEquals(400, ex.getStatus());
    }

    // ---- ADMIN: toggleStatus ----

    @Test
    void toggleStatus_deactivatesProduct() {
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(sampleProduct));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of("active", false);
        ProductDTO result = controller.toggleStatus("ADMIN", "prod-1", body);

        assertFalse(result.getActive());
        verify(cache).invalidateOnWrite("prod-1");
    }
}
