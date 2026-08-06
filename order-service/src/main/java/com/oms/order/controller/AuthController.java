package com.oms.order.controller;

import com.oms.order.dto.ApiException;
import com.oms.order.dto.UserDTO;
import com.oms.order.model.User;
import com.oms.order.repository.UserRepository;
import com.oms.order.service.JwtUtil;
import com.oms.order.service.OrderMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/users")
public class AuthController {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;
    private final String adminSecret;

    public AuthController(UserRepository userRepo, PasswordEncoder encoder,
                          JwtUtil jwt, @Value("${app.admin.secret}") String adminSecret) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwt = jwt;
        this.adminSecret = adminSecret;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        String name = str(body, "name"), email = str(body, "email"),
               password = str(body, "password"), role = str(body, "role"),
               adminSecretIn = str(body, "adminSecret");

        if (name == null || email == null || password == null)
            throw new ApiException(400, "name, email, and password are required");
        if (password.length() < 6)
            throw new ApiException(400, "Password must be at least 6 characters");
        if (userRepo.existsByEmailIgnoreCase(email))
            throw new ApiException(409, "Email already registered");
        if ("ADMIN".equalsIgnoreCase(role) && !adminSecret.equals(adminSecretIn))
            throw new ApiException(403, "Invalid admin secret");

        User u = new User();
        u.setName(name);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(encoder.encode(password));
        u.setRole("ADMIN".equalsIgnoreCase(role) ? "ADMIN" : "USER");
        if (body.get("phone") != null) u.setPhone(body.get("phone").toString());
        if (body.get("address") != null) u.setAddress(body.get("address").toString());
        u = userRepo.save(u);

        // JWT includes role — gateway extracts it and injects X-User-Role header
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("token", jwt.sign(u.getId(), u.getRole()));
        res.put("user", OrderMapper.toUserDTO(u));
        return res;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String email = str(body, "email"), password = str(body, "password");
        User u = userRepo.findByEmailIgnoreCase(email != null ? email : "").orElse(null);
        if (u == null || !encoder.matches(password != null ? password : "", u.getPasswordHash()))
            throw new ApiException(401, "Invalid credentials");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("token", jwt.sign(u.getId(), u.getRole()));
        res.put("user", OrderMapper.toUserDTO(u));
        return res;
    }

    @GetMapping("/me")
    public Map<String, Object> me(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId == null) throw new ApiException(401, "Not authenticated");
        User u = userRepo.findById(userId)
                .orElseThrow(() -> new ApiException(404, "User not found"));
        return Map.of("user", OrderMapper.toUserDTO(u));
    }

    @PutMapping("/me")
    public Map<String, Object> updateProfile(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody Map<String, Object> body) {
        if (userId == null) throw new ApiException(401, "Not authenticated");
        User u = userRepo.findById(userId)
                .orElseThrow(() -> new ApiException(404, "User not found"));
        if (body.get("name") != null) u.setName(body.get("name").toString());
        if (body.get("phone") != null) u.setPhone(body.get("phone").toString());
        if (body.get("address") != null) u.setAddress(body.get("address").toString());
        u = userRepo.save(u);
        return Map.of("user", OrderMapper.toUserDTO(u));
    }

    private static String str(Map<String, Object> b, String k) {
        Object v = b.get(k); return v == null ? null : v.toString();
    }
}
