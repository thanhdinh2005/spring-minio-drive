package com.spring.backend.service.impl;

import com.spring.backend.entity.User;
import com.spring.backend.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtService {

  @Value("${application.security.jwt.secret-key}")
  private String secretKey;

  @Value("${application.security.jwt.expiration}")
  private long jwtExpiration;

  @PostConstruct
  private void validateSecretKey() {
    if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException(
        "JWT secret key must be at least 32 characters"
      );
    }
  }

  public String generateAccessToken(User user) {
    Date now = new Date();
    Date expiration = new Date(now.getTime() + jwtExpiration);

    return Jwts.builder()
      .setSubject(user.getEmail())
      .claim("userId", user.getId().toString())
      .setIssuedAt(now)
      .setExpiration(expiration)
      .signWith(getSigningKey(), SignatureAlgorithm.HS256)
      .compact();
  }

  public String extractUsername(String token) {
    try {
      return extractAllClaims(token).getSubject();
    } catch (JwtException e) {
      return null;
    }
  }

  public UUID extractUserId(String token) {
    try {
      String userId = extractAllClaims(token)
        .get("userId", String.class);

      return UUID.fromString(userId);
    } catch (JwtException | IllegalArgumentException e) {
      return null;
    }
  }

  public boolean isValid(
    String token,
    CustomUserDetails userDetails
  ) {
    try {
      Claims claims = extractAllClaims(token);

      return claims.getSubject().equals(userDetails.getUsername())
        && claims.getExpiration().after(new Date());

    } catch (JwtException e) {
      return false;
    }
  }

  public long getExpirationSeconds() {
    return jwtExpiration / 1000;
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parserBuilder()
      .setSigningKey(getSigningKey())
      .build()
      .parseClaimsJws(token)
      .getBody();
  }

  private Key getSigningKey() {
    return Keys.hmacShaKeyFor(
      secretKey.getBytes(StandardCharsets.UTF_8)
    );
  }
}
