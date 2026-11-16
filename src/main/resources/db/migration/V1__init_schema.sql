CREATE TABLE tb_ship (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(50)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tb_user (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login_id      VARCHAR(30)   NOT NULL,
    name          VARCHAR(30)   NOT NULL,
    password      VARCHAR(255)  NOT NULL,
    role          VARCHAR(20)   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    ship_id       BIGINT        REFERENCES tb_ship(id),

    CONSTRAINT uq_user_login UNIQUE (login_id),

    CONSTRAINT chk_user_role CHECK (role IN ('ADMIN','MEDICAL_OFFICER')),
    CONSTRAINT chk_user_ship CHECK (
        (role = 'ADMIN' AND ship_id IS NULL) OR
        (role = 'MEDICAL_OFFICER' AND ship_id IS NOT NULL))
);

CREATE TABLE tb_medication (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(50)  NOT NULL,
    category   VARCHAR(30),
    unit       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tb_stock_requirement (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    min_quantity  INT           NOT NULL CHECK (min_quantity >= 0),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    ship_id       BIGINT        NOT NULL REFERENCES tb_ship(id),
    medication_id BIGINT        NOT NULL REFERENCES tb_medication(id),

    CONSTRAINT uq_requirement UNIQUE (ship_id, medication_id)
);

CREATE TABLE tb_stock_lot (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lot_number         VARCHAR(50)  NOT NULL,
    initial_quantity   INT          NOT NULL CHECK (initial_quantity > 0),
    remaining_quantity INT          NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    discarded_reason   VARCHAR(255),
    expiry_date        DATE         NOT NULL,
    received_at        TIMESTAMPTZ  NOT NULL,
    discarded_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    received_by        BIGINT       NOT NULL REFERENCES tb_user(id),
    discarded_by       BIGINT       REFERENCES tb_user(id),
    ship_id            BIGINT       NOT NULL REFERENCES tb_ship(id),
    medication_id      BIGINT       NOT NULL REFERENCES tb_medication(id),

    CONSTRAINT uq_lot UNIQUE (ship_id, medication_id, lot_number),

    CONSTRAINT chk_lot_remaining_nonneg  CHECK (remaining_quantity >= 0),
    CONSTRAINT chk_lot_remaining_le_init CHECK (remaining_quantity <= initial_quantity),
    CONSTRAINT chk_lot_status            CHECK (status IN ('ACTIVE','DISCARDED'))
);

CREATE TABLE tb_usage_record (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    total_quantity  INT          NOT NULL CHECK (total_quantity > 0),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    used_at         TIMESTAMPTZ  NOT NULL,
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   VARCHAR(255),
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    cancelled_by    BIGINT       REFERENCES tb_user(id),
    ship_id         BIGINT       NOT NULL REFERENCES tb_ship(id),
    medication_id   BIGINT       NOT NULL REFERENCES tb_medication(id),
    user_id         BIGINT       NOT NULL REFERENCES tb_user(id),

    CONSTRAINT uq_usage_idempotency UNIQUE (user_id, idempotency_key),

    CONSTRAINT chk_usage_status CHECK (status IN ('ACTIVE','CANCELLED'))
);

CREATE TABLE tb_usage_lot_allocation (
     id                 BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
     allocated_quantity INT         NOT NULL CHECK (allocated_quantity > 0),
     created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

     usage_id           BIGINT      NOT NULL REFERENCES tb_usage_record(id),
     lot_id             BIGINT      NOT NULL REFERENCES tb_stock_lot(id),

     CONSTRAINT uq_alloc UNIQUE (usage_id, lot_id)
);

CREATE INDEX idx_alloc_lot ON tb_usage_lot_allocation (lot_id);
CREATE INDEX idx_usage_ship_med  ON tb_usage_record (ship_id, medication_id);
CREATE INDEX idx_usage_user_time ON tb_usage_record (user_id, used_at);
CREATE INDEX idx_lot_fefo
    ON tb_stock_lot (ship_id, medication_id, expiry_date, received_at, id)
    WHERE status = 'ACTIVE';