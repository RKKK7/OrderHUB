package com.oms.product.controller;

import com.oms.product.dto.ProductDTO;
import com.oms.product.dto.StockDTOs.*;
import com.oms.product.model.Product;
import com.oms.product.repository.ProductRepository;
import com.oms.product.service.StockService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalProductControllerTest {

    @Mock private StockService stockService;
    @Mock private ProductRepository productRepo;

    @InjectMocks private InternalProductController controller;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product();
        sampleProduct.setId("prod-1");
        sampleProduct.setName("Widget");
        sampleProduct.setCategory("Electronics");
        sampleProduct.setPrice(new BigDecimal("29.99"));
        sampleProduct.setStockQuantity(50);
        sampleProduct.setActive(true);
    }

    @Test
    void checkAndReserve_delegatesToStockService() {
        List<StockItem> items = List.of(new StockItem("prod-1", 2));
        ReservationResponse expected = ReservationResponse.ok(
                List.of(new ReservedItem("prod-1", "Widget", new BigDecimal("29.99"), 2)));
        when(stockService.checkAndReserve(items)).thenReturn(expected);

        ReservationResponse result = controller.checkAndReserve(items);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getItems().size());
    }

    @Test
    void restoreStock_delegatesToStockService() {
        List<StockItem> items = List.of(new StockItem("prod-1", 3));

        controller.restoreStock(items);

        verify(stockService).restoreStock(items);
    }

    @Test
    void getById_found_returnsAdminDTO() {
        when(productRepo.findById("prod-1")).thenReturn(Optional.of(sampleProduct));

        ProductDTO result = controller.getById("prod-1");

        assertNotNull(result);
        assertEquals("Widget", result.getName());
        assertNotNull(result.getStockQuantity()); // admin DTO includes stock
    }

    @Test
    void getById_notFound_returnsNull() {
        when(productRepo.findById("missing")).thenReturn(Optional.empty());

        ProductDTO result = controller.getById("missing");

        assertNull(result);
    }

    @Test
    void getByIds_returnsBatchResults() {
        when(productRepo.findByIdIn(List.of("prod-1"))).thenReturn(List.of(sampleProduct));

        List<ProductDTO> result = controller.getByIds(List.of("prod-1"));

        assertEquals(1, result.size());
        assertEquals("prod-1", result.get(0).getId());
    }

    @Test
    void lowStock_returnsLowStockProducts() {
        sampleProduct.setStockQuantity(3);
        sampleProduct.setLowStockThreshold(10);
        when(productRepo.findLowStockProducts()).thenReturn(List.of(sampleProduct));

        List<ProductDTO> result = controller.lowStock();

        assertEquals(1, result.size());
    }
}
