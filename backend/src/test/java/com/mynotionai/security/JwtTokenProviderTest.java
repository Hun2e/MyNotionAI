package com.mynotionai.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtTokenProvider 단위 테스트.
 *
 * 토큰은 인증의 근거이므로 "유효한 토큰이 통과하는가"보다
 * "잘못된 토큰이 확실히 거부되는가"가 더 중요하다.
 */
@DisplayName("JwtTokenProvider 단위 테스트")
class JwtTokenProviderTest {

    private static final String SECRET =
        "test-secret-key-for-unit-tests-only-do-not-use-in-production";
    private static final long ONE_DAY_MS = 86_400_000L;
    private static final Long USER_ID = 42L;

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        // @Value 주입 필드를 테스트에서 직접 설정한다
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", ONE_DAY_MS);
    }

    /** 다른 만료 시간을 가진 프로바이더를 만든다. */
    private JwtTokenProvider providerWithExpiration(long expirationMs) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", expirationMs);
        return provider;
    }

    @Nested
    @DisplayName("정상 발급")
    class ValidToken {

        @Test
        @DisplayName("발급한 토큰에서 userId를 그대로 꺼낼 수 있다")
        void extractsUserId() {
            String token = jwtTokenProvider.generateToken(USER_ID);

            assertThat(jwtTokenProvider.extractUserId(token)).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("발급한 토큰은 유효하고 만료되지 않은 상태다")
        void isValidAndNotExpired() {
            String token = jwtTokenProvider.generateToken(USER_ID);

            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            assertThat(jwtTokenProvider.isTokenExpired(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("위조·손상된 토큰 거부")
    class InvalidToken {

        @Test
        @DisplayName("서명이 다른 키로 만들어진 토큰은 거부한다")
        void rejectsTokenSignedWithDifferentKey() {
            JwtTokenProvider attacker = new JwtTokenProvider();
            ReflectionTestUtils.setField(attacker, "jwtSecret",
                "another-secret-key-that-is-long-enough-to-be-accepted-by-hmac");
            ReflectionTestUtils.setField(attacker, "jwtExpirationMs", ONE_DAY_MS);

            String forged = attacker.generateToken(USER_ID);

            assertThat(jwtTokenProvider.validateToken(forged)).isFalse();
        }

        @Test
        @DisplayName("페이로드가 변조된 토큰은 거부한다")
        void rejectsTamperedPayload() {
            String token = jwtTokenProvider.generateToken(USER_ID);
            String[] parts = token.split("\\.");
            // 페이로드 부분을 임의로 바꾼다
            String tampered = parts[0] + "." + parts[1] + "X." + parts[2];

            assertThat(jwtTokenProvider.validateToken(tampered)).isFalse();
        }

        @Test
        @DisplayName("형식이 아예 다른 문자열은 거부한다")
        void rejectsMalformedToken() {
            assertThat(jwtTokenProvider.validateToken("not-a-jwt-at-all")).isFalse();
            assertThat(jwtTokenProvider.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("null 토큰은 거부한다")
        void rejectsNullToken() {
            assertThat(jwtTokenProvider.validateToken(null)).isFalse();
        }

        @Test
        @DisplayName("유효하지 않은 토큰에서 userId를 꺼내려 하면 예외가 발생한다")
        void extractingFromInvalidTokenThrows() {
            assertThatThrownBy(() -> jwtTokenProvider.extractUserId("not-a-jwt-at-all"))
                .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("만료 처리")
    class Expiration {

        @Test
        @DisplayName("이미 만료된 토큰은 유효하지 않다")
        void expiredTokenIsInvalid() {
            // 만료 시간을 음수로 두어 발급 즉시 만료된 토큰을 만든다
            JwtTokenProvider expiredProvider = providerWithExpiration(-1000L);
            String expiredToken = expiredProvider.generateToken(USER_ID);

            assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
        }

        @Test
        @DisplayName("만료된 토큰은 isTokenExpired가 true를 반환한다")
        void expiredTokenReportsExpired() {
            JwtTokenProvider expiredProvider = providerWithExpiration(-1000L);
            String expiredToken = expiredProvider.generateToken(USER_ID);

            assertThat(jwtTokenProvider.isTokenExpired(expiredToken)).isTrue();
        }

        @Test
        @DisplayName("해석할 수 없는 토큰은 만료된 것으로 간주한다")
        void unparsableTokenIsTreatedAsExpired() {
            // 안전한 기본값: 판단할 수 없으면 만료로 처리한다
            assertThat(jwtTokenProvider.isTokenExpired("garbage")).isTrue();
        }
    }
}
