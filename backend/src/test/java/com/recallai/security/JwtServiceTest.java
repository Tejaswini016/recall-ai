package com.recallai.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recallai.entity.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-with-at-least-thirty-two-characters";
    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
    private static final JwtProperties PROPERTIES = new JwtProperties(SECRET, 60);

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User("Ada", "ada@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 42L);
    }

    @Test
    void issuedTokenCarriesUserIdAndEmailAndExpiry() {
        JwtService.IssuedToken issued = jwtService.issue(user);

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        Optional<JwtService.TokenClaims> claims = jwtService.verify(issued.token());
        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(42L);
        assertThat(claims.get().email()).isEqualTo("ada@example.com");
    }

    @Test
    void expiredTokenIsRejected() {
        String token = jwtService.issue(user).token();
        JwtService later = new JwtService(PROPERTIES,
                Clock.fixed(NOW.plus(Duration.ofMinutes(61)), ZoneOffset.UTC));

        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void tokenStillValidJustBeforeExpiry() {
        String token = jwtService.issue(user).token();
        JwtService almostLater = new JwtService(PROPERTIES,
                Clock.fixed(NOW.plus(Duration.ofMinutes(59)), ZoneOffset.UTC));

        assertThat(almostLater.verify(token)).isPresent();
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        JwtService other = new JwtService(
                new JwtProperties("another-secret-that-is-also-long-enough-for-hmac", 60),
                Clock.fixed(NOW, ZoneOffset.UTC));
        String token = other.issue(user).token();

        assertThat(jwtService.verify(token)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.issue(user).token();
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2];

        assertThat(jwtService.verify(tampered)).isEmpty();
    }

    @Test
    void garbageIsRejectedWithoutThrowing() {
        assertThat(jwtService.verify("not-a-jwt")).isEmpty();
        assertThat(jwtService.verify("")).isEmpty();
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 60), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is too short");
    }
}
