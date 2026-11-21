-- Business application details: what the business sells, its rough monthly volume, a website or social
-- page for the dispatcher to check, and the pickup city and postal code (bulk uploads fall back to them).
-- Nullable: applications submitted before this migration do not have them.
ALTER TABLE merchant_accounts
    ADD COLUMN IF NOT EXISTS business_category VARCHAR(32),
    ADD COLUMN IF NOT EXISTS monthly_parcel_volume VARCHAR(32),
    ADD COLUMN IF NOT EXISTS website_url VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pickup_city VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pickup_postal_code VARCHAR(5);
