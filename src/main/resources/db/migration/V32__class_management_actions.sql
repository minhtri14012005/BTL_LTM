-- Preserve the exact membership state before a subject/year removal.
ALTER TABLE class_membership ADD COLUMN removed_from_status VARCHAR(16) NULL,
 ADD CONSTRAINT ck_removed_membership_origin CHECK(removed_from_status IS NULL OR
  (status='REMOVED' AND removed_from_status IN ('APPROVED','PENDING','REJECTED')));

-- A batch receipt is independent of an individual membership (including empty batches).
CREATE TABLE class_membership_batch (
 actor_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 course_class_id BIGINT NOT NULL,
 fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result_json JSON NOT NULL,
 created_at_ms BIGINT NOT NULL,
 PRIMARY KEY(actor_id,request_id),
 CONSTRAINT fk_batch_actor FOREIGN KEY(actor_id) REFERENCES app_user(id),
 CONSTRAINT fk_batch_class FOREIGN KEY(course_class_id) REFERENCES course_class(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
