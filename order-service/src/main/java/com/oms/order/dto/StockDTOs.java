package com.oms.order.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mirrors product-service's StockDTOs — used by Feign client.
 * Defined separately here (no shared library) to keep services fully independent.
 */
public final class StockDTOs {

    private StockDTOs() {}

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StockItem {
        private String productId;
        private int quantity;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ReservedItem {
        private String productId;
        private String productName;
        private BigDecimal unitPrice;
        private int quantity;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class FailedItem {
        private String productId;
        private int requested;
        private int available;
        private String reason;
    }

    @Data @NoArgsConstructor
    public static class ReservationResponse {
        private boolean success;
        private List<ReservedItem> items;
        private List<FailedItem> failedItems;
    }
}
