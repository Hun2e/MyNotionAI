package com.mynotionai.service;

import com.mynotionai.dto.AuthResponse;
import com.mynotionai.dto.GoogleLoginRequest;
import com.mynotionai.dto.UserLoginRequest;
import com.mynotionai.dto.UserRegisterRequest;
import com.mynotionai.entity.User;
import com.mynotionai.exception.BusinessException;
import com.mynotionai.repository.UserRepository;
import com.mynotionai.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AuthService 단위 테스트.
 *
 * 인증은 실패 경로가 핵심이다.
 * - 중복 가입 차단
 * - 잘못된 자격 증명에서 정보가 새지 않는가
 * - 로컬 계정과 소셜 계정의 이메일 충돌 처리
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    private static final String EMAIL = "test@example.com";
    private static final String RAW_PASSWORD = "password123";
    private static final String HASHED_PASSWORD = "$2a$10$hashedvalue";
    private static final String TOKEN = "jwt.token.value";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private GoogleTokenVerifierService googleTokenVerifierService;

    @InjectMocks
    private AuthService authService;

    private User localUser() {
        return User.builder()
            .id(1L)
            .email(EMAIL)
            .passwordHash(HASHED_PASSWORD)
            .nickname("테스터")
            .provider(User.Provider.LOCAL)
            .build();
    }

    @Nested
    @DisplayName("회원가입")
    class Register {

        @Test
        @DisplayName("이미 등록된 이메일로는 가입할 수 없다")
        void rejectsDuplicateEmail() {
            given(userRepository.existsByEmail(EMAIL)).willReturn(true);

            UserRegisterRequest request = new UserRegisterRequest();
            request.setEmail(EMAIL);
            request.setPassword(RAW_PASSWORD);
            request.setNickname("테스터");

            assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getStatus())
                    .isEqualTo(HttpStatus.CONFLICT));

            // 중복이면 저장을 시도하지 않아야 한다
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("비밀번호는 평문이 아니라 해시로 저장된다")
        void storesHashedPassword() {
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(HASHED_PASSWORD);
            given(userRepository.save(any(User.class))).willReturn(localUser());
            given(jwtTokenProvider.generateToken(anyLong())).willReturn(TOKEN);

            UserRegisterRequest request = new UserRegisterRequest();
            request.setEmail(EMAIL);
            request.setPassword(RAW_PASSWORD);
            request.setNickname("테스터");

            AuthResponse response = authService.register(request);

            assertThat(response.getAccessToken()).isEqualTo(TOKEN);
            // 평문 비밀번호가 인코더를 거쳤는지 확인
            verify(passwordEncoder).encode(RAW_PASSWORD);
            ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(savedUser.capture());
            assertThat(savedUser.getValue().getPasswordHash())
                .isEqualTo(HASHED_PASSWORD)
                .isNotEqualTo(RAW_PASSWORD);
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("존재하지 않는 이메일은 401을 반환한다")
        void rejectsUnknownEmail() {
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

            UserLoginRequest request = new UserLoginRequest();
            request.setEmail(EMAIL);
            request.setPassword(RAW_PASSWORD);

            assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getStatus())
                    .isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        @DisplayName("비밀번호가 틀리면 401을 반환한다")
        void rejectsWrongPassword() {
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(localUser()));
            given(passwordEncoder.matches("wrong-password", HASHED_PASSWORD)).willReturn(false);

            UserLoginRequest request = new UserLoginRequest();
            request.setEmail(EMAIL);
            request.setPassword("wrong-password");

            assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getStatus())
                    .isEqualTo(HttpStatus.UNAUTHORIZED));

            // 실패 시 토큰이 발급되지 않아야 한다
            verify(jwtTokenProvider, never()).generateToken(anyLong());
        }

        @Test
        @DisplayName("없는 이메일과 틀린 비밀번호는 같은 응답을 준다")
        void doesNotLeakWhetherEmailExists() {
            // 두 경우 모두 401 + 동일 메시지여야 계정 존재 여부가 새지 않는다
            given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(localUser()));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

            UserLoginRequest unknownEmail = new UserLoginRequest();
            unknownEmail.setEmail("unknown@example.com");
            unknownEmail.setPassword(RAW_PASSWORD);

            UserLoginRequest wrongPassword = new UserLoginRequest();
            wrongPassword.setEmail(EMAIL);
            wrongPassword.setPassword("wrong");

            BusinessException first = catchBusinessException(
                () -> authService.login(unknownEmail));
            BusinessException second = catchBusinessException(
                () -> authService.login(wrongPassword));

            assertThat(first.getErrorCode()).isEqualTo(second.getErrorCode());
            assertThat(first.getMessage()).isEqualTo(second.getMessage());
        }

        private BusinessException catchBusinessException(Runnable action) {
            try {
                action.run();
                throw new AssertionError("예외가 발생하지 않았다");
            } catch (BusinessException e) {
                return e;
            }
        }
    }

    @Nested
    @DisplayName("구글 로그인")
    class GoogleLogin {

        private GoogleLoginRequest googleRequest() {
            GoogleLoginRequest request = new GoogleLoginRequest();
            request.setIdToken("google-id-token");
            return request;
        }

        private GoogleTokenVerifierService.GoogleUserInfo googleUserInfo() {
            return new GoogleTokenVerifierService.GoogleUserInfo(
                "google-user-id", EMAIL, "구글사용자");
        }

        @Test
        @DisplayName("같은 이메일의 로컬 계정이 있으면 충돌로 거부한다")
        void rejectsWhenEmailBelongsToLocalAccount() {
            given(googleTokenVerifierService.verifyIdToken(anyString()))
                .willReturn(googleUserInfo());
            given(userRepository.findByProviderAndProviderUserId(
                User.Provider.GOOGLE, "google-user-id"))
                .willReturn(Optional.empty());
            given(userRepository.findByEmail(EMAIL))
                .willReturn(Optional.of(localUser()));

            assertThatThrownBy(() -> authService.loginWithGoogle(googleRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getStatus())
                    .isEqualTo(HttpStatus.CONFLICT));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 연결된 구글 계정은 새로 만들지 않고 재사용한다")
        void reusesExistingGoogleAccount() {
            User googleUser = User.builder()
                .id(2L)
                .email(EMAIL)
                .nickname("구글사용자")
                .provider(User.Provider.GOOGLE)
                .providerUserId("google-user-id")
                .build();

            given(googleTokenVerifierService.verifyIdToken(anyString()))
                .willReturn(googleUserInfo());
            given(userRepository.findByProviderAndProviderUserId(
                User.Provider.GOOGLE, "google-user-id"))
                .willReturn(Optional.of(googleUser));
            given(jwtTokenProvider.generateToken(2L)).willReturn(TOKEN);

            AuthResponse response = authService.loginWithGoogle(googleRequest());

            assertThat(response.getAccessToken()).isEqualTo(TOKEN);
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("사용자 조회")
    class GetUser {

        @Test
        @DisplayName("존재하지 않는 사용자는 404를 반환한다")
        void rejectsUnknownUserId() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getUserById(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND));
        }
    }
}
