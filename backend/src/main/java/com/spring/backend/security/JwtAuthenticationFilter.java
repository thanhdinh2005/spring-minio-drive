package com.spring.backend.security;

import com.spring.backend.entity.User;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.impl.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter{
  private final JwtService jwtService;
  private final UserRepository userRepository;

  @Override
  protected void doFilterInternal(
    @NonNull HttpServletRequest request,
    @NonNull HttpServletResponse response,
    @NonNull FilterChain filterChain
  ) throws ServletException, IOException {

    // 1. Bỏ qua nếu không có Bearer token
    String token = extractToken(request);
    if (token == null) {
      filterChain.doFilter(request, response);
      return;
    }

    // 2. Bỏ qua nếu đã có authentication trong context (request đã được xử lý)
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      filterChain.doFilter(request, response);
      return;
    }

    // 3. Extract email từ token — trả null nếu token malformed/invalid
    String email = jwtService.extractUsername(token);
    if (email == null) {
      log.warn("Cannot extract email from token, path: {}", request.getRequestURI());
      filterChain.doFilter(request, response);
      return;
    }

    // 4. Load user từ DB
    Optional<User> userOpt = userRepository.findByEmail(email);
    if (userOpt.isEmpty()) {
      // Token hợp lệ nhưng user không còn tồn tại trong DB
      log.warn("Token valid but user not found: {}, path: {}", email, request.getRequestURI());
      filterChain.doFilter(request, response);
      return;
    }

    User user = userOpt.get();
    Set<GrantedAuthority> authority = Set.of(
      new SimpleGrantedAuthority(user.getRole().name())
    );
    CustomUserDetails userDetails = new CustomUserDetails(user, authority);

    // 5. Validate token gắn với đúng user này
    if (!jwtService.isValid(token, userDetails)) {
      log.warn("Token invalid or expired for user: {}", email);
      filterChain.doFilter(request, response);
      return;
    }

    // 6. Check user có đang active không
    if (!userDetails.isEnabled()) {
      log.warn("Disabled user attempted access: {}", email);
      filterChain.doFilter(request, response);
      return;
    }

    // 7. Set authentication vào SecurityContext
    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
      userDetails,
      null,
      userDetails.getAuthorities()
    );
    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(auth);

    log.debug("Authenticated user: {}, path: {}", email, request.getRequestURI());

    filterChain.doFilter(request, response);
  }

  /**
   * Extract Bearer token từ Authorization header.
   * Trả về null nếu header không tồn tại hoặc không đúng format.
   */
  private String extractToken(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    String token = authHeader.substring(7).trim();
    return token.isEmpty() ? null : token;
  }
}

