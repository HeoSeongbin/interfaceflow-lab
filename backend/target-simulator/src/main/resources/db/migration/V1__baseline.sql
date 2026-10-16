CREATE TABLE target.if_target_receipt (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,request_id varchar(80) NOT NULL,trace_id varchar(32) NOT NULL,attempt_id uuid NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
 CREATE INDEX ix_receipt_trace ON target.if_target_receipt(trace_id);
