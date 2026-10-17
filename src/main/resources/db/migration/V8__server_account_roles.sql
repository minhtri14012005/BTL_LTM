-- Existing accounts remain ordinary users. Host stays a Room permission.
ALTER TABLE app_user
    ADD COLUMN role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT ck_user_role CHECK (role IN ('USER', 'ADMIN'));
