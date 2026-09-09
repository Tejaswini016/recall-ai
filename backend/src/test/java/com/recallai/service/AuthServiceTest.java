package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recallai.dto.AuthResponse;
import com.recallai.dto.LoginRequest;
import com.recallai.dto.RegisterRequest;
import com.recallai.entity.User;
import com.recallai.exception.ConflictException;
import com.recallai.exception.InvalidCredentialsException;
import com.recallai.repository.UserRepository;
import com.recallai.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hashed(" + inv.getArgument(0) + ")");
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerNormalizesEmailHashesPasswordAndIssuesToken() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 7L));
        when(jwtService.issue(any(User.class)))
                .thenReturn(new JwtService.IssuedToken("jwt", Instant.parse("2026-09-10T00:00:00Z")));

        AuthResponse response = authService.register(
                new RegisterRequest("  Ada ", "  Ada@Example.COM ", "correct horse"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getValue().getName()).isEqualTo("Ada");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed(correct horse)");
        assertThat(response.token()).isEqualTo("jwt");
        assertThat(response.user().id()).isEqualTo(7L);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ada", "ada@example.com", "password1")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginSucceedsWithMatchingPassword() {
        User user = withId(new User("Ada", "ada@example.com", "stored-hash"), 7L);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "stored-hash")).thenReturn(true);
        when(jwtService.issue(user)).thenReturn(new JwtService.IssuedToken("jwt", Instant.now()));

        AuthResponse response = authService.login(new LoginRequest("ADA@example.com", "secret123"));

        assertThat(response.token()).isEqualTo("jwt");
        assertThat(response.user().email()).isEqualTo("ada@example.com");
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = new User("Ada", "ada@example.com", "stored-hash");
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ada@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwtService, never()).issue(any());
    }

    @Test
    void loginForUnknownEmailStillRunsPasswordCheckAndFails() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
        // The dummy hash is compared so response time does not reveal whether the email exists.
        verify(passwordEncoder).matches("whatever", "hashed(timing-equalizer)");
    }

    private static User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
