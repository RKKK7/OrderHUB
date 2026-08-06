package com.oms.product.service;

import com.oms.product.dto.StockDTOs.*;
import com.oms.product.model.Product;
import com.oms.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the atomic stock reservation logic — the core of inventory consistency.
 * These are arguably the most important tests in the entire system.
 */
@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock private ProductRepository productRepo;
    @Mock private ProductCacheService cache;

    private StockService stockService;

    @BeforeEach
    void setUp() {
        stockService = new StockService(productRepo, cache);
    }

    private Product product(String id, String name, BigDecimal price, int stock, boolean active) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(price);
        p.setStockQuantity(stock);
        p.setActive(active);
        return p;
    }

    // ---------- CHECK AND RESERVE ----------

    @Test
    void checkAndReserve_allItemsAvailable_reservesSuccessfully() {
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(
                product("prod-1", "Laptop", BigDecimal.valueOf(49999), 10, true)));
        when(productRepo.findById("prod-2")).thenReturn(Optional.of(
                product("prod-2", "Mouse", BigDecimal.valueOf(499), 50, true)));
        when(productRepo.decrementStock("prod-1", 2)).thenReturn(1);
        when(productRepo.decrementStock("prod-2", 1)).thenReturn(1);

        List<StockItem> items = List.of(new StockItem("prod-1", 2), new StockItem("prod-2", 1));

        ReservationResponse res = stockService.checkAndReserve(items);

        assertTrue(res.isSuccess());
        assertEquals(2, res.getItems().size());
        assertEquals("Laptop", res.getItems().get(0).getProductName());
        assertEquals(BigDecimal.valueOf(49999), res.getItems().get(0).getUnitPrice());
        assertEquals(2, res.getItems().get(0).getQuantity());
        assertTrue(res.getFailedItems().isEmpty());
        verify(cache, times(2)).invalidateOnWrite(any());
    }

    @Test
    void checkAndReserve_oneItemInsufficientStock_failsEntireReservation() {
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(
                product("prod-1", "Laptop", BigDecimal.valueOf(49999), 10, true)));
        when(productRepo.findById("prod-2")).thenReturn(Optional.of(
                product("prod-2", "Keyboard", BigDecimal.valueOf(999), 0, true)));  // out of stock
        when(productRepo.decrementStock("prod-1", 2)).thenReturn(1);  // first item succeeds
        when(productRepo.decrementStock("prod-2", 1)).thenReturn(0);  // second item fails

        List<StockItem> items = List.of(new StockItem("prod-1", 2), new StockItem("prod-2", 1));

        ReservationResponse res = stockService.checkAndReserve(items);

        assertFalse(res.isSuccess());
        assertFalse(res.getFailedItems().isEmpty());
        assertEquals("INSUFFICIENT_STOCK", res.getFailedItems().get(0).getReason());
        // CRITICAL: the successful first item must be ROLLED BACK
        verify(productRepo, times(1)).incrementStock("prod-1", 2);
        // Cache should NOT be invalidated since the whole thing rolled back
        verify(cache, never()).invalidateOnWrite(any());
    }

    @Test
    void checkAndReserve_productNotFound_failsWithClearReason() {
        when(productRepo.findById("ghost")).thenReturn(Optional.empty());

        List<StockItem> items = List.of(new StockItem("ghost", 1));

        ReservationResponse res = stockService.checkAndReserve(items);

        assertFalse(res.isSuccess());
        assertEquals("PRODUCT_NOT_FOUND", res.getFailedItems().get(0).getReason());
    }

    @Test
    void checkAndReserve_inactiveProduct_failsWithClearReason() {
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(
                product("prod-1", "Laptop", BigDecimal.valueOf(49999), 10, false)));  // INACTIVE

        List<StockItem> items = List.of(new StockItem("prod-1", 1));

        ReservationResponse res = stockService.checkAndReserve(items);

        assertFalse(res.isSuccess());
        assertEquals("PRODUCT_INACTIVE", res.getFailedItems().get(0).getReason());
        verify(productRepo, never()).decrementStock(any(), anyInt());
    }

    @Test
    void checkAndReserve_singleItemSuccess_cachesInvalidated() {
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(
                product("prod-1", "Laptop", BigDecimal.valueOf(49999), 10, true)));
        when(productRepo.decrementStock("prod-1", 1)).thenReturn(1);

        ReservationResponse res = stockService.checkAndReserve(List.of(new StockItem("prod-1", 1)));

        assertTrue(res.isSuccess());
        verify(cache, times(1)).invalidateOnWrite("prod-1");
    }

    // ---------- RESTORE STOCK ----------

    @Test
    void restoreStock_addsBackCorrectQuantities() {
        List<StockItem> items = List.of(
                new StockItem("prod-1", 2),
                new StockItem("prod-2", 3)
        );

        stockService.restoreStock(items);

        verify(productRepo, times(1)).incrementStock("prod-1", 2);
        verify(productRepo, times(1)).incrementStock("prod-2", 3);
        verify(cache, times(1)).invalidateOnWrite("prod-1");
        verify(cache, times(1)).invalidateOnWrite("prod-2");
    }
}
