package com.spring.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "vnpay")
public class VnPayConfig {

  private String tmnCode;

  private String hashSecret;

  private String url;

  private String returnUrl;
}
