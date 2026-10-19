ALTER TABLE integration.if_request ADD COLUMN simulation_id uuid;
-- Target owns scenario definitions. No cross-service FK.
CREATE INDEX ix_request_simulation ON integration.if_request(simulation_id) WHERE simulation_id IS NOT NULL;
