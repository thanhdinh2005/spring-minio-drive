package com.spring.backend.entity;

import com.spring.backend.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "txn_ref", nullable = false, unique = true, length = 100)
  private String txnRef;

  @Column(nullable = false)
  private Long amount;

  @Column(name = "order_info", nullable = false)
  private String orderInfo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status = PaymentStatus.PENDING;

  @Column(name = "transaction_no", length = 100)
  private String transactionNo;

  @Column(name = "response_code", length = 10)
  private String responseCode;

  @Column(name = "transaction_status", length = 10)
  private String transactionStatus;

  @Column(name = "pay_date", length = 20)
  private String payDate;
}
