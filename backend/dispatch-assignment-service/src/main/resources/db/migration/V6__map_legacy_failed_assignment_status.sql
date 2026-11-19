UPDATE dispatch_assignments
SET status = 'CANCELLED',
    failure_reason = COALESCE(failure_reason, 'Parcel delivery failed')
WHERE status = 'FAILED';
