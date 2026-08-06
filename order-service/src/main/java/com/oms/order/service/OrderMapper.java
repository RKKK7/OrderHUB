package com.oms.order.service;

import com.oms.order.dto.OrderDTO;
import com.oms.order.dto.UserDTO;
import com.oms.order.model.Order;
import com.oms.order.model.OrderItem;
import com.oms.order.model.User;

import java.util.List;

public final class OrderMapper {

    private OrderMapper() {}

    public static UserDTO toUserDTO(User u) {
        if (u == null) return null;
        return UserDTO.builder()
                .id(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .role(u.getRole())
                .address(u.getAddress())
                .createdAt(u.getCreatedAt() != null ? u.getCreatedAt().toString() : null)
                .build();
    }

    public static OrderDTO.OrderItemDTO toItemDTO(OrderItem item) {
        if (item == null) return null;
        return OrderDTO.OrderItemDTO.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .build();
    }

    public static OrderDTO toOrderDTO(Order o) {
        if (o == null) return null;
        List<OrderDTO.OrderItemDTO> items = o.getItems() != null
                ? o.getItems().stream().map(OrderMapper::toItemDTO).toList()
                : List.of();

        return OrderDTO.builder()
                .id(o.getId())
                .userId(o.getUserId())
                .status(o.getStatus())
                .totalAmount(o.getTotalAmount())
                .shippingAddress(o.getShippingAddress())
                .statusNote(o.getStatusNote())
                .items(items)
                .createdAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : null)
                .updatedAt(o.getUpdatedAt() != null ? o.getUpdatedAt().toString() : null)
                .build();
    }

    /** Admin view — includes user name/email alongside order details */
    public static OrderDTO toAdminOrderDTO(Order o, User user) {
        OrderDTO dto = toOrderDTO(o);
        if (dto != null && user != null) {
            dto.setUserName(user.getName());
            dto.setUserEmail(user.getEmail());
        }
        return dto;
    }
}
