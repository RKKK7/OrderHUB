package com.oms.product.service;

import com.oms.product.dto.StockDTOs.*;
import com.oms.product.model.Product;
import com.oms.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Atomic stock operations — the heart of inventory consistency.
 *
 * check-and-reserve is a single @Transactional method:
 *   - For each item, atomically decrements stock via a conditional UPDATE
 *   - If ANY item fails → rolls back the ENTIRE transaction (no partial reservations)
 *   - On success, returns product names + prices for order snapshots
 *
 * restore-stock is used on cancellation or compensation (failed order save):
 *   - Adds quantities back to stock
 */
@Service
public class StockService {

    private final ProductRepository productRepo;
    private final ProductCacheService cache;

    public StockService(ProductRepository productRepo, ProductCacheService cache) {
        this.productRepo = productRepo;
        this.cache = cache;
    }

    /**
     * Atomic check + reserve — ALL items succeed or ALL fail.
     * Uses conditional UPDATE (WHERE stock >= requested AND active = true)
     * to prevent overselling at the database level.
     */
    @Transactional
    public ReservationResponse checkAndReserve(List<StockItem> items) {
        List<ReservedItem> reserved = new ArrayList<>();
        List<FailedItem> failed = new ArrayList<>();

        for (StockItem item : items) {
            Optional<Product> productOpt = productRepo.findById(item.getProductId());

            if (productOpt.isEmpty()) {
                failed.add(new FailedItem(item.getProductId(), item.getQuantity(), 0, "PRODUCT_NOT_FOUND"));
                continue;
            }

            Product product = productOpt.get();

            if (!product.isActive()) {
                failed.add(new FailedItem(item.getProductId(), item.getQuantity(), product.getStockQuantity(), "PRODUCT_INACTIVE"));
                continue;
            }

            // Atomic conditional decrement — the key oversell-prevention mechanism
            int rowsAffected = productRepo.decrementStock(item.getProductId(), item.getQuantity());

            if (rowsAffected == 0) {
                // Stock insufficient — the UPDATE's WHERE clause didn't match
                failed.add(new FailedItem(item.getProductId(), item.getQuantity(), product.getStockQuantity(), "INSUFFICIENT_STOCK"));
                continue;
            }

            // Stock reserved successfully — capture snapshot for order creation
            reserved.add(new ReservedItem(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    item.getQuantity()
            ));
        }

        // ALL-OR-NOTHING: if ANY item failed, roll back ALL successful decrements
        if (!failed.isEmpty()) {
            // Undo all successful reservations (this is within the same @Transactional)
            for (ReservedItem r : reserved) {
                productRepo.incrementStock(r.getProductId(), r.getQuantity());
            }
            return ReservationResponse.failed(failed);
        }

        // All items reserved — invalidate cache for all affected products
        for (ReservedItem r : reserved) {
            cache.invalidateOnWrite(r.getProductId());
        }

        return ReservationResponse.ok(reserved);
    }

    /**
     * Restore stock — called on order cancellation or compensation after failed order save.
     */
    @Transactional
    public void restoreStock(List<StockItem> items) {
        for (StockItem item : items) {
            productRepo.incrementStock(item.getProductId(), item.getQuantity());
            cache.invalidateOnWrite(item.getProductId());
        }
    }
}
