package com.oms.order.controller;

import com.oms.order.dto.*;
import com.oms.order.dto.StockDTOs.*;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock private OrderRepository orderRepo;
    @Mock private UserRepository userRepo;
    @Mock private ProductServiceClient productClient;
    @Mock private OrderEventPublisher eventPublisher;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderRepo, userRepo, productClient, eventPublisher);
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

    private PlaceOrderRequest validRequest() {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setShippingAddress("123 Main St");
        req.setItems(List.of(new PlaceOrderRequest.Item("prod-1", 2)));
        return req;
    }

    private ReservationResponse successReservation() {
        ReservationResponse res = new ReservationResponse();
        res.setSuccess(true);
        ReservedItem ri = new ReservedItem("prod-1", "Widget", new BigDecimal("100.00"), 2);
        res.setItems(List.of(ri));
        return res;
    }

    private Order savedOrder() {
        Order o = new Order();
        o.setId("order-12345678-abcd");
        o.setUserId("user-1");
        o.setStatus("CONFIRMED");
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

    // ---- placeOrder ----

    @Test
    void placeOrder_happyPath_savesOrderAndPublishesEvent() {
        User user = testUser();
        when(userRepo.findById("user-1")).thenReturn(Optional.of(user));
        when(productClient.checkAndReserve(anyList())).thenReturn(successReservation());
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId("order-12345678-abcd");
            o.setCreatedAt(Instant.now());
            o.setUpdatedAt(Instant.now());
            return o;
        });

        var result = controller.placeOrder("user-1", "USER", validRequest());

        assertNotNull(result.get("order"));
        verify(productClient).checkAndReserve(anyList());
        verify(orderRepo).save(any(Order.class));
        verify(eventPublisher).publish(any());
    }

    @Test
    void placeOrder_noAuthentication_throws401() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.placeOrder(null, "USER", validRequest()));
        assertEquals(401, ex.getStatus());
    }

    @Test
    void placeOrder_adminRole_throws403() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.placeOrder("user-1", "ADMIN", validRequest()));
        assertEquals(403, ex.getStatus());
    }

    @Test
    void placeOrder_emptyItems_throws400() {
        when(userRepo.findById("user-1")).thenReturn(Optional.of(testUser()));

        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setShippingAddress("123 Main St");
        req.setItems(List.of());

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.placeOrder("user-1", "USER", req));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void placeOrder_noShippingAddress_throws400() {
        when(userRepo.findById("user-1")).thenReturn(Optional.of(testUser()));

        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setShippingAddress("");
        req.setItems(List.of(new PlaceOrderRequest.Item("prod-1", 1)));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.placeOrder("user-1", "USER", req));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void placeOrder_insufficientStock_throws400() {
        when(userRepo.findById("user-1")).thenReturn(Optional.of(testUser()));

        ReservationResponse failRes = new ReservationResponse();
        failRes.setSuccess(false);
        failRes.setFailedItems(List.of(new FailedItem("prod-1", 10, 2, "Not enough stock")));
        when(productClient.checkAndReserve(anyList())).thenReturn(failRes);

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.placeOrder("user-1", "USER", validRequest()));
        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("Insufficient stock"));
    }

    @Test
    void placeOrder_feignUnavailable_throws503() {
        when(userRepo.findById("user-1")).thenReturn(Optional.of(testUser()));
        when(productClient.checkAndReserve(anyList())).thenThrow(new RuntimeException("Connection refused"));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.placeOrder("user-1", "USER", validRequest()));
        assertEquals(503, ex.getStatus());
    }

    @Test
    void placeOrder_dbSaveFailure_compensatesStock() {
        when(userRepo.findById("user-1")).thenReturn(Optional.of(testUser()));
        when(productClient.checkAndReserve(anyList())).thenReturn(successReservation());
        when(orderRepo.save(any(Order.class))).thenThrow(new RuntimeException("DB error"));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.placeOrder("user-1", "USER", validRequest()));
        assertEquals(500, ex.getStatus());
        verify(productClient).restoreStock(anyList());
    }

    // ---- myOrders ----

    @Test
    void myOrders_returnsUserOrders() {
        Order o = savedOrder();
        when(orderRepo.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(o));

        List<OrderDTO> result = controller.myOrders("user-1");

        assertEquals(1, result.size());
        assertEquals("order-12345678-abcd", result.get(0).getId());
    }

    @Test
    void myOrders_noAuth_throws401() {
        ApiException ex = assertThrows(ApiException.class, () -> controller.myOrders(null));
        assertEquals(401, ex.getStatus());
    }

    // ---- getOrder ----

    @Test
    void getOrder_ownerAccess_returnsOrder() {
        Order o = savedOrder();
        when(orderRepo.findById("order-12345678-abcd")).thenReturn(Optional.of(o));

        OrderDTO result = controller.getOrder("user-1", "order-12345678-abcd");

        assertEquals("order-12345678-abcd", result.getId());
    }

    @Test
    void getOrder_notOwner_throws403() {
        Order o = savedOrder();
        when(orderRepo.findById("order-12345678-abcd")).thenReturn(Optional.of(o));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.getOrder("other-user", "order-12345678-abcd"));
        assertEquals(403, ex.getStatus());
    }

    // ---- cancelOrder ----

    @Test
    void cancelOrder_confirmedOrder_cancelsAndRestoresStock() {
        Order o = savedOrder(); // status = CONFIRMED
        when(orderRepo.findById("order-12345678-abcd")).thenReturn(Optional.of(o));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findById("user-1")).thenReturn(Optional.of(testUser()));

        OrderDTO result = controller.cancelOrder("user-1", "order-12345678-abcd");

        assertEquals("CANCELLED", result.getStatus());
        verify(productClient).restoreStock(anyList());
        verify(eventPublisher).publish(any());
    }

    @Test
    void cancelOrder_shippedOrder_throws400() {
        Order o = savedOrder();
        o.setStatus("SHIPPED");
        when(orderRepo.findById("order-12345678-abcd")).thenReturn(Optional.of(o));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.cancelOrder("user-1", "order-12345678-abcd"));
        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("shipped"));
    }

    @Test
    void cancelOrder_notOwner_throws403() {
        Order o = savedOrder();
        when(orderRepo.findById("order-12345678-abcd")).thenReturn(Optional.of(o));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.cancelOrder("other-user", "order-12345678-abcd"));
        assertEquals(403, ex.getStatus());
    }

    @Test
    void cancelOrder_feignRestoreFails_throws503AndDoesNotUpdateStatus() {
        Order o = savedOrder();
        when(orderRepo.findById("order-12345678-abcd")).thenReturn(Optional.of(o));
        doThrow(new RuntimeException("Connection refused")).when(productClient).restoreStock(anyList());

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.cancelOrder("user-1", "order-12345678-abcd"));
        assertEquals(503, ex.getStatus());
        // Order status must remain unchanged
        assertEquals("CONFIRMED", o.getStatus());
        verify(orderRepo, never()).save(any());
    }
}
