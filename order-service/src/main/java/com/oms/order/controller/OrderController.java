package com.oms.order.controller;

import com.oms.order.dto.*;
import com.oms.order.dto.StockDTOs.*;
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
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final ProductServiceClient productClient;   // SYNC — critical path (kept)
    private final OrderEventPublisher eventPublisher;   // ASYNC — notifications via Kafka

    public OrderController(OrderRepository orderRepo, UserRepository userRepo,
                           ProductServiceClient productClient,
                           OrderEventPublisher eventPublisher) {
        this.orderRepo = orderRepo;
        this.userRepo = userRepo;
        this.productClient = productClient;
        this.eventPublisher = eventPublisher;
    }

    private void requireUser(String role) {
        if ("ADMIN".equals(role))
            throw new ApiException(403, "Admins cannot place orders");
    }

    /**
     * PLACE ORDER — the critical flow.
     * 1. Validate user + request
     * 2. Call product-service: check-and-reserve (atomic, all-or-nothing)  [SYNC Feign — kept]
     * 3. Create Order + OrderItems (with price/name snapshots)
     * 4. If order save fails -> COMPENSATE: restore stock                  [SYNC Feign — kept]
     * 5. Publish ORDER_PLACED event to Kafka                               [ASYNC — new]
     *
     * Steps 2 and 4 stay synchronous ON PURPOSE: placing an order needs an immediate
     * yes/no on stock, and the compensation must be reliable. Only the notification
     * (step 5) — which is genuinely fire-and-forget — moved to Kafka.
     */
    @PostMapping
    public Map<String, Object> placeOrder(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody PlaceOrderRequest request) {

        if (userId == null) throw new ApiException(401, "Not authenticated");
        requireUser(role);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ApiException(404, "User not found"));

        if (request.getItems() == null || request.getItems().isEmpty())
            throw new ApiException(400, "Order must contain at least one item");
        if (request.getShippingAddress() == null || request.getShippingAddress().isBlank())
            throw new ApiException(400, "Shipping address is required");

        // Step 1: Convert to stock items for product-service
        List<StockItem> stockItems = request.getItems().stream()
                .map(i -> new StockItem(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());

        // Step 2: Atomic check-and-reserve (one Feign call, one DB transaction in product-service)
        ReservationResponse reservation;
        try {
            reservation = productClient.checkAndReserve(stockItems);
        } catch (Exception e) {
            throw new ApiException(503, "Product service unavailable, please retry");
        }

        if (!reservation.isSuccess()) {
            String errors = reservation.getFailedItems().stream()
                    .map(f -> f.getProductId() + ": " + f.getReason() +
                              (f.getAvailable() > 0 ? " (available: " + f.getAvailable() + ")" : ""))
                    .collect(Collectors.joining(", "));
            throw new ApiException(400, "Insufficient stock: " + errors);
        }

        // Step 3: Create order with snapshots
        Order order = new Order();
        order.setUserId(userId);
        order.setShippingAddress(request.getShippingAddress());
        order.setStatus("CONFIRMED");

        BigDecimal total = BigDecimal.ZERO;
        for (ReservedItem ri : reservation.getItems()) {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(ri.getProductId());
            item.setProductName(ri.getProductName());
            item.setQuantity(ri.getQuantity());
            item.setUnitPrice(ri.getUnitPrice());
            order.getItems().add(item);
            total = total.add(ri.getUnitPrice().multiply(BigDecimal.valueOf(ri.getQuantity())));
        }
        order.setTotalAmount(total);

        // Step 3b: Save — with compensation on failure
        try {
            order = orderRepo.save(order);
        } catch (Exception e) {
            try { productClient.restoreStock(stockItems); } catch (Exception ignored) {}
            throw new ApiException(500, "Order creation failed, please retry");
        }

        // Step 4: Publish event (non-critical — order already committed)
        publishOrderEvent(user, order, "ORDER_PLACED",
                "Order Confirmed",
                "Your order #" + shortId(order) +
                " has been confirmed. Total: \u20B9" + total);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("order", OrderMapper.toOrderDTO(order));
        return res;
    }

    @GetMapping("/my")
    public List<OrderDTO> myOrders(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId == null) throw new ApiException(401, "Not authenticated");
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(OrderMapper::toOrderDTO).toList();
    }

    @GetMapping("/{id}")
    public OrderDTO getOrder(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String id) {
        if (userId == null) throw new ApiException(401, "Not authenticated");
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new ApiException(404, "Order not found"));
        if (!order.getUserId().equals(userId))
            throw new ApiException(403, "Not authorized to view this order");
        return OrderMapper.toOrderDTO(order);
    }

    /**
     * CANCEL ORDER — stock restored BEFORE status change.
     * If stock restore fails -> cancellation is rejected, order stays unchanged.
     * The restore stays SYNCHRONOUS (Feign) so the consistency guarantee is preserved.
     */
    @PutMapping("/{id}/cancel")
    public OrderDTO cancelOrder(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String id) {
        if (userId == null) throw new ApiException(401, "Not authenticated");
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new ApiException(404, "Order not found"));
        if (!order.getUserId().equals(userId))
            throw new ApiException(403, "Not authorized to cancel this order");
        if (!List.of("PENDING", "CONFIRMED").contains(order.getStatus()))
            throw new ApiException(400, "Cannot cancel a " + order.getStatus().toLowerCase() + " order");

        // Restore stock FIRST — if this fails, order stays unchanged
        List<StockItem> items = order.getItems().stream()
                .map(i -> new StockItem(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());
        try {
            productClient.restoreStock(items);
        } catch (Exception e) {
            throw new ApiException(503, "Cancellation failed — product service unavailable, please retry");
        }

        // Stock restored successfully — NOW update status
        order.setStatus("CANCELLED");
        order.setStatusNote("Cancelled by user");
        order = orderRepo.save(order);

        User user = userRepo.findById(userId).orElse(null);
        publishOrderEvent(user, order, "ORDER_CANCELLED",
                "Order Cancelled",
                "Your order #" + shortId(order) + " has been cancelled.");

        return OrderMapper.toOrderDTO(order);
    }

    // ---------- Helpers ----------

    private static String shortId(Order order) {
        return order.getId().substring(0, 8).toUpperCase();
    }

    /**
     * Build an OrderEvent and hand it to the publisher.
     * userEmail is denormalized into the event so notification-service never calls back.
     */
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
