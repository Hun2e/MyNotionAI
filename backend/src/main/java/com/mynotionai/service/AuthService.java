package com.mynotionai.service;

import com.mynotionai.dto.AuthResponse;
import com.mynotionai.dto.GoogleLoginRequest;
import com.mynotionai.dto.UserLoginRequest;
import com.mynotionai.dto.UserProfileUpdateRequest;
import com.mynotionai.dto.UserRegisterRequest;
import com.mynotionai.dto.UserResponse;
import com.mynotionai.entity.User;
import com.mynotionai.repository.UserRepository;
import com.mynotionai.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleTokenVerifierService googleTokenVerifierService;

    @Transactional
    public AuthResponse register(UserRegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        User user = User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .nickname(request.getNickname())
            .provider(User.Provider.LOCAL)
            .build();

        User savedUser = userRepository.save(user);
        String token = jwtTokenProvider.generateToken(savedUser.getId());

        return AuthResponse.builder()
            .accessToken(token)
            .user(UserResponse.from(savedUser))
            .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(UserLoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtTokenProvider.generateToken(user.getId());

        return AuthResponse.builder()
            .accessToken(token)
            .user(UserResponse.from(user))
            .build();
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleTokenVerifierService.GoogleUserInfo googleUser =
            googleTokenVerifierService.verifyIdToken(request.getIdToken());

        User user = userRepository
            .findByProviderAndProviderUserId(User.Provider.GOOGLE, googleUser.providerUserId())
            .orElseGet(() -> createGoogleUser(googleUser));

        String token = jwtTokenProvider.generateToken(user.getId());
        return AuthResponse.builder()
            .accessToken(token)
            .user(UserResponse.from(user))
            .build();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UserProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setNickname(request.getNickname());
        return UserResponse.from(userRepository.save(user));
    }

    private User createGoogleUser(GoogleTokenVerifierService.GoogleUserInfo googleUser) {
        User existingByEmail = userRepository.findByEmail(googleUser.email()).orElse(null);
        if (existingByEmail != null) {
            if (existingByEmail.getProvider() != User.Provider.GOOGLE) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This email is already registered with a local account"
                );
            }
            return existingByEmail;
        }

        String nickname = googleUser.name();
        if (nickname == null || nickname.isBlank()) {
            nickname = googleUser.email().split("@")[0];
        }

        User googleAccount = User.builder()
            .email(googleUser.email())
            .passwordHash(null)
            .nickname(nickname)
            .provider(User.Provider.GOOGLE)
            .providerUserId(googleUser.providerUserId())
            .build();

        return userRepository.save(googleAccount);
    }
}
