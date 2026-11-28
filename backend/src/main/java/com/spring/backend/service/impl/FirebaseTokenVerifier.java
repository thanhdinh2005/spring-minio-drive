package com.spring.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.backend.exception.AppException;
import com.spring.backend.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verify Firebase ID token (RS256 JWT) bằng public certs của Google
 * securetoken service. Không cần Firebase Admin SDK / service account.
 */
@Component
@Slf4j
public class FirebaseTokenVerifier {

  // Public certs Google dùng ký Firebase ID token (public key -> không cần secret)
  private static final String CERTS_URL =
      "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";

  // Chỉ chấp nhận token do đúng Firebase project của mình ký (iss + aud)
  private static final String EXPECTED_ISSUER_PREFIX = "https://securetoken.google.com/";

  // Certs Google rotate định kỳ -> cache 1h
  private static final long CERTS_TTL_MS = 60 * 60 * 1000; // 1h

  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String projectId;

  private final Map<String, String> certCache = new ConcurrentHashMap<>(); // kid -> PEM cert
  private volatile long certsFetchedAt;

  public FirebaseTokenVerifier(
      @Value("${application.security.firebase.project-id:}") String projectId
  ) {
    this.restClient = RestClient.create();
    this.projectId = projectId;
  }

  /** Verify Firebase ID token -> trả email + name do Google chứng thực */
  public FirebaseUserInfo verify(String idToken) {
    if (projectId == null || projectId.isBlank()) {
      throw new AppException(ErrorCode.FIREBASE_LOGIN_NOT_CONFIGURED);
    }

    // kid trong JWT header -> chọn đúng cert (header chưa verify, chỉ để lookup)
    String kid = extractKeyId(idToken);
    PublicKey publicKey = resolvePublicKey(kid, idToken);

    // Verify chữ ký RS256 + expiry; fail thì fetch certs mới thử lại 1 lần
    // (phòng Google vừa rotate key)
    Claims claims;
    try {
      claims = Jwts.parserBuilder()
          .setSigningKey(publicKey)
          .build()
          .parseClaimsJws(idToken)
          .getBody();
    } catch (JwtException e) {
      publicKey = resolvePublicKey(kid, idToken, true);
      try {
        claims = Jwts.parserBuilder()
            .setSigningKey(publicKey)
            .build()
            .parseClaimsJws(idToken)
            .getBody();
      } catch (JwtException retryEx) {
        log.warn("Firebase token verification failed: {}", retryEx.getMessage());
        throw new AppException(ErrorCode.INVALID_FIREBASE_TOKEN);
      }
    }

    // Check iss/aud = projectId và email_verified = true
    String expectedIssuer = EXPECTED_ISSUER_PREFIX + projectId;
    if (!expectedIssuer.equals(claims.getIssuer())
        || !projectId.equals(claims.getAudience())) {
      throw new AppException(ErrorCode.INVALID_FIREBASE_TOKEN);
    }

    Object emailVerified = claims.get("email_verified");
    boolean verified = Boolean.TRUE.equals(emailVerified)
        || "true".equalsIgnoreCase(String.valueOf(emailVerified));
    if (!verified) {
      throw new AppException(ErrorCode.FIREBASE_EMAIL_NOT_VERIFIED);
    }

    String email = claims.get("email", String.class);
    if (email == null || email.isBlank()) {
      throw new AppException(ErrorCode.INVALID_FIREBASE_TOKEN);
    }

    return new FirebaseUserInfo(email, claims.get("name", String.class));
  }

  /** Đọc "kid" (key id) từ JWT header — chưa verify, chỉ để chọn cert */
  private String extractKeyId(String idToken) {
    try {
      String[] parts = idToken.split("\\.");
      if (parts.length != 3) {
        throw new AppException(ErrorCode.INVALID_FIREBASE_TOKEN);
      }
      JsonNode header = objectMapper.readTree(
          Base64.getUrlDecoder().decode(parts[0]));
      String kid = header.path("kid").asText(null);
      if (kid == null) {
        throw new AppException(ErrorCode.INVALID_FIREBASE_TOKEN);
      }
      return kid;
    } catch (Exception e) {
      throw new AppException(ErrorCode.INVALID_FIREBASE_TOKEN);
    }
  }

  private PublicKey resolvePublicKey(String kid, String idToken) {
    return resolvePublicKey(kid, idToken, false);
  }

  /** Tìm cert theo kid trong cache; miss thì fetch từ Google. forceRefresh bỏ qua cache */
  private PublicKey resolvePublicKey(String kid, String idToken, boolean forceRefresh) {
    String pem = forceRefresh ? null : certCache.get(kid);
    if (pem == null) {
      if (forceRefresh) {
        fetchCerts();
      } else {
        refreshCerts();
      }
      pem = certCache.get(kid);
    }
    if (pem == null) {
      throw new AppException(ErrorCode.INVALID_FIREBASE_TOKEN);
    }
    try {
      // PEM cert -> PublicKey RSA để verify chữ ký
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      X509Certificate cert = (X509Certificate) cf.generateCertificate(
          new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
      return cert.getPublicKey();
    } catch (Exception e) {
      throw new AppException(ErrorCode.INVALID_FIREBASE_TOKEN);
    }
  }

  /** Chỉ fetch certs mới khi cache quá 1h hoặc rỗng */
  private void refreshCerts() {
    if (System.currentTimeMillis() - certsFetchedAt < CERTS_TTL_MS
        && !certCache.isEmpty()) {
      return;
    }
    fetchCerts();
  }

  /** Fetch Map<kid, PEM> từ Google; synchronized -> chỉ fetch 1 lần khi nhiều request cùng miss */
  private void fetchCerts() {
    synchronized (this) {
      try {
        Map<String, String> certs = restClient.get()
            .uri(CERTS_URL)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
        if (certs != null) {
          certCache.clear();
          certCache.putAll(certs);
          certsFetchedAt = System.currentTimeMillis();
        }
      } catch (RestClientException e) {
        log.warn("Failed to fetch Firebase certs: {}", e.getMessage());
        throw new AppException(ErrorCode.INVALID_FIREBASE_TOKEN);
      }
    }
  }

  /** Email + name đã được Google chứng thực */
  public record FirebaseUserInfo(String email, String name) {
  }
}
