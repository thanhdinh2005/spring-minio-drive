package com.spring.backend.security;

import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.Status;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

@Getter
public class CustomUserDetails implements UserDetails {
  private final User user;
  private final Collection<? extends GrantedAuthority> authorities;

  public CustomUserDetails(User user, Collection<? extends GrantedAuthority> authorities) {
    this.user = user;
    this.authorities = authorities;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() { return user.getPasswordHash(); }

  @Override
  public String getUsername() { return user.getEmail(); }

  @Override
  public boolean isEnabled() { return user.getStatus().equals(Status.ACTIVE); }

  public UUID getId() { return user.getId(); }
}
