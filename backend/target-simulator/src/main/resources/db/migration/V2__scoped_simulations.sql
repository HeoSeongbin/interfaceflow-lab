CREATE TABLE target.if_simulation (
 id uuid PRIMARY KEY, type varchar(32) NOT NULL,
 interface_id varchar(80) NOT NULL, request_id varchar(80) NOT NULL,
 delay_ms integer NOT NULL CHECK(delay_ms BETWEEN 0 AND 15000),
 failure_count integer NOT NULL CHECK(failure_count BETWEEN 0 AND 5),
 expires_at timestamptz NOT NULL, uses integer NOT NULL DEFAULT 0 CHECK(uses BETWEEN 0 AND 20),
 created_at timestamptz NOT NULL DEFAULT now());
ALTER TABLE target.if_target_receipt ADD COLUMN simulation_id uuid REFERENCES target.if_simulation(id),
 ADD COLUMN simulation_sequence integer, ADD COLUMN planned_http_status integer;
CREATE INDEX ix_receipt_simulation ON target.if_target_receipt(simulation_id);
