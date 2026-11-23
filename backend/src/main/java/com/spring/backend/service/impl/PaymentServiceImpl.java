package com.spring.backend.service.impl;

import com.spring.backend.config.VnPayConfig;
import com.spring.backend.dto.payment.CreatePaymentResponse;
import com.spring.backend.entity.Payment;
import com.spring.backend.entity.User;
import com.spring.backend.entity.enums.PaymentStatus;
import com.spring.backend.entity.enums.Plan;
import com.spring.backend.repository.PaymentRepository;
import com.spring.backend.repository.UserRepository;
import com.spring.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

  private static final long PREMIUM_PRICE = 29_000L;
  private static final long PREMIUM_STORAGE_LIMIT =
    10L * 1024 * 1024 * 1024;

  private static final ZoneId VIETNAM_ZONE =
    ZoneId.of("Asia/Ho_Chi_Minh");

  private static final DateTimeFormatter VNPAY_DATE_FORMAT =
    DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final PaymentRepository paymentRepository;
  private final UserRepository userRepository;
  private final VnPayConfig vnPayConfig;

  @Override
  @Transactional
  public CreatePaymentResponse createPayment(UUID userId, String ipAddress) {

    User user = userRepository.findById(userId)
      .orElseThrow(() -> new IllegalArgumentException(
        "User not found"
      ));

    String txnRef = UUID.randomUUID()
      .toString()
      .replace("-", "");

    String orderInfo = "Thanh toan goi Premium 1 thang";

    // Lưu giao dịch PENDING trước khi chuyển người dùng sang VNPay.
    Payment payment = new Payment();
    payment.setUser(user);
    payment.setTxnRef(txnRef);
    payment.setAmount(PREMIUM_PRICE);
    payment.setOrderInfo(orderInfo);
    payment.setStatus(PaymentStatus.PENDING);

    paymentRepository.save(payment);

    LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);

    TreeMap<String, String> params = new TreeMap<>();

    params.put("vnp_Version", "2.1.0");
    params.put("vnp_Command", "pay");
    params.put("vnp_TmnCode", vnPayConfig.getTmnCode());
    params.put("vnp_Amount", String.valueOf(PREMIUM_PRICE * 100));
    params.put("vnp_CurrCode", "VND");
    params.put("vnp_TxnRef", txnRef);
    params.put("vnp_OrderInfo", orderInfo);
    params.put("vnp_OrderType", "other");
    params.put("vnp_Locale", "vn");
    params.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
    params.put(
      "vnp_IpAddr",
      ipAddress == null || ipAddress.isBlank()
        ? "127.0.0.1"
        : ipAddress
    );
    params.put(
      "vnp_CreateDate",
      now.format(VNPAY_DATE_FORMAT)
    );
    params.put(
      "vnp_ExpireDate",
      now.plusMinutes(15).format(VNPAY_DATE_FORMAT)
    );

    String hashData = buildQueryString(params);
    String secureHash = hmacSHA512(
      vnPayConfig.getHashSecret(),
      hashData
    );

    String paymentUrl = vnPayConfig.getUrl()
      + "?"
      + hashData
      + "&vnp_SecureHash="
      + secureHash;

    return CreatePaymentResponse.builder()
      .txnRef(txnRef)
      .amount(PREMIUM_PRICE)
      .paymentUrl(paymentUrl)
      .build();
  }

  @Override
  @Transactional
  public Map<String, String> processIpn(Map<String, String> params) {

    // 1. Kiểm tra chữ ký VNPay.
    if (!verifySignature(params)) {
      return ipnResponse("97", "Invalid signature");
    }

    // 2. Kiểm tra mã website/merchant.
    if (!vnPayConfig.getTmnCode().equals(params.get("vnp_TmnCode"))) {
      return ipnResponse("97", "Invalid merchant");
    }

    String txnRef = params.get("vnp_TxnRef");

    if (txnRef == null || txnRef.isBlank()) {
      return ipnResponse("01", "Order not found");
    }

    // 3. Khóa giao dịch để tránh xử lý đồng thời nhiều lần.
    Payment payment = paymentRepository
      .findByTxnRefForUpdate(txnRef)
      .orElse(null);

    if (payment == null) {
      return ipnResponse("01", "Order not found");
    }

    // 4. Đối chiếu số tiền.
    try {
      long expectedAmount = Math.multiplyExact(
        payment.getAmount(),
        100L
      );

      long receivedAmount = Long.parseLong(
        params.get("vnp_Amount")
      );

      if (receivedAmount != expectedAmount) {
        return ipnResponse("04", "Invalid amount");
      }
    } catch (Exception e) {
      return ipnResponse("04", "Invalid amount");
    }

    // 5. Không xử lý lại giao dịch đã được xác nhận.
    if (payment.getStatus() != PaymentStatus.PENDING) {
      return ipnResponse("02", "Order already confirmed");
    }

    String responseCode = params.get("vnp_ResponseCode");
    String transactionStatus = params.get("vnp_TransactionStatus");

    payment.setResponseCode(responseCode);
    payment.setTransactionStatus(transactionStatus);
    payment.setTransactionNo(params.get("vnp_TransactionNo"));
    payment.setPayDate(params.get("vnp_PayDate"));

    // 6. Giao dịch không thành công: cập nhật FAILED.
    if (!"00".equals(responseCode)
      || !"00".equals(transactionStatus)) {

      payment.setStatus(PaymentStatus.FAILED);
      paymentRepository.save(payment);

      return ipnResponse("00", "Confirm Success");
    }

    // 7. Giao dịch thành công: khóa tài khoản và cấp Premium.
    User user = userRepository
      .findByIdForUpdate(payment.getUser().getId())
      .orElse(null);

    if (user == null) {
      return ipnResponse("01", "User not found");
    }

    Instant now = Instant.now();

    Instant currentExpiry = user.getPremiumExpiresAt();

    Instant baseTime = currentExpiry != null
      && currentExpiry.isAfter(now)
      ? currentExpiry
      : now;

    Instant newExpiry = ZonedDateTime
      .ofInstant(baseTime, VIETNAM_ZONE)
      .plusMonths(1)
      .toInstant();

    user.setPlan(Plan.PREMIUM);
    user.setStorageLimit(PREMIUM_STORAGE_LIMIT);
    user.setPremiumExpiresAt(newExpiry);

    payment.setStatus(PaymentStatus.SUCCESS);

    userRepository.save(user);
    paymentRepository.save(payment);

    return ipnResponse("00", "Confirm Success");
  }

  private String buildQueryString(Map<String, String> params) {
    return params.entrySet()
      .stream()
      .map(entry ->
        encode(entry.getKey())
          + "="
          + encode(entry.getValue())
      )
      .collect(Collectors.joining("&"));
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String hmacSHA512(String key, String data) {
    try {
      Mac hmac = Mac.getInstance("HmacSHA512");

      SecretKeySpec secretKey = new SecretKeySpec(
        key.getBytes(StandardCharsets.UTF_8),
        "HmacSHA512"
      );

      hmac.init(secretKey);

      byte[] hash = hmac.doFinal(
        data.getBytes(StandardCharsets.UTF_8)
      );

      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException(
        "Cannot generate VNPay signature",
        e
      );
    }
  }

  private boolean verifySignature(Map<String, String> params) {

    String receivedHash = params.get("vnp_SecureHash");

    if (receivedHash == null || receivedHash.isBlank()) {
      return false;
    }

    TreeMap<String, String> sortedParams = new TreeMap<>(params);

    sortedParams.remove("vnp_SecureHash");
    sortedParams.remove("vnp_SecureHashType");

    String hashData = buildQueryString(sortedParams);

    String calculatedHash = hmacSHA512(
      vnPayConfig.getHashSecret(),
      hashData
    );

    return MessageDigest.isEqual(
      calculatedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII),
      receivedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII)
    );
  }

  private Map<String, String> ipnResponse(
    String responseCode,
    String message
  ) {
    return Map.of(
      "RspCode", responseCode,
      "Message", message
    );
  }
}
