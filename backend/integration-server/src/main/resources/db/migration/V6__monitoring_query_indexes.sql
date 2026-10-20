CREATE INDEX ix_request_created_key ON integration.if_request(created_at DESC,id DESC);
CREATE INDEX ix_request_status_created_key ON integration.if_request(status,created_at DESC,id DESC);
CREATE INDEX ix_delivery_wait_due ON integration.if_delivery(next_at,request_key) WHERE state='WAIT';
CREATE INDEX ix_delivery_lease_due ON integration.if_delivery(lease_until,request_key) WHERE state='LEASED';
