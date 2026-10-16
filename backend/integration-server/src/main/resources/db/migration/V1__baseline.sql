CREATE TABLE integration.if_request (
 id uuid PRIMARY KEY, request_id varchar(80) NOT NULL, interface_id varchar(80) NOT NULL DEFAULT 'IF-USER-001',
 trace_id varchar(32) NOT NULL, attempt_id uuid NOT NULL UNIQUE,
 status varchar(24) NOT NULL CHECK (status IN ('SENDING','SUCCESS','FAILED','UNKNOWN')),
 http_status integer, error_code varchar(80), created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now());
 CREATE INDEX ix_request_trace ON integration.if_request(trace_id);
 CREATE INDEX ix_request_created ON integration.if_request(created_at DESC);
