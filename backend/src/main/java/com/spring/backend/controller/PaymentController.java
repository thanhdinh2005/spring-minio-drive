package com.spring.backend.controller;

import com.spring.backend.dto.payment.CreatePaymentResponse;
import com.spring.backend.common.AppResponse;
import com.spring.backend.security.CustomUserDetails;
import com.spring.backend.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  /**
   * Tạo giao dịch thanh toán Premium qua VNPay.
   * Người dùng phải đăng nhập.
   */
  @PostMapping("/create")
  public ResponseEntity<AppResponse<CreatePaymentResponse>> createPayment(
    @AuthenticationPrincipal CustomUserDetails me,
    HttpServletRequest request
  ) {
    String ipAddress = request.getRemoteAddr();

    CreatePaymentResponse response = paymentService.createPayment(
      me.getId(),
      ipAddress
    );

    return ResponseEntity.ok(AppResponse.success(response));
  }

  /**
   * VNPay gọi endpoint này để thông báo kết quả giao dịch.
   * Endpoint cần được cấu hình public trong SecurityConfig.
   */
  @GetMapping("/ipn")
  public ResponseEntity<Map<String, String>> receiveIpn(
    @RequestParam Map<String, String> params
  ) {
    Map<String, String> response = paymentService.processIpn(params);

    return ResponseEntity.ok(response);
  }
}
