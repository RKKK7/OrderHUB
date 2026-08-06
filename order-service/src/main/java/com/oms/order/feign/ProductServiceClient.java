package com.oms.order.feign;

import com.oms.order.dto.StockDTOs.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "product-service")
public interface ProductServiceClient {

    @PostMapping("/internal/products/check-and-reserve")
    ReservationResponse checkAndReserve(@RequestBody List<StockItem> items);

    @PostMapping("/internal/products/restore-stock")
    void restoreStock(@RequestBody List<StockItem> items);

    @GetMapping("/internal/products/low-stock")
    List<Map<String, Object>> getLowStockProducts();
}
