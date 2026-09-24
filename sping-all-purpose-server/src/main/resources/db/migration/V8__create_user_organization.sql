-- Memberships are assigned explicitly; existing accounts are not automatically
-- granted access to any organization.
CREATE TABLE user_organization (
    user_id         BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, organization_id),
    CONSTRAINT fk_user_organization_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_organization_organization FOREIGN KEY (organization_id)
        REFERENCES organization (id) ON DELETE CASCADE
);

-- The primary key already indexes membership lookups by user.
CREATE INDEX ix_user_organization_organization ON user_organization (organization_id);
