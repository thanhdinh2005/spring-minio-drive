
-- Thêm thời hạn sử dụng gói Premium
ALTER TABLE users
  ADD COLUMN premium_expires_at TIMESTAMP NULL;

-- Bảng lưu giao dịch thanh toán
CREATE TABLE payments (
                        id                  UUID PRIMARY KEY,
                        user_id             UUID NOT NULL,
                        txn_ref             VARCHAR(100) NOT NULL UNIQUE,
                        amount              BIGINT NOT NULL,
                        order_info          VARCHAR(255) NOT NULL,
                        status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                        transaction_no      VARCHAR(100),
                        response_code       VARCHAR(10),
                        transaction_status  VARCHAR(10),
                        pay_date            VARCHAR(20),
                        created_at          TIMESTAMP NOT NULL DEFAULT now(),
                        updated_at          TIMESTAMP NOT NULL DEFAULT now(),

                        CONSTRAINT fk_payments_user
                          FOREIGN KEY (user_id)
                            REFERENCES users(id),

                        CONSTRAINT chk_payments_amount
                          CHECK (amount > 0),

                        CONSTRAINT chk_payments_status
                          CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_payments_user_id
  ON payments(user_id);

CREATE INDEX idx_payments_status
  ON payments(status);
