package com.example.demo.controller;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.SignupRequest;
import com.example.demo.security.JwtService;

import com.example.demo.service.AuthService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JwtService jwtService;
    private final AuthService authService;

    public AuthController(JwtService jwtService, AuthService authService) {
        this.jwtService = jwtService;
        this.authService= authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {

        // Temporary authentication for our demo
        if (authService.authenticate(request.getUsername(), request.getPassword())) {

            String token =
                    jwtService.generateToken(request.getUsername());

            return new LoginResponse(token);
        }

        throw new RuntimeException("Invalid username or password");
    }
    @PostMapping("/signup")
    public String signup(@RequestBody SignupRequest request)
    {
        authService.signup(request.getUsername(), request.getPassword());
        return "User registed successfully";
    }

}
