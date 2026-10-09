CREATE TABLE password_reset (
 member_id BIGINT NOT NULL PRIMARY KEY,
 token_hash NVARCHAR(64) NOT NULL,
 requested_at DATETIME2 NOT NULL,
 expires_at DATETIME2 NOT NULL,
 CONSTRAINT uq_password_reset_hash UNIQUE(token_hash),
 CONSTRAINT fk_password_reset_member FOREIGN KEY(member_id) REFERENCES member(id) ON DELETE CASCADE
);
