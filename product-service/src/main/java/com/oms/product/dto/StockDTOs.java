package com.oms.product.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTOs for internal stock operations (order-service ↔ product-service).
 * Nested as static inner classes to keep them together — they're always used as a pair.
 */
public final class StockDTOs {

    private StockDTOs() {}

    /** Single item in a stock request (used by both check-and-reserve and restore-stock) */
    public static class StockItem {
        private String productId;
        private int quantity;

        public StockItem() {}
        public StockItem(String productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }

    /** Response for a successful reservation — includes price + name snapshots for order creation */
    public static class ReservedItem {
        private String productId;
        private String productName;
        private BigDecimal unitPrice;
        private int quantity;

        public ReservedItem() {}
        public ReservedItem(String productId, String productName, BigDecimal unitPrice, int quantity) {
            this.productId = productId;
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
        }

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }

    /** Item that failed reservation — tells the caller exactly what went wrong */
    public static class FailedItem {
        private String productId;
        private int requested;
        private int available;
        private String reason;  // "INSUFFICIENT_STOCK" | "PRODUCT_INACTIVE" | "PRODUCT_NOT_FOUND"

        public FailedItem() {}
        public FailedItem(String productId, int requested, int available, String reason) {
            this.productId = productId;
            this.requested = requested;
            this.available = available;
            this.reason = reason;
        }

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public int getRequested() { return requested; }
        public void setRequested(int requested) { this.requested = requested; }
        public int getAvailable() { return available; }
        public void setAvailable(int available) { this.available = available; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /** Full response from check-and-reserve */
    public static class ReservationResponse {
        private boolean success;
        private List<ReservedItem> items;
        private List<FailedItem> failedItems;

        public ReservationResponse() {}

        public static ReservationResponse ok(List<ReservedItem> items) {
            ReservationResponse r = new ReservationResponse();
            r.success = true;
            r.items = items;
            r.failedItems = List.of();
            return r;
        }

        public static ReservationResponse failed(List<FailedItem> failedItems) {
            ReservationResponse r = new ReservationResponse();
            r.success = false;
            r.items = List.of();
            r.failedItems = failedItems;
            return r;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public List<ReservedItem> getItems() { return items; }
        public void setItems(List<ReservedItem> items) { this.items = items; }
        public List<FailedItem> getFailedItems() { return failedItems; }
        public void setFailedItems(List<FailedItem> failedItems) { this.failedItems = failedItems; }
    }
}
