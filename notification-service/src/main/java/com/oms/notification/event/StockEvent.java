package com.oms.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * Consumer-side mirror of product-service's StockEvent.
 * Same JSON shape, separate Java class — see OrderEvent for the rationale.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockEvent {

    private String eventId;
    private String eventType;
    private String timestamp;

    private String productId;
    private String productName;
    private int quantityChanged;
    private int stockAfter;
    private int threshold;
}
