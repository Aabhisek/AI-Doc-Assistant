package com.example.AI.Doc.Assistant.controller;


import com.example.AI.Doc.Assistant.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public String signup(@RequestBody Map<String,String> req){

        return authService.signup(req.get("email"), req.get("password"));
    }
}
