package com.oms.product.controller;

import com.oms.product.dto.ApiException;
import com.oms.product.dto.ProductDTO;
import com.oms.product.model.Product;
import com.oms.product.repository.ProductRepository;
import com.oms.product.service.NoOpProductCacheService;
import com.oms.product.service.ProductCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ProductController admin operations and product detail retrieval.
 * Uses NoOpProductCacheService (cache disabled) so every test hits the mock repo.
 */
@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock private ProductRepository productRepo;

    private ProductController controller;

    @BeforeEach
    void setUp() {
        // Use NoOp cache — tests verify business logic, not caching
        ProductCacheService cache = new NoOpProductCacheService();
        controller = new ProductController(productRepo, cache);
    }

    private Product sampleProduct() {
        Product p = new Product();
        p.setId("prod-1");
        p.setName("Laptop");
        p.setCategory("electronics");
        p.setPrice(BigDecimal.valueOf(49999));
        p.setStockQuantity(10);
        p.setLowStockThreshold(5);
        p.setActive(true);
        return p;
    }

    // ---------- CREATE PRODUCT ----------

    @Test
    void createProduct_byAdmin_succeeds() {
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId("new-id");
            return p;
        });

        Map<String, Object> body = Map.of("name", "Laptop", "category", "electronics", "price", 49999);
        ProductDTO result = controller.createProduct("ADMIN", body);

        assertEquals("Laptop", result.getName());
        assertEquals("electronics", result.getCategory());
        verify(productRepo, times(1)).save(any());
    }

    @Test
    void createProduct_byUser_throws403() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.createProduct("USER", Map.of("name", "Laptop", "category", "electronics", "price", 49999)));

        assertEquals(403, ex.getStatus());
        verify(productRepo, never()).save(any());
    }

    @Test
    void createProduct_withMissingFields_throws400() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.createProduct("ADMIN", Map.of("name", "Laptop")));

        assertEquals(400, ex.getStatus());
    }

    // ---------- UPDATE PRODUCT ----------

    @Test
    void updateProduct_byAdmin_updatesFields() {
        Product existing = sampleProduct();
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(existing));
        when(productRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductDTO result = controller.updateProduct("ADMIN", "prod-1",
                Map.of("name", "Gaming Laptop", "price", 59999));

        assertEquals("Gaming Laptop", result.getName());
        assertEquals(BigDecimal.valueOf(59999), result.getPrice());
    }

    @Test
    void updateProduct_nonExistent_throws404() {
        when(productRepo.findById("ghost")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.updateProduct("ADMIN", "ghost", Map.of("name", "X")));

        assertEquals(404, ex.getStatus());
    }

    // ---------- UPDATE STOCK ----------

    @Test
    void updateStock_byAdmin_setsNewQuantity() {
        Product existing = sampleProduct();
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(existing));
        when(productRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductDTO result = controller.updateStock("ADMIN", "prod-1", Map.of("stockQuantity", 50));

        assertEquals(50, result.getStockQuantity());
    }

    @Test
    void updateStock_negativeValue_throws400() {
        Product existing = sampleProduct();
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(existing));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.updateStock("ADMIN", "prod-1", Map.of("stockQuantity", -5)));

        assertEquals(400, ex.getStatus());
        verify(productRepo, never()).save(any());
    }

    // ---------- TOGGLE STATUS ----------

    @Test
    void toggleStatus_deactivatesProduct() {
        Product existing = sampleProduct();
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(existing));
        when(productRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductDTO result = controller.toggleStatus("ADMIN", "prod-1", Map.of("active", false));

        assertFalse(result.getActive());
    }

    @Test
    void toggleStatus_withoutBooleanField_throws400() {
        Product existing = sampleProduct();
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(existing));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.toggleStatus("ADMIN", "prod-1", Map.of("active", "not-a-boolean")));

        assertEquals(400, ex.getStatus());
    }

    // ---------- GET PRODUCT ----------

    @Test
    void getProduct_existsAndActive_returnsUserDTO() {
        Product p = sampleProduct();
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(p));

        ProductDTO result = controller.getProduct("prod-1");

        assertEquals("Laptop", result.getName());
        assertEquals("IN_STOCK", result.getStockStatus());
        assertNull(result.getStockQuantity(), "Users should NOT see exact stock number");
    }

    @Test
    void getProduct_existsButInactive_throws404() {
        Product p = sampleProduct();
        p.setActive(false);
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(p));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.getProduct("prod-1"));

        assertEquals(404, ex.getStatus());
    }

    @Test
    void getProduct_notFound_throws404() {
        when(productRepo.findById("ghost")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.getProduct("ghost"));

        assertEquals(404, ex.getStatus());
    }
}
