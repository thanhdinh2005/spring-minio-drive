package com.spring.backend.repository;

import com.spring.backend.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  Optional<Payment> findByTxnRef(String txnRef);

  boolean existsByTxnRef(String txnRef);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Payment p WHERE p.txnRef = :txnRef")
  Optional<Payment> findByTxnRefForUpdate(@Param("txnRef") String txnRef);
}
