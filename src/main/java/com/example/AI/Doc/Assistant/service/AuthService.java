package com.example.AI.Doc.Assistant.service;

import com.example.AI.Doc.Assistant.model.User;
import com.example.AI.Doc.Assistant.repository.UserRepository;
import com.example.AI.Doc.Assistant.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;



@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public String signup(String email, String password) {

        if(userRepository.existsByEmail(email))
            throw new RuntimeException("Email exists");

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .build();

        userRepository.save(user);

        return jwtUtil.generateToken(user.getId(), email);
    }
}
