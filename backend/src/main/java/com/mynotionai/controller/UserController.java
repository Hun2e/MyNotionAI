package com.mynotionai.controller;

import com.mynotionai.dto.UserProfileUpdateRequest;
import com.mynotionai.dto.UserResponse;
import com.mynotionai.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
        Authentication authentication,
        @Valid @RequestBody UserProfileUpdateRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(authService.updateProfile(userId, request));
    }
}
