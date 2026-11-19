package com.saferoute.ai.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saferoute.ai.dto.AuthDtos.LoginRequest;
import com.saferoute.ai.dto.AuthDtos.ProfileRequest;
import com.saferoute.ai.dto.AuthDtos.RegisterRequest;
import com.saferoute.ai.dto.AuthDtos.UserView;
import com.saferoute.ai.service.AuthService;
import com.saferoute.ai.service.CurrentUserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UserView.of(authService.register(request)));
    }

    @PostMapping("/login")
    public UserView login(@Valid @RequestBody LoginRequest request) {
        return UserView.of(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authService.logout();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserView me() {
        return UserView.of(currentUserService.currentUser());
    }

    @PutMapping("/me")
    public UserView updateMe(@Valid @RequestBody ProfileRequest request) {
        return UserView.of(authService.updateProfile(request));
    }
}
