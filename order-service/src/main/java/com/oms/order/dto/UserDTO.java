package com.oms.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {
    private String id;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String address;
    private String createdAt;  // String, never Instant
}
