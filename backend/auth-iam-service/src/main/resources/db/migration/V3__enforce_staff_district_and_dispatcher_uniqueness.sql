ALTER TABLE user_accounts
    ADD CONSTRAINT ck_user_accounts_staff_district
    CHECK (role NOT IN ('COURIER', 'DISPATCHER') OR district IS NOT NULL);

CREATE UNIQUE INDEX ux_user_accounts_active_dispatcher_district
    ON user_accounts (district)
    WHERE role = 'DISPATCHER' AND status = 'ACTIVE';

COMMENT ON COLUMN user_accounts.role IS
    'Canonical single application role. Legacy role_assignments is not an authorization source.';
