CREATE TABLE integration.if_delivery (
 request_key uuid PRIMARY KEY REFERENCES integration.if_request(id) ON DELETE CASCADE,
 interface_id varchar(80) NOT NULL,source_system varchar(80) NOT NULL,request_id varchar(80) NOT NULL,
 scope varchar(40) NOT NULL,payload_hash char(64) NOT NULL,replay_cipher text NOT NULL,
 state varchar(20) NOT NULL CHECK(state IN ('WAIT','LEASED','DONE','DEAD_LETTER')),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 10),
 max_attempts integer NOT NULL CHECK(max_attempts BETWEEN 1 AND 10),
 next_at timestamptz NOT NULL DEFAULT now(),lease_token uuid,lease_until timestamptz,
 retryable boolean NOT NULL DEFAULT false,last_error varchar(80),
 UNIQUE(interface_id,source_system,request_id,scope));
CREATE INDEX ix_delivery_due ON integration.if_delivery(state,next_at,lease_until);
-- Historical requests intentionally have no idempotency registration or replay source.
