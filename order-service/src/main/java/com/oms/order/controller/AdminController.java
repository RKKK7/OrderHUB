package com.oms.order.controller;

import com.oms.order.dto.*;
import com.oms.order.dto.StockDTOs.StockItem;
import com.oms.order.event.OrderEvent;
import com.oms.order.event.OrderEventPublisher;
import com.oms.order.feign.ProductServiceClient;
import com.oms.order.model.*;
import com.oms.order.repository.OrderRepository;
import com.oms.order.repository.UserRepository;
import com.oms.order.service.OrderMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final ProductServiceClient productClient;   // SYNC — critical path (kept)
    private final OrderEventPublisher eventPublisher;   // ASYNC — notifications via Kafka

    /** Valid state transitions — enforced, not advisory */
    private static final Map<String, List<String>> VALID_TRANSITIONS = Map.of(
            "PENDING",   List.of("CONFIRMED", "CANCELLED"),
            "CONFIRMED", List.of("SHIPPED", "CANCELLED"),
            "SHIPPED",   List.of("DELIVERED"),
            "DELIVERED", List.of(),
            "CANCELLED", List.of()
    );

    public AdminController(OrderRepository orderRepo, UserRepository userRepo,
                           ProductServiceClient productClient,
                           OrderEventPublisher eventPublisher) {
        this.orderRepo = orderRepo;
        this.userRepo = userRepo;
        this.productClient = productClient;
        this.eventPublisher = eventPublisher;
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role))
            throw new ApiException(403, "Admin access required");
    }

    @GetMapping("/orders")
    public List<OrderDTO> allOrders(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String status) {
        requireAdmin(role);
        List<Order> orders = orderRepo.findAllByOrderByCreatedAtDesc();
        if (status != null && !status.isBlank()) {
            orders = orders.stream()
                    .filter(o -> status.equalsIgnoreCase(o.getStatus()))
                    .toList();
        }
        return orders.stream().map(o -> {
            User user = userRepo.findById(o.getUserId()).orElse(null);
            return OrderMapper.toAdminOrderDTO(o, user);
        }).toList();
    }

    /**
     * Update order status — enforces the state machine.
     * CANCELLED triggers stock restoration BEFORE status change (SYNC Feign — kept).
     * Every status change then publishes an order-event to Kafka (ASYNC — new).
     */
    @PutMapping("/orders/{id}/status")
    public OrderDTO updateStatus(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        requireAdmin(role);

        String newStatus = body.get("status") != null ? body.get("status").toString().toUpperCase() : null;
        if (newStatus == null) throw new ApiException(400, "status is required");

        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new ApiException(404, "Order not found"));

        List<String> allowed = VALID_TRANSITIONS.getOrDefault(order.getStatus(), List.of());
        if (!allowed.contains(newStatus))
            throw new ApiException(400, "Cannot change from " + order.getStatus() + " to " + newStatus);

        // If cancelling, restore stock BEFORE changing status
        if ("CANCELLED".equals(newStatus)) {
            restoreOrderStock(order);
            order.setStatusNote("Cancelled by admin");
        }

        order.setStatus(newStatus);
        order = orderRepo.save(order);

        // Notify user via Kafka
        User user = userRepo.findById(order.getUserId()).orElse(null);
        String orderId = order.getId().substring(0, 8).toUpperCase();
        String message = switch (newStatus) {
            case "CONFIRMED" -> "Your order #" + orderId + " has been confirmed.";
            case "SHIPPED"   -> "Your order #" + orderId + " has been shipped!";
            case "DELIVERED"  -> "Your order #" + orderId + " has been delivered.";
            case "CANCELLED"  -> "Your order #" + orderId + " has been cancelled by the seller.";
            default -> "Your order #" + orderId + " status has been updated.";
        };
        publishOrderEvent(user, order, "ORDER_" + newStatus,
                "Order " + newStatus.charAt(0) + newStatus.substring(1).toLowerCase(), message);

        return OrderMapper.toAdminOrderDTO(order, user);
    }

    @PutMapping("/orders/{id}/cancel")
    public OrderDTO cancelOrder(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String id) {
        requireAdmin(role);
        return updateStatus(role, id, Map.of("status", "CANCELLED"));
    }

    @GetMapping("/stats")
    public Map<String, Object> dashboardStats(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);

        List<Order> allOrders = orderRepo.findAllByOrderByCreatedAtDesc();
        long totalOrders = allOrders.size();
        BigDecimal totalRevenue = allOrders.stream()
                .filter(o -> !"CANCELLED".equals(o.getStatus()))
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : List.of("PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED")) {
            byStatus.put(s, allOrders.stream().filter(o -> s.equals(o.getStatus())).count());
        }

        // Top products by quantity sold
        Map<String, int[]> productStats = new LinkedHashMap<>();
        Map<String, String> productNames = new LinkedHashMap<>();
        for (Order o : allOrders) {
            if ("CANCELLED".equals(o.getStatus())) continue;
            for (OrderItem item : o.getItems()) {
                int[] stats = productStats.computeIfAbsent(item.getProductId(), k -> new int[2]);
                stats[0] += item.getQuantity();
                stats[1] += item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())).intValue();
                productNames.putIfAbsent(item.getProductId(), item.getProductName());
            }
        }
        List<Map<String, Object>> topProducts = productStats.entrySet().stream()
                .sorted((a, b) -> b.getValue()[0] - a.getValue()[0])
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("productId", e.getKey());
                    m.put("productName", productNames.get(e.getKey()));
                    m.put("totalSold", e.getValue()[0]);
                    m.put("totalRevenue", e.getValue()[1]);
                    return m;
                }).toList();

        // Low stock from product-service (SYNC read — fine to keep as Feign)
        List<Map<String, Object>> lowStock = List.of();
        try { lowStock = productClient.getLowStockProducts(); } catch (Exception ignored) {}

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("overview", Map.of("totalOrders", totalOrders, "totalRevenue", totalRevenue));
        res.put("ordersByStatus", byStatus);
        res.put("topProducts", topProducts);
        res.put("lowStockProducts", lowStock);
        return res;
    }

    // ---------- Helpers ----------

    private void restoreOrderStock(Order order) {
        List<StockItem> items = order.getItems().stream()
                .map(i -> new StockItem(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());
        try {
            productClient.restoreStock(items);
        } catch (Exception e) {
            throw new ApiException(503, "Cancellation failed — product service unavailable");
        }
    }

    private void publishOrderEvent(User user, Order order, String type, String title, String message) {
        if (user == null) return;
        OrderEvent event = OrderEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(type)
                .timestamp(Instant.now().toString())
                .orderId(order.getId())
                .userId(user.getId())
                .userEmail(user.getEmail())
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .title(title)
                .message(message)
                .link("/orders/" + order.getId())
                .build();
        eventPublisher.publish(event);
    }
}
