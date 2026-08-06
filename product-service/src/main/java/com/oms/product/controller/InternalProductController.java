package com.oms.product.controller;

import com.oms.product.dto.ProductDTO;
import com.oms.product.dto.StockDTOs.*;
import com.oms.product.model.Product;
import com.oms.product.repository.ProductRepository;
import com.oms.product.service.ProductMapper;
import com.oms.product.service.StockService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal API — called by order-service via Feign.
 * NOT exposed through the API Gateway (no /internal/** route in gateway).
 */
@RestController
@RequestMapping("/internal/products")
public class InternalProductController {

    private final StockService stockService;
    private final ProductRepository productRepo;

    public InternalProductController(StockService stockService, ProductRepository productRepo) {
        this.stockService = stockService;
        this.productRepo = productRepo;
    }

    /**
     * Atomic check + reserve — the core order-placement operation.
     * Either ALL items are reserved or NONE are (all-or-nothing transaction).
     */
    @PostMapping("/check-and-reserve")
    public ReservationResponse checkAndReserve(@RequestBody List<StockItem> items) {
        return stockService.checkAndReserve(items);
    }

    /**
     * Restore stock — called on order cancellation or compensation.
     */
    @PostMapping("/restore-stock")
    public void restoreStock(@RequestBody List<StockItem> items) {
        stockService.restoreStock(items);
    }

    /** Get single product by ID */
    @GetMapping("/{id}")
    public ProductDTO getById(@PathVariable String id) {
        return productRepo.findById(id)
                .map(ProductMapper::toAdminDTO)
                .orElse(null);
    }

    /** Get multiple products by IDs (batch) */
    @GetMapping("/batch")
    public List<ProductDTO> getByIds(@RequestParam List<String> ids) {
        return productRepo.findByIdIn(ids).stream()
                .map(ProductMapper::toAdminDTO)
                .toList();
    }

    /** Low-stock products (used by order-service admin dashboard) */
    @GetMapping("/low-stock")
    public List<ProductDTO> lowStock() {
        return productRepo.findLowStockProducts().stream()
                .map(ProductMapper::toAdminDTO)
                .toList();
    }
}
