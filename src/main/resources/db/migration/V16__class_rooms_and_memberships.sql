CREATE TABLE class_room (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 course_class_id BIGINT NOT NULL,
 join_code CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 CONSTRAINT uq_class_room UNIQUE(course_class_id),
 CONSTRAINT uq_class_join_code UNIQUE(join_code),
 CONSTRAINT fk_class_room_class FOREIGN KEY(course_class_id) REFERENCES course_class(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Existing classes receive permanent rooms without changing legacy Room/history.
INSERT INTO class_room(course_class_id,join_code) SELECT id,HEX(RANDOM_BYTES(16)) FROM course_class;

CREATE TABLE class_membership (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 class_room_id BIGINT NOT NULL,
 student_id BIGINT NOT NULL,
 status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 requested_at_ms BIGINT NOT NULL,
 reviewed_at_ms BIGINT NULL,
 reviewer_id BIGINT NULL,
 CONSTRAINT uq_class_student UNIQUE(class_room_id,student_id),
 CONSTRAINT fk_class_member_room FOREIGN KEY(class_room_id) REFERENCES class_room(id),
 CONSTRAINT fk_class_member_student FOREIGN KEY(student_id) REFERENCES app_user(id),
 CONSTRAINT fk_class_member_reviewer FOREIGN KEY(reviewer_id) REFERENCES app_user(id),
 CONSTRAINT ck_class_membership CHECK(status IN ('PENDING','APPROVED','REJECTED','REMOVED') AND revision>=0
   AND requested_at_ms>=0 AND ((reviewed_at_ms IS NULL AND reviewer_id IS NULL) OR
     (reviewed_at_ms>=requested_at_ms AND reviewer_id IS NOT NULL))),
 INDEX ix_student_classes(student_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE subject_year_exclusion (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 student_id BIGINT NOT NULL,
 subject_id BIGINT NOT NULL,
 academic_year_id BIGINT NOT NULL,
 source_class_id BIGINT NOT NULL,
 teacher_id BIGINT NOT NULL,
 removed_at_ms BIGINT NOT NULL,
 CONSTRAINT uq_subject_year_exclusion UNIQUE(student_id,subject_id,academic_year_id),
 CONSTRAINT fk_exclusion_student FOREIGN KEY(student_id) REFERENCES app_user(id),
 CONSTRAINT fk_exclusion_subject FOREIGN KEY(subject_id) REFERENCES academic_subject(id),
 CONSTRAINT fk_exclusion_year FOREIGN KEY(academic_year_id) REFERENCES academic_year(id),
 CONSTRAINT fk_exclusion_class FOREIGN KEY(source_class_id) REFERENCES course_class(id),
 CONSTRAINT fk_exclusion_teacher FOREIGN KEY(teacher_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE class_membership_action (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 command VARCHAR(16) NOT NULL,
 membership_id BIGINT NOT NULL,
 result_status VARCHAR(16) NOT NULL,
 result_revision BIGINT NOT NULL,
 created_at_ms BIGINT NOT NULL,
 CONSTRAINT uq_class_member_request UNIQUE(actor_id,request_id),
 CONSTRAINT fk_class_action_actor FOREIGN KEY(actor_id) REFERENCES app_user(id),
 CONSTRAINT fk_class_action_member FOREIGN KEY(membership_id) REFERENCES class_membership(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
