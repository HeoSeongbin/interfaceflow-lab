CREATE TABLE integration.if_attempt (
 id uuid PRIMARY KEY, request_key uuid NOT NULL REFERENCES integration.if_request(id) ON DELETE CASCADE,
 attempt_number integer NOT NULL CHECK(attempt_number > 0), status varchar(24) NOT NULL,
 http_status integer, error_code varchar(80), started_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 completed_at timestamptz, duration_ms bigint, UNIQUE(request_key,attempt_number));
CREATE TABLE integration.if_trace_event (
 id bigserial PRIMARY KEY, request_key uuid NOT NULL REFERENCES integration.if_request(id) ON DELETE CASCADE,
 attempt_id uuid REFERENCES integration.if_attempt(id), stage varchar(40) NOT NULL,
 outcome varchar(24) NOT NULL, recorded_at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE INDEX ix_trace_event_request ON integration.if_trace_event(request_key,id);
-- Legacy requests remain readable. No invented historical events or attempt timings.
