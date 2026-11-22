-- Phones are stored as +94 and 9 digits from now on, so 0771234567, 771234567 and +94771234567 are the
-- same number for the unique index. Existing numbers in the other two forms are rewritten, except where
-- the rewritten number already belongs to another account. Anything else is left as it is.
WITH candidates AS (
    SELECT id,
           '+94' || right(phone, 9) AS e164,
           row_number() OVER (PARTITION BY right(phone, 9) ORDER BY id) AS pick
    FROM user_accounts
    WHERE phone ~ '^0?[1-9][0-9]{8}$'
)
UPDATE user_accounts account
SET phone = candidates.e164
FROM candidates
WHERE account.id = candidates.id
  AND candidates.pick = 1
  AND NOT EXISTS (SELECT 1 FROM user_accounts other WHERE other.phone = candidates.e164);
