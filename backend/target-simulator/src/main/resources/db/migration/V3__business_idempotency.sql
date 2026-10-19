CREATE TABLE target.if_business_result (
 interface_id varchar(80) NOT NULL,source_system varchar(80) NOT NULL,request_id varchar(80) NOT NULL,
 scope varchar(40) NOT NULL,payload_hash char(64) NOT NULL,code varchar(40) NOT NULL,
 processed_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(interface_id,source_system,request_id,scope));
ALTER TABLE target.if_target_receipt ADD COLUMN replayed boolean NOT NULL DEFAULT false;
