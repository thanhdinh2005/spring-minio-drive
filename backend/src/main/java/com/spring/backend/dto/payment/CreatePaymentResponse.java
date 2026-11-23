package com.spring.backend.dto.payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreatePaymentResponse {

  private String txnRef;

  private Long amount;

  private String paymentUrl;
}
