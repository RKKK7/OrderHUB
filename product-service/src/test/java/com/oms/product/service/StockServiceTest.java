package com.oms.product.service;

import com.oms.product.dto.StockDTOs.*;
import com.oms.product.event.StockEvent;
import com.oms.product.event.StockEventPublisher;
import com.oms.product.model.Product;
import com.oms.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock private ProductRepository productRepo;
    @Mock private ProductCacheService cache;
    @Mock private StockEventPublisher stockEventPublisher;

    @InjectMocks private StockService stockService;

    private Product productA;
    private Product productB;

    @BeforeEach
    void setUp() {
        productA = new Product();
        productA.setId("A");
        productA.setName("Product A");
        productA.setPrice(new BigDecimal("10.00"));
        productA.setStockQuantity(20);
        productA.setLowStockThreshold(5);
        productA.setActive(true);

        productB = new Product();
        productB.setId("B");
        productB.setName("Product B");
        productB.setPrice(new BigDecimal("25.00"));
        productB.setStockQuantity(8);
        productB.setLowStockThreshold(5);
        productB.setActive(true);
    }

    // ---- checkAndReserve: happy paths ----

    @Test
    void checkAndReserve_allSucceed_returnsOkWithReservedItems() {
        List<StockItem> items = List.of(new StockItem("A", 3), new StockItem("B", 2));
        when(productRepo.findById("A")).thenReturn(Optional.of(productA));
        when(productRepo.findById("B")).thenReturn(Optional.of(productB));
        when(productRepo.decrementStock("A", 3)).thenReturn(1);
        when(productRepo.decrementStock("B", 2)).thenReturn(1);

        ReservationResponse resp = stockService.checkAndReserve(items);

        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getItems().size());
        assertEquals("A", resp.getItems().get(0).getProductId());
        assertEquals(new BigDecimal("10.00"), resp.getItems().get(0).getUnitPrice());
        assertTrue(resp.getFailedItems().isEmpty());
        verify(cache).invalidateOnWrite("A");
        verify(cache).invalidateOnWrite("B");
        // STOCK_RESERVED events published
        verify(stockEventPublisher, times(2)).publish(any(StockEvent.class));
    }

    @Test
    void checkAndReserve_lowStockAfterReserve_publishesLowStockAlert() {
        // productB has 8 stock, threshold 5. Reserve 4 -> stockAfter=4, which is <= 5
        List<StockItem> items = List.of(new StockItem("B", 4));
        when(productRepo.findById("B")).thenReturn(Optional.of(productB));
        when(productRepo.decrementStock("B", 4)).thenReturn(1);

        ReservationResponse resp = stockService.checkAndReserve(items);

        assertTrue(resp.isSuccess());
        // Expect 2 events: STOCK_RESERVED + LOW_STOCK_ALERT
        verify(stockEventPublisher, times(2)).publish(any(StockEvent.class));
    }

    // ---- checkAndReserve: failure + compensation ----

    @Test
    void checkAndReserve_productNotFound_returnsFailed() {
        List<StockItem> items = List.of(new StockItem("missing", 1));
        when(productRepo.findById("missing")).thenReturn(Optional.empty());

        ReservationResponse resp = stockService.checkAndReserve(items);

        assertFalse(resp.isSuccess());
        assertEquals("PRODUCT_NOT_FOUND", resp.getFailedItems().get(0).getReason());
    }

    @Test
    void checkAndReserve_productInactive_returnsFailed() {
        productA.setActive(false);
        List<StockItem> items = List.of(new StockItem("A", 1));
        when(productRepo.findById("A")).thenReturn(Optional.of(productA));

        ReservationResponse resp = stockService.checkAndReserve(items);

        assertFalse(resp.isSuccess());
        assertEquals("PRODUCT_INACTIVE", resp.getFailedItems().get(0).getReason());
    }

    @Test
    void checkAndReserve_insufficientStock_returnsFailed() {
        List<StockItem> items = List.of(new StockItem("A", 5));
        when(productRepo.findById("A")).thenReturn(Optional.of(productA));
        when(productRepo.decrementStock("A", 5)).thenReturn(0); // atomic UPDATE matched 0 rows

        ReservationResponse resp = stockService.checkAndReserve(items);

        assertFalse(resp.isSuccess());
        assertEquals("INSUFFICIENT_STOCK", resp.getFailedItems().get(0).getReason());
    }

    @Test
    void checkAndReserve_secondItemFails_compensatesFirstDecrement() {
        // A succeeds, B fails -> A must be rolled back
        List<StockItem> items = List.of(new StockItem("A", 3), new StockItem("B", 100));
        when(productRepo.findById("A")).thenReturn(Optional.of(productA));
        when(productRepo.findById("B")).thenReturn(Optional.of(productB));
        when(productRepo.decrementStock("A", 3)).thenReturn(1);
        when(productRepo.decrementStock("B", 100)).thenReturn(0); // insufficient

        ReservationResponse resp = stockService.checkAndReserve(items);

        assertFalse(resp.isSuccess());
        // Compensation: A's stock restored
        verify(productRepo).incrementStock("A", 3);
        // No events published (all discarded on failure)
        verify(stockEventPublisher, never()).publish(any(StockEvent.class));
        // No cache invalidation on failure
        verify(cache, never()).invalidateOnWrite(anyString());
    }

    @Test
    void checkAndReserve_middleItemNotFound_compensatesEarlierDecrements() {
        Product productC = new Product();
        productC.setId("C");
        productC.setName("Product C");
        productC.setPrice(new BigDecimal("5.00"));
        productC.setStockQuantity(10);
        productC.setLowStockThreshold(2);
        productC.setActive(true);

        // A succeeds, missing fails, C never reached
        List<StockItem> items = List.of(
                new StockItem("A", 1),
                new StockItem("missing", 1),
                new StockItem("C", 1));
        when(productRepo.findById("A")).thenReturn(Optional.of(productA));
        when(productRepo.decrementStock("A", 1)).thenReturn(1);
        when(productRepo.findById("missing")).thenReturn(Optional.empty());
        when(productRepo.findById("C")).thenReturn(Optional.of(productC));
        when(productRepo.decrementStock("C", 1)).thenReturn(1);

        ReservationResponse resp = stockService.checkAndReserve(items);

        assertFalse(resp.isSuccess());
        // Both A and C were decremented before the failure list was checked at the end
        // Actually, the loop continues - "missing" adds to failed, then C succeeds.
        // At the end, failed is non-empty, so both A and C get compensated.
        verify(productRepo).incrementStock("A", 1);
        verify(productRepo).incrementStock("C", 1);
    }

    // ---- restoreStock ----

    @Test
    void restoreStock_incrementsAndPublishesRestoredEvent() {
        List<StockItem> items = List.of(new StockItem("A", 5));
        when(productRepo.incrementStock("A", 5)).thenReturn(1);
        productA.setStockQuantity(25); // after restore
        when(productRepo.findById("A")).thenReturn(Optional.of(productA));

        stockService.restoreStock(items);

        verify(productRepo).incrementStock("A", 5);
        verify(cache).invalidateOnWrite("A");
        verify(stockEventPublisher).publish(eq("STOCK_RESTORED"), eq("A"), eq("Product A"),
                eq(5), eq(25), eq(5));
    }

    @Test
    void restoreStock_multipleItems_restoresAll() {
        List<StockItem> items = List.of(new StockItem("A", 2), new StockItem("B", 3));
        when(productRepo.incrementStock("A", 2)).thenReturn(1);
        when(productRepo.incrementStock("B", 3)).thenReturn(1);
        when(productRepo.findById("A")).thenReturn(Optional.of(productA));
        when(productRepo.findById("B")).thenReturn(Optional.of(productB));

        stockService.restoreStock(items);

        verify(productRepo).incrementStock("A", 2);
        verify(productRepo).incrementStock("B", 3);
        verify(cache).invalidateOnWrite("A");
        verify(cache).invalidateOnWrite("B");
    }
}
