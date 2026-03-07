package com.example.AI.Doc.Assistant.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.UUID;


@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    public String generateToken(UUID userId, String email) {

        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("email", email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis()+86400000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .compact();
    }
    public UUID validateAndGetUserId(String token) {

        try {

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return UUID.fromString(claims.getSubject());

        } catch (Exception e) {
            return null;
        }
    }
}


