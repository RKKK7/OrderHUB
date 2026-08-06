package com.oms.order.dto;

import lombok.*;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class PlaceOrderRequest {
    private String shippingAddress;
    private List<Item> items;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Item {
        private String productId;
        private int quantity;
    }
}
