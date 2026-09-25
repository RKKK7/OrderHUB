package com.oms.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;

/**
 * Consumer-side mirror of order-service's OrderEvent.
 *
 * This is a SEPARATE class from the producer's — the two services agree on the JSON
 * shape, not on a shared Java type (the project's "no shared library" rule). Field names
 * must match the producer exactly for Jackson to map them.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) makes the consumer tolerant: if the
 * producer adds a new field later, older consumers won't break.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent {

    private String eventId;
    private String eventType;
    private String timestamp;

    private String orderId;
    private String userId;
    private String userEmail;

    private BigDecimal totalAmount;
    private String shippingAddress;

    private String title;
    private String message;
    private String link;
}
