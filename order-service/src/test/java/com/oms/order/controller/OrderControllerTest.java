package com.oms.order.controller;

import com.oms.order.dto.*;
import com.oms.order.dto.StockDTOs.*;
import com.oms.order.feign.NotificationServiceClient;
import com.oms.order.feign.ProductServiceClient;
import com.oms.order.model.*;
import com.oms.order.repository.OrderRepository;
import com.oms.order.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock private OrderRepository orderRepo;
    @Mock private UserRepository userRepo;
    @Mock private ProductServiceClient productClient;
    @Mock private NotificationServiceClient notifClient;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderRepo, userRepo, productClient, notifClient);
    }

    private User sampleUser() {
        User u = new User();
        u.setId("user-1"); u.setName("Ravi"); u.setEmail("ravi@test.com");
        u.setRole("USER"); u.setCreatedAt(Instant.now());
        return u;
    }

    private PlaceOrderRequest validRequest() {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setShippingAddress("123 Main St, Bengaluru");
        req.setItems(List.of(new PlaceOrderRequest.Item("prod-1", 2)));
        return req;
    }

    private ReservationResponse successfulReservation() {
        ReservationResponse r = new ReservationResponse();
        r.setSuccess(true);
        r.setItems(List.of(new ReservedItem("prod-1", "Laptop", BigDecimal.valueOf(49999), 2)));
        r.setFailedItems(List.of());
        return r;
    }

    private Order savedOrder() {
        Order o = new Order();
        o.setId("order-100"); o.setUserId("user-1"); o.setStatus("CONFIRMED");
        o.setTotalAmount(BigDecimal.valueOf(99998));
        o.setShippingAddress("123 Main St"); o.setCreatedAt(Instant.now());
        OrderItem item = new OrderItem();
        item.setId("item-1"); item.setProductId("prod-1");
        item.setProductName("Laptop"); item.setQuantity(2);
        item.setUnitPrice(BigDecimal.valueOf(49999)); item.setOrder(o);
        o.setItems(List.of(item));
        return o;
    }

    // ---------- PLACE ORDER ----------

    @Test
    void placeOrder_success() {
        when(userRepo.findById("user-1")).thenReturn(Optional.of(sampleUser()));
        when(productClient.checkAndReserve(any())).thenReturn(successfulReservation());
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId("order-100"); o.setCreatedAt(Instant.now());
            return o;
        });

        Map<String, Object> res = controller.placeOrder("user-1", "USER", validRequest());

        assertNotNull(res.get("order"));
        verify(productClient, times(1)).checkAndReserve(any());
        verify(orderRepo, times(1)).save(any(Order.class));
        verify(notifClient, times(1)).send(any(NotificationRequest.class));
    }

    @Test
    void placeOrder_insufficientStock_400() {
        when(userRepo.findById("user-1")).thenReturn(Optional.of(sampleUser()));
        ReservationResponse failed = new ReservationResponse();
        failed.setSuccess(false);
        failed.setItems(List.of());
        failed.setFailedItems(List.of(new FailedItem("prod-1", 2, 0, "INSUFFICIENT_STOCK")));
        when(productClient.checkAndReserve(any())).thenReturn(failed);

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.placeOrder("user-1", "USER", validRequest()));

        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("INSUFFICIENT_STOCK"));
        verify(orderRepo, never()).save(any());
    }

    @Test
    void placeOrder_emptyItems_400() {
        when(userRepo.findById("user-1")).thenReturn(Optional.of(sampleUser()));
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setShippingAddress("123 Main St");
        req.setItems(List.of());

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.placeOrder("user-1", "USER", req));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void placeOrder_noShippingAddress_400() {
        when(userRepo.findById("user-1")).thenReturn(Optional.of(sampleUser()));
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setShippingAddress("");
        req.setItems(List.of(new PlaceOrderRequest.Item("prod-1", 1)));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.placeOrder("user-1", "USER", req));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void placeOrder_byAdmin_403() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.placeOrder("admin-1", "ADMIN", validRequest()));
        assertEquals(403, ex.getStatus());
    }

    @Test
    void placeOrder_notAuthenticated_401() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.placeOrder(null, "USER", validRequest()));
        assertEquals(401, ex.getStatus());
    }

    @Test
    void placeOrder_dbFailure_compensatesStock() {
        when(userRepo.findById("user-1")).thenReturn(Optional.of(sampleUser()));
        when(productClient.checkAndReserve(any())).thenReturn(successfulReservation());
        when(orderRepo.save(any(Order.class))).thenThrow(new RuntimeException("DB crashed"));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.placeOrder("user-1", "USER", validRequest()));

        assertEquals(500, ex.getStatus());
        // CRITICAL: stock must be restored after failed order save
        verify(productClient, times(1)).restoreStock(any());
    }

    @Test
    void placeOrder_notificationFailure_doesNotFailOrder() {
        when(userRepo.findById("user-1")).thenReturn(Optional.of(sampleUser()));
        when(productClient.checkAndReserve(any())).thenReturn(successfulReservation());
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId("order-100"); o.setCreatedAt(Instant.now());
            return o;
        });
        when(notifClient.send(any())).thenThrow(new RuntimeException("Notification service down"));

        // Order should still succeed despite notification failure
        Map<String, Object> res = controller.placeOrder("user-1", "USER", validRequest());

        assertNotNull(res.get("order"));
        verify(orderRepo, times(1)).save(any(Order.class));
    }

    // ---------- CANCEL ORDER ----------

    @Test
    void cancelOrder_success_restoresStockBeforeStatusChange() {
        Order order = savedOrder();
        when(orderRepo.findById("order-100")).thenReturn(Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findById("user-1")).thenReturn(Optional.of(sampleUser()));

        OrderDTO result = controller.cancelOrder("user-1", "order-100");

        assertEquals("CANCELLED", result.getStatus());
        // Stock restored BEFORE status change — this is the fix from architecture review
        verify(productClient, times(1)).restoreStock(any());
        verify(orderRepo, times(1)).save(any());
    }

    @Test
    void cancelOrder_shippedOrder_400() {
        Order order = savedOrder();
        order.setStatus("SHIPPED");
        when(orderRepo.findById("order-100")).thenReturn(Optional.of(order));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.cancelOrder("user-1", "order-100"));

        assertEquals(400, ex.getStatus());
        verify(productClient, never()).restoreStock(any());
    }

    @Test
    void cancelOrder_otherUsersOrder_403() {
        Order order = savedOrder();
        when(orderRepo.findById("order-100")).thenReturn(Optional.of(order));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.cancelOrder("other-user", "order-100"));

        assertEquals(403, ex.getStatus());
    }

    @Test
    void cancelOrder_productServiceDown_503_orderUnchanged() {
        Order order = savedOrder();
        when(orderRepo.findById("order-100")).thenReturn(Optional.of(order));
        doThrow(new RuntimeException("Product service down")).when(productClient).restoreStock(any());

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.cancelOrder("user-1", "order-100"));

        assertEquals(503, ex.getStatus());
        // Order must remain CONFIRMED — cancellation was rejected because stock couldn't be restored
        assertEquals("CONFIRMED", order.getStatus());
        verify(orderRepo, never()).save(any());
    }
}
