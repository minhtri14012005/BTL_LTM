ALTER TABLE app_user DROP CHECK ck_user_role;
UPDATE app_user SET role='SINH_VIEN' WHERE role='USER';
ALTER TABLE app_user
 ALTER COLUMN role SET DEFAULT 'SINH_VIEN',
 ADD COLUMN ma_sinh_vien VARCHAR(32) NULL,
 ADD COLUMN account_status VARCHAR(16) GENERATED ALWAYS AS
   (CASE WHEN ban_kind='INDEFINITE' THEN 'EXPELLED' ELSE 'ACTIVE' END) STORED,
 ADD CONSTRAINT uq_student_code UNIQUE(ma_sinh_vien),
 ADD CONSTRAINT ck_student_code CHECK(ma_sinh_vien IS NULL OR
   (role='SINH_VIEN' AND REGEXP_LIKE(ma_sinh_vien,'^[A-Z0-9][A-Z0-9_-]{0,31}$','c'))),
 ADD CONSTRAINT ck_user_role CHECK(role IN ('ADMIN','GIANG_VIEN','SINH_VIEN'));
-- Existing student accounts keep their name/ID/history and complete their actual
-- student code through the authenticated profile endpoint. Never invent an ID.
