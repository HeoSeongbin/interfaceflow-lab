CREATE TABLE integration.if_system (
 system_id varchar(80) PRIMARY KEY, system_name varchar(120) NOT NULL,
 role varchar(10) NOT NULL CHECK(role IN ('SOURCE','TARGET')), created_at timestamptz NOT NULL DEFAULT now());
INSERT INTO integration.if_system(system_id,system_name,role) VALUES
 ('SYSTEM_A','가상 출발 시스템','SOURCE'),('SYSTEM_B','가상 대상 시뮬레이터','TARGET');
CREATE TABLE integration.if_interface (
 interface_id varchar(80) PRIMARY KEY,interface_name varchar(120) NOT NULL,
 source_system varchar(80) NOT NULL REFERENCES integration.if_system(system_id),
 target_system varchar(80) NOT NULL REFERENCES integration.if_system(system_id),
 enabled boolean NOT NULL,current_version integer NOT NULL CHECK(current_version>0),
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE integration.if_interface_version (
 interface_id varchar(80) NOT NULL REFERENCES integration.if_interface(interface_id) ON DELETE CASCADE,
 version integer NOT NULL,snapshot jsonb NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(interface_id,version));
CREATE TABLE integration.if_field_mapping (
 interface_id varchar(80) NOT NULL,version integer NOT NULL,position integer NOT NULL,rule jsonb NOT NULL,
 PRIMARY KEY(interface_id,version,position),
 FOREIGN KEY(interface_id,version) REFERENCES integration.if_interface_version(interface_id,version) ON DELETE CASCADE);
CREATE TABLE integration.if_validation_rule (
 interface_id varchar(80) NOT NULL,version integer NOT NULL,position integer NOT NULL,rule jsonb NOT NULL,
 PRIMARY KEY(interface_id,version,position),
 FOREIGN KEY(interface_id,version) REFERENCES integration.if_interface_version(interface_id,version) ON DELETE CASCADE);
INSERT INTO integration.if_interface(interface_id,interface_name,source_system,target_system,enabled,current_version)
 VALUES('IF-USER-001','사용자 정보 연동','SYSTEM_A','SYSTEM_B',true,1);
INSERT INTO integration.if_interface_version(interface_id,version,snapshot) VALUES('IF-USER-001',1,'{"interfaceId": "IF-USER-001", "interfaceName": "사용자 정보 연동", "description": "사용자 필드를 직원 전문으로 변환하는 가상 인터페이스", "sourceSystem": "SYSTEM_A", "targetSystem": "SYSTEM_B", "method": "POST", "path": "/api/receive", "enabled": true, "timeoutMs": 3000, "retryMaxAttempts": 1, "version": 1, "mappings": [{"source": "userId", "target": "employee_id", "transform": "COPY", "required": true, "nullPolicy": "OMIT", "defaultValue": null, "sourceFormat": null, "targetFormat": null}, {"source": "userName", "target": "employee_name", "transform": "COPY", "required": true, "nullPolicy": "OMIT", "defaultValue": null, "sourceFormat": null, "targetFormat": null}, {"source": "departmentCode", "target": "dept_code", "transform": "COPY", "required": false, "nullPolicy": "OMIT", "defaultValue": null, "sourceFormat": null, "targetFormat": null}, {"source": "email", "target": "email", "transform": "COPY", "required": false, "nullPolicy": "OMIT", "defaultValue": null, "sourceFormat": null, "targetFormat": null}], "validations": [{"field": "userId", "stage": "SOURCE", "type": "STRING", "required": true, "nullable": false, "minLength": 1, "maxLength": 120, "minValue": null, "maxValue": null, "enumValues": null, "dateFormat": null, "pattern": null}, {"field": "userName", "stage": "SOURCE", "type": "STRING", "required": true, "nullable": false, "minLength": 1, "maxLength": 120, "minValue": null, "maxValue": null, "enumValues": null, "dateFormat": null, "pattern": null}, {"field": "departmentCode", "stage": "SOURCE", "type": "STRING", "required": false, "nullable": false, "minLength": 1, "maxLength": 120, "minValue": null, "maxValue": null, "enumValues": null, "dateFormat": null, "pattern": null}, {"field": "email", "stage": "SOURCE", "type": "STRING", "required": false, "nullable": false, "minLength": 1, "maxLength": 120, "minValue": null, "maxValue": null, "enumValues": null, "dateFormat": null, "pattern": null}, {"field": "employee_id", "stage": "TARGET", "type": "STRING", "required": true, "nullable": false, "minLength": 1, "maxLength": 120, "minValue": null, "maxValue": null, "enumValues": null, "dateFormat": null, "pattern": null}, {"field": "employee_name", "stage": "TARGET", "type": "STRING", "required": true, "nullable": false, "minLength": 1, "maxLength": 120, "minValue": null, "maxValue": null, "enumValues": null, "dateFormat": null, "pattern": null}]}'::jsonb);
INSERT INTO integration.if_field_mapping(interface_id,version,position,rule)
 SELECT interface_id,version,(item.ordinality-1)::integer,item.value FROM integration.if_interface_version,
 LATERAL jsonb_array_elements(snapshot->'mappings') WITH ORDINALITY item(value,ordinality);
INSERT INTO integration.if_validation_rule(interface_id,version,position,rule)
 SELECT interface_id,version,(item.ordinality-1)::integer,item.value FROM integration.if_interface_version,
 LATERAL jsonb_array_elements(snapshot->'validations') WITH ORDINALITY item(value,ordinality);
ALTER TABLE integration.if_request DROP CONSTRAINT if_request_status_check;
ALTER TABLE integration.if_request ALTER COLUMN attempt_id DROP NOT NULL;
ALTER TABLE integration.if_request ADD CONSTRAINT if_request_status_check CHECK(status IN
 ('VALIDATING','VALIDATION_FAILED','MAPPING_FAILED','READY','SENDING','SUCCESS','FAILED','UNKNOWN'));
ALTER TABLE integration.if_request
 ADD COLUMN source_system varchar(80),ADD COLUMN interface_version integer,
 ADD COLUMN original_payload jsonb,ADD COLUMN mapped_payload jsonb,ADD COLUMN response_payload jsonb,
 ADD COLUMN validation_errors jsonb NOT NULL DEFAULT '[]'::jsonb,
 ADD COLUMN processing_stage varchar(40),ADD COLUMN duration_ms bigint,
 ADD FOREIGN KEY(interface_id,interface_version) REFERENCES integration.if_interface_version(interface_id,version);
CREATE INDEX ix_request_interface_created ON integration.if_request(interface_id,created_at DESC);
CREATE INDEX ix_request_status_created ON integration.if_request(status,created_at DESC);
