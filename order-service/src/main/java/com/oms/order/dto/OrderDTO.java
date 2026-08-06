package com.oms.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDTO {
    private String id;
    private String userId;
    private String status;
    private BigDecimal totalAmount;
    private String shippingAddress;
    private String statusNote;
    private List<OrderItemDTO> items;
    private String createdAt;  // String, never Instant
    private String updatedAt;

    // Joined for admin view
    private String userName;
    private String userEmail;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OrderItemDTO {
        private String id;
        private String productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
    }
}
