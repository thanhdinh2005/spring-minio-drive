package com.spring.backend.service;

import com.spring.backend.dto.payment.CreatePaymentResponse;

import java.util.Map;
import java.util.UUID;

public interface PaymentService {

  CreatePaymentResponse createPayment(UUID userId, String ipAddress);

  Map<String, String> processIpn(Map<String, String> params);
}
