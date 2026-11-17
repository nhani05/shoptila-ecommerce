CREATE TABLE auth_users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(30)  NOT NULL DEFAULT 'ROLE_CUSTOMER',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_users_email       UNIQUE (email),
    CONSTRAINT ck_auth_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_auth_users_role        CHECK (role IN ('ROLE_CUSTOMER', 'ROLE_ADMIN'))
);

CREATE INDEX idx_auth_users_created_at ON auth_users (created_at);
