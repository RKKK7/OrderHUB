package com.oms.product.service;

import com.oms.product.dto.StockDTOs.*;
import com.oms.product.event.StockEvent;
import com.oms.product.event.StockEventPublisher;
import com.oms.product.model.Product;
import com.oms.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Atomic stock operations — the heart of inventory consistency.
 *
 * The synchronous contract is UNCHANGED: check-and-reserve is still one @Transactional,
 * all-or-nothing method that returns an immediate result to order-service over Feign.
 * Kafka is layered ON TOP — after the stock decision is made, we ALSO emit stock-events
 * so downstream consumers can react asynchronously. The Feign caller neither knows nor
 * cares that events are being published.
 */
@Service
public class StockService {

    private final ProductRepository productRepo;
    private final ProductCacheService cache;
    private final StockEventPublisher stockEventPublisher;

    public StockService(ProductRepository productRepo, ProductCacheService cache,
                        StockEventPublisher stockEventPublisher) {
        this.productRepo = productRepo;
        this.cache = cache;
        this.stockEventPublisher = stockEventPublisher;
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
        // Events are staged here and only published if the WHOLE batch succeeds.
        List<StockEvent> pendingEvents = new ArrayList<>();

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

            // Stage a STOCK_RESERVED event. stockAfter is the pre-read value minus the qty.
            int stockAfter = product.getStockQuantity() - item.getQuantity();
            pendingEvents.add(new StockEvent(
                    UUID.randomUUID().toString(), "STOCK_RESERVED", Instant.now().toString(),
                    product.getId(), product.getName(),
                    -item.getQuantity(), stockAfter, product.getLowStockThreshold()));
            // If this reservation pushed the product to/below its threshold, stage an alert too.
            if (stockAfter <= product.getLowStockThreshold()) {
                pendingEvents.add(new StockEvent(
                        UUID.randomUUID().toString(), "LOW_STOCK_ALERT", Instant.now().toString(),
                        product.getId(), product.getName(),
                        -item.getQuantity(), stockAfter, product.getLowStockThreshold()));
            }
        }

        // ALL-OR-NOTHING: if ANY item failed, roll back ALL successful decrements
        if (!failed.isEmpty()) {
            for (ReservedItem r : reserved) {
                productRepo.incrementStock(r.getProductId(), r.getQuantity());
            }
            // pendingEvents are discarded — nothing actually changed
            return ReservationResponse.failed(failed);
        }

        // All items reserved — invalidate cache + publish the staged events
        for (ReservedItem r : reserved) {
            cache.invalidateOnWrite(r.getProductId());
        }
        pendingEvents.forEach(stockEventPublisher::publish);

        return ReservationResponse.ok(reserved);
    }

    /**
     * Restore stock — called on order cancellation or compensation after failed order save.
     * Emits STOCK_RESTORED per item.
     */
    @Transactional
    public void restoreStock(List<StockItem> items) {
        for (StockItem item : items) {
            productRepo.incrementStock(item.getProductId(), item.getQuantity());
            cache.invalidateOnWrite(item.getProductId());

            // Re-read to report an accurate stockAfter in the event
            productRepo.findById(item.getProductId()).ifPresent(p ->
                    stockEventPublisher.publish("STOCK_RESTORED", p.getId(), p.getName(),
                            item.getQuantity(), p.getStockQuantity(), p.getLowStockThreshold()));
        }
    }
}
