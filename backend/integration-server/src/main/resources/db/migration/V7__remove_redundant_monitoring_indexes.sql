-- V6 ordering indexes cover the old creation/status order prefixes.
DROP INDEX integration.ix_request_created;
DROP INDEX integration.ix_request_status_created;
-- Measured WAIT selection uses the existing state/next_at covering index.
-- Keep the selective expired-lease index; avoid another write on every WAIT transition.
DROP INDEX integration.ix_delivery_wait_due;
