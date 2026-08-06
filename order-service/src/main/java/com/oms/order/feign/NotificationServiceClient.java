package com.oms.order.feign;

import com.oms.order.dto.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "notification-service")
public interface NotificationServiceClient {

    @PostMapping("/internal/notifications/send")
    Map<String, Object> send(@RequestBody NotificationRequest request);
}
