CREATE TABLE auth_session (
    id NVARCHAR(36) NOT NULL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    token_hash NVARCHAR(64) NOT NULL,
    previous_hash NVARCHAR(64) NULL,
    expires_at DATETIME2 NOT NULL,
    revoked BIT NOT NULL DEFAULT 0,
    CONSTRAINT fk_auth_session_member FOREIGN KEY (member_id) REFERENCES member(id)
);
CREATE INDEX ix_auth_session_member ON auth_session(member_id);
CREATE INDEX ix_auth_session_expiry ON auth_session(expires_at);
