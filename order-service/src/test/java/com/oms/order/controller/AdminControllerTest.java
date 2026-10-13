package com.oms.order.controller;

import com.oms.order.dto.ApiException;
import com.oms.order.dto.OrderDTO;
import com.oms.order.event.OrderEventPublisher;
import com.oms.order.feign.ProductServiceClient;
import com.oms.order.model.Order;
import com.oms.order.model.OrderItem;
import com.oms.order.model.User;
import com.oms.order.repository.OrderRepository;
import com.oms.order.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private OrderRepository orderRepo;
    @Mock private UserRepository userRepo;
    @Mock private ProductServiceClient productClient;
    @Mock private OrderEventPublisher eventPublisher;

    private AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController(orderRepo, userRepo, productClient, eventPublisher);
    }

    private Order testOrder(String status) {
        Order o = new Order();
        o.setId("order-12345678-abcd");
        o.setUserId("user-1");
        o.setStatus(status);
        o.setTotalAmount(new BigDecimal("200.00"));
        o.setShippingAddress("123 Main St");
        o.setCreatedAt(Instant.now());
        o.setUpdatedAt(Instant.now());

        OrderItem item = new OrderItem();
        item.setId("item-1");
        item.setProductId("prod-1");
        item.setProductName("Widget");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setOrder(o);
        o.getItems().add(item);
        return o;
    }

    private User testUser() {
        User u = new User();
        u.setId("user-1");
        u.setName("John");
        u.setEmail("john@example.com");
        u.setRole("USER");
        u.setCreatedAt(Instant.now());
        return u;
    }

    // ---- requireAdmin ----

    @Test
    void allOrders_nonAdmin_throws403() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.allOrders("USER", null));
        assertEquals(403, ex.getStatus());
    }

    // ---- getAllOrders ----

    @Test
    void allOrders_admin_returnsAllOrders() {
        Order o = testOrder("CONFIRMED");
        when(orderRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(o));
        when(userRepo.findById("user-1")).thenReturn(Optional.of(testUser()));

        List<OrderDTO> result = controller.allOrders("ADMIN", null);

        assertEquals(1, result.size());
        assertEquals("John", result.get(0).getUserName());
    }

    @Test
    void allOrders_withStatusFilter_filtersOrders() {
        Order confirmed = testOrder("CONFIRMED");
        Order shipped = testOrder("SHIPPED");
        shipped.setId("order-shipped-1234");
        when(orderRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(confirmed, shipped));
        when(userRepo.findById("user-1")).thenReturn(Optional.of(testUser()));

        List<OrderDTO> result = controller.allOrders("ADMIN", "SHIPPED");

        assertEquals(1, result.size());
        assertEquals("SHIPPED", result.get(0).getStatus());
    }

    // ---- updateStatus: state machine ----

    @Test
    void updateStatus_confirmedToShipped_succeeds() {
        Order o = testOrder("CONFIRMED");
        when(orderRepo.findById("order-12345678-abcd")).thenReturn(Optional.of(o));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findById("user-1")).thenReturn(Optional.of(testUser()));

        OrderDTO result = controller.updateStatus("ADMIN", "order-12345678-abcd",
                Map.of("status", "SHIPPED"));

        assertEquals("SHIPPED", result.getStatus());
        verify(eventPublisher).publish(any());
    }

    @Test
    void updateStatus_shippedToDelivered_succeeds() {
        Order o = testOrder("SHIPPED");
        when(orderRepo.findById("order-12345678-abcd")).thenReturn(Optional.of(o));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findById("user-1")).thenReturn(Optional.of(testUser()));

        OrderDTO result = controller.updateStatus("ADMIN", "order-12345678-abcd",
                Map.of("status", "DELIVERED"));

        assertEquals("DELIVERED", result.getStatus());
    }

    @Test
    void updateStatus_confirmedToCancelled_restoresStockFirst() {
        Order o = testOrder("CONFIRMED");
        when(orderRepo.findById("order-12345678-abcd")).thenReturn(Optional.of(o));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findById("user-1")).thenReturn(Optional.of(testUser()));

        OrderDTO result = controller.updateStatus("ADMIN", "order-12345678-abcd",
                Map.of("status", "CANCELLED"));

        assertEquals("CANCELLED", result.getStatus());
        verify(productClient).restoreStock(anyList());
    }

    @Test
    void updateStatus_invalidTransition_shippedToPending_throws400() {
        Order o = testOrder("SHIPPED");
        when(orderRepo.findById("order-12345678-abcd")).thenReturn(Optional.of(o));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.updateStatus("ADMIN", "order-12345678-abcd",
                        Map.of("status", "PENDING")));
        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("Cannot change"));
    }

    @Test
    void updateStatus_deliveredToCancelled_throws400() {
        Order o = testOrder("DELIVERED");
        when(orderRepo.findById("order-12345678-abcd")).thenReturn(Optional.of(o));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.updateStatus("ADMIN", "order-12345678-abcd",
                        Map.of("status", "CANCELLED")));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void updateStatus_missingStatusField_throws400() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.updateStatus("ADMIN", "order-12345678-abcd",
                        Map.of()));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void updateStatus_cancelFeignFails_throws503AndStatusUnchanged() {
        Order o = testOrder("CONFIRMED");
        when(orderRepo.findById("order-12345678-abcd")).thenReturn(Optional.of(o));
        doThrow(new RuntimeException("unavailable")).when(productClient).restoreStock(anyList());

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.updateStatus("ADMIN", "order-12345678-abcd",
                        Map.of("status", "CANCELLED")));
        assertEquals(503, ex.getStatus());
        assertEquals("CONFIRMED", o.getStatus());
    }

    // ---- dashboardStats ----

    @Test
    void dashboardStats_admin_returnsStatsWithLowStock() {
        Order confirmed = testOrder("CONFIRMED");
        Order cancelled = testOrder("CANCELLED");
        cancelled.setId("order-cancelled-1234");
        when(orderRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(confirmed, cancelled));
        when(productClient.getLowStockProducts()).thenReturn(
                List.of(Map.of("productId", "prod-1", "stock", 3)));

        Map<String, Object> result = controller.dashboardStats("ADMIN");

        assertNotNull(result.get("overview"));
        assertNotNull(result.get("ordersByStatus"));
        assertNotNull(result.get("topProducts"));
        assertNotNull(result.get("lowStockProducts"));

        @SuppressWarnings("unchecked")
        Map<String, Object> overview = (Map<String, Object>) result.get("overview");
        assertEquals(2L, overview.get("totalOrders"));
        // Revenue excludes cancelled orders
        assertEquals(new BigDecimal("200.00"), overview.get("totalRevenue"));
    }

    @Test
    void dashboardStats_nonAdmin_throws403() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.dashboardStats("USER"));
        assertEquals(403, ex.getStatus());
    }
}
