package com.oms.product.event;

/**
 * StockEvent — the message published to the "stock-events" Kafka topic.
 *
 * product-service emits one of these whenever inventory changes:
 *   - STOCK_RESERVED  : stock decremented by a successful order reservation
 *   - STOCK_RESTORED  : stock added back on cancellation / compensation
 *   - STOCK_UPDATED   : an admin manually set the stock quantity
 *   - LOW_STOCK_ALERT : stockAfter fell to or below the product's threshold
 *
 * This is a plain POJO (no Lombok) — product-service does not use Lombok, matching
 * the existing StockDTOs / ProductDTO style in this service.
 *
 * Field names must stay identical to the consumer's mirror class
 * (com.oms.notification.event.StockEvent) because they agree on the JSON shape,
 * not on a shared Java type.
 */
public class StockEvent {

    private String eventId;
    private String eventType;
    private String timestamp;   // ISO-8601 String, never Instant

    private String productId;
    private String productName;
    private int quantityChanged;
    private int stockAfter;
    private int threshold;

    public StockEvent() {}

    public StockEvent(String eventId, String eventType, String timestamp,
                      String productId, String productName,
                      int quantityChanged, int stockAfter, int threshold) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.productId = productId;
        this.productName = productName;
        this.quantityChanged = quantityChanged;
        this.stockAfter = stockAfter;
        this.threshold = threshold;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public int getQuantityChanged() { return quantityChanged; }
    public void setQuantityChanged(int quantityChanged) { this.quantityChanged = quantityChanged; }
    public int getStockAfter() { return stockAfter; }
    public void setStockAfter(int stockAfter) { this.stockAfter = stockAfter; }
    public int getThreshold() { return threshold; }
    public void setThreshold(int threshold) { this.threshold = threshold; }
}
