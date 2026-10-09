ALTER TABLE member ADD status_version BIGINT NOT NULL CONSTRAINT df_member_status_version DEFAULT 0;
CREATE TABLE member_status_audit (
 id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
 member_id BIGINT NOT NULL,
 actor_member_id BIGINT NOT NULL,
 from_status NVARCHAR(20) NOT NULL,
 to_status NVARCHAR(20) NOT NULL,
 status_version BIGINT NOT NULL,
 reason NVARCHAR(200) NOT NULL,
 created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT ck_member_audit_status CHECK (from_status IN ('ACTIVE','DISABLED') AND to_status IN ('ACTIVE','DISABLED') AND from_status<>to_status),
 CONSTRAINT ck_member_audit_version CHECK (status_version>0)
);
-- Historical identifiers intentionally have no cascading FK; audit entries cannot be edited via API.
CREATE INDEX ix_member_status_audit_member ON member_status_audit(member_id,id DESC);
