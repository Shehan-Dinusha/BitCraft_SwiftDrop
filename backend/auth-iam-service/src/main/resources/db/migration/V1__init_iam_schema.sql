CREATE TABLE IF NOT EXISTS user_accounts (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id BIGINT,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES user_accounts(id)
);

CREATE TABLE IF NOT EXISTS role_assignments (
    id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(100) NOT NULL,
    user_id BIGINT,
    CONSTRAINT fk_role_assignment_user FOREIGN KEY (user_id) REFERENCES user_accounts(id)
);
