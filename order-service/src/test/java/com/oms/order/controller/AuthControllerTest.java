package com.oms.order.controller;

import com.oms.order.dto.ApiException;
import com.oms.order.model.User;
import com.oms.order.repository.UserRepository;
import com.oms.order.service.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private UserRepository userRepo;
    @Mock private PasswordEncoder encoder;
    @Mock private JwtUtil jwt;

    private AuthController controller;

    private static final String ADMIN_SECRET = "test-admin-secret";

    @BeforeEach
    void setUp() {
        controller = new AuthController(userRepo, encoder, jwt, ADMIN_SECRET);
    }

    // ---- register ----

    @Test
    void register_happyPath_returnsTokenAndUser() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "John");
        body.put("email", "john@example.com");
        body.put("password", "secret123");

        when(userRepo.existsByEmailIgnoreCase("john@example.com")).thenReturn(false);
        when(encoder.encode("secret123")).thenReturn("hashed");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-1");
            u.setCreatedAt(Instant.now());
            return u;
        });
        when(jwt.sign("user-1", "USER")).thenReturn("jwt-token");

        Map<String, Object> result = controller.register(body);

        assertEquals("jwt-token", result.get("token"));
        assertNotNull(result.get("user"));
        verify(userRepo).save(any(User.class));
    }

    @Test
    void register_missingRequiredFields_throws400() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "John");
        // missing email and password

        ApiException ex = assertThrows(ApiException.class, () -> controller.register(body));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void register_shortPassword_throws400() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "John");
        body.put("email", "john@example.com");
        body.put("password", "abc");

        ApiException ex = assertThrows(ApiException.class, () -> controller.register(body));
        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("6 characters"));
    }

    @Test
    void register_duplicateEmail_throws409() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "John");
        body.put("email", "john@example.com");
        body.put("password", "secret123");

        when(userRepo.existsByEmailIgnoreCase("john@example.com")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> controller.register(body));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void register_adminWithWrongSecret_throws403() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Admin");
        body.put("email", "admin@example.com");
        body.put("password", "secret123");
        body.put("role", "ADMIN");
        body.put("adminSecret", "wrong-secret");

        when(userRepo.existsByEmailIgnoreCase("admin@example.com")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> controller.register(body));
        assertEquals(403, ex.getStatus());
    }

    @Test
    void register_adminWithCorrectSecret_setsAdminRole() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Admin");
        body.put("email", "admin@example.com");
        body.put("password", "secret123");
        body.put("role", "ADMIN");
        body.put("adminSecret", ADMIN_SECRET);

        when(userRepo.existsByEmailIgnoreCase("admin@example.com")).thenReturn(false);
        when(encoder.encode("secret123")).thenReturn("hashed");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("admin-1");
            u.setCreatedAt(Instant.now());
            return u;
        });
        when(jwt.sign("admin-1", "ADMIN")).thenReturn("admin-jwt");

        Map<String, Object> result = controller.register(body);

        assertEquals("admin-jwt", result.get("token"));
        verify(jwt).sign("admin-1", "ADMIN");
    }

    // ---- login ----

    @Test
    void login_validCredentials_returnsTokenAndUser() {
        Map<String, Object> body = new HashMap<>();
        body.put("email", "john@example.com");
        body.put("password", "secret123");

        User user = new User();
        user.setId("user-1");
        user.setName("John");
        user.setEmail("john@example.com");
        user.setPasswordHash("hashed");
        user.setRole("USER");
        user.setCreatedAt(Instant.now());

        when(userRepo.findByEmailIgnoreCase("john@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("secret123", "hashed")).thenReturn(true);
        when(jwt.sign("user-1", "USER")).thenReturn("jwt-token");

        Map<String, Object> result = controller.login(body);

        assertEquals("jwt-token", result.get("token"));
        assertNotNull(result.get("user"));
    }

    @Test
    void login_invalidPassword_throws401() {
        Map<String, Object> body = new HashMap<>();
        body.put("email", "john@example.com");
        body.put("password", "wrong");

        User user = new User();
        user.setId("user-1");
        user.setPasswordHash("hashed");

        when(userRepo.findByEmailIgnoreCase("john@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "hashed")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> controller.login(body));
        assertEquals(401, ex.getStatus());
    }

    @Test
    void login_unknownEmail_throws401() {
        Map<String, Object> body = new HashMap<>();
        body.put("email", "unknown@example.com");
        body.put("password", "whatever");

        when(userRepo.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> controller.login(body));
        assertEquals(401, ex.getStatus());
    }

    // ---- me (getProfile) ----

    @Test
    void me_authenticated_returnsUser() {
        User user = new User();
        user.setId("user-1");
        user.setName("John");
        user.setEmail("john@example.com");
        user.setRole("USER");
        user.setCreatedAt(Instant.now());

        when(userRepo.findById("user-1")).thenReturn(Optional.of(user));

        Map<String, Object> result = controller.me("user-1");

        assertNotNull(result.get("user"));
    }

    @Test
    void me_noUserId_throws401() {
        ApiException ex = assertThrows(ApiException.class, () -> controller.me(null));
        assertEquals(401, ex.getStatus());
    }

    // ---- updateProfile ----

    @Test
    void updateProfile_updatesFieldsAndReturnsUser() {
        User user = new User();
        user.setId("user-1");
        user.setName("John");
        user.setEmail("john@example.com");
        user.setRole("USER");
        user.setCreatedAt(Instant.now());

        when(userRepo.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("name", "John Updated");
        body.put("phone", "1234567890");

        Map<String, Object> result = controller.updateProfile("user-1", body);

        assertNotNull(result.get("user"));
        assertEquals("John Updated", user.getName());
        assertEquals("1234567890", user.getPhone());
    }

    @Test
    void updateProfile_noUserId_throws401() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.updateProfile(null, Map.of()));
        assertEquals(401, ex.getStatus());
    }
}
