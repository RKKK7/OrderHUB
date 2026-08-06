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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private UserRepository userRepo;
    @Mock private PasswordEncoder encoder;
    @Mock private JwtUtil jwt;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(userRepo, encoder, jwt, "test_admin_secret");
    }

    private User sampleUser() {
        User u = new User();
        u.setId("user-1"); u.setName("Ravi"); u.setEmail("ravi@test.com");
        u.setPasswordHash("hashed"); u.setRole("USER"); u.setCreatedAt(Instant.now());
        return u;
    }

    @Test
    void register_success() {
        when(userRepo.existsByEmailIgnoreCase("ravi@test.com")).thenReturn(false);
        when(encoder.encode("test1234")).thenReturn("hashed");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> { User u = inv.getArgument(0); u.setId("user-1"); u.setCreatedAt(Instant.now()); return u; });
        when(jwt.sign("user-1", "USER")).thenReturn("jwt-token");

        Map<String, Object> res = controller.register(Map.of("name", "Ravi", "email", "ravi@test.com", "password", "test1234"));

        assertEquals("jwt-token", res.get("token"));
        assertNotNull(res.get("user"));
    }

    @Test
    void register_duplicateEmail_409() {
        when(userRepo.existsByEmailIgnoreCase("ravi@test.com")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.register(Map.of("name", "Ravi", "email", "ravi@test.com", "password", "test1234")));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void register_missingFields_400() {
        ApiException ex = assertThrows(ApiException.class, () -> controller.register(Map.of("name", "Ravi")));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void register_shortPassword_400() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.register(Map.of("name", "Ravi", "email", "r@t.com", "password", "12")));
        assertEquals(400, ex.getStatus());
    }

    @Test
    void register_adminWithWrongSecret_403() {
        when(userRepo.existsByEmailIgnoreCase("admin@test.com")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.register(Map.of("name", "Admin", "email", "admin@test.com", "password", "test1234", "role", "ADMIN", "adminSecret", "wrong")));
        assertEquals(403, ex.getStatus());
    }

    @Test
    void register_adminWithCorrectSecret_success() {
        when(userRepo.existsByEmailIgnoreCase("admin@test.com")).thenReturn(false);
        when(encoder.encode(any())).thenReturn("hashed");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> { User u = inv.getArgument(0); u.setId("admin-1"); u.setCreatedAt(Instant.now()); return u; });
        when(jwt.sign("admin-1", "ADMIN")).thenReturn("admin-jwt");

        Map<String, Object> res = controller.register(Map.of("name", "Admin", "email", "admin@test.com", "password", "test1234", "role", "ADMIN", "adminSecret", "test_admin_secret"));

        assertEquals("admin-jwt", res.get("token"));
        verify(jwt).sign("admin-1", "ADMIN");
    }

    @Test
    void login_success() {
        User u = sampleUser();
        when(userRepo.findByEmailIgnoreCase("ravi@test.com")).thenReturn(Optional.of(u));
        when(encoder.matches("test1234", "hashed")).thenReturn(true);
        when(jwt.sign("user-1", "USER")).thenReturn("jwt-token");

        Map<String, Object> res = controller.login(Map.of("email", "ravi@test.com", "password", "test1234"));
        assertEquals("jwt-token", res.get("token"));
    }

    @Test
    void login_wrongPassword_401() {
        User u = sampleUser();
        when(userRepo.findByEmailIgnoreCase("ravi@test.com")).thenReturn(Optional.of(u));
        when(encoder.matches("wrong", "hashed")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.login(Map.of("email", "ravi@test.com", "password", "wrong")));
        assertEquals(401, ex.getStatus());
    }

    @Test
    void login_nonExistentEmail_401() {
        when(userRepo.findByEmailIgnoreCase("ghost@test.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.login(Map.of("email", "ghost@test.com", "password", "test1234")));
        assertEquals(401, ex.getStatus());
    }

    @Test
    void me_withValidUserId_returnsUser() {
        when(userRepo.findById("user-1")).thenReturn(Optional.of(sampleUser()));
        Map<String, Object> res = controller.me("user-1");
        assertNotNull(res.get("user"));
    }

    @Test
    void me_withoutUserId_401() {
        ApiException ex = assertThrows(ApiException.class, () -> controller.me(null));
        assertEquals(401, ex.getStatus());
    }
}
