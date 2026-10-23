CREATE TABLE academic_year (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(40) NOT NULL,
 name VARCHAR(120) NOT NULL,
 CONSTRAINT uq_academic_year_code UNIQUE(code),
 CONSTRAINT ck_academic_year CHECK(CHAR_LENGTH(TRIM(code))>0 AND CHAR_LENGTH(TRIM(name))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE academic_semester (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 academic_year_id BIGINT NOT NULL,
 code VARCHAR(40) NOT NULL,
 name VARCHAR(120) NOT NULL,
 CONSTRAINT fk_semester_year FOREIGN KEY(academic_year_id) REFERENCES academic_year(id),
 CONSTRAINT uq_semester_code UNIQUE(academic_year_id,code),
 CONSTRAINT ck_semester_name CHECK(CHAR_LENGTH(TRIM(code))>0 AND CHAR_LENGTH(TRIM(name))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE academic_subject (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(40) NOT NULL,
 name VARCHAR(200) NOT NULL,
 CONSTRAINT uq_subject_code UNIQUE(code),
 CONSTRAINT ck_subject_name CHECK(CHAR_LENGTH(TRIM(code))>0 AND CHAR_LENGTH(TRIM(name))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE subject_offering (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 semester_id BIGINT NOT NULL,
 subject_id BIGINT NOT NULL,
 CONSTRAINT fk_offering_semester FOREIGN KEY(semester_id) REFERENCES academic_semester(id),
 CONSTRAINT fk_offering_subject FOREIGN KEY(subject_id) REFERENCES academic_subject(id),
 CONSTRAINT uq_subject_offering UNIQUE(semester_id,subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE course_class (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 offering_id BIGINT NOT NULL,
 code VARCHAR(40) NOT NULL,
 name VARCHAR(120) NOT NULL,
 required_lessons INT NOT NULL,
 CONSTRAINT fk_class_offering FOREIGN KEY(offering_id) REFERENCES subject_offering(id),
 CONSTRAINT uq_class_code UNIQUE(offering_id,code),
 CONSTRAINT ck_class_values CHECK(required_lessons>0 AND CHAR_LENGTH(TRIM(code))>0 AND CHAR_LENGTH(TRIM(name))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE teaching_assignment (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 course_class_id BIGINT NOT NULL,
 teacher_id BIGINT NOT NULL,
 CONSTRAINT fk_assignment_class FOREIGN KEY(course_class_id) REFERENCES course_class(id),
 CONSTRAINT fk_assignment_teacher FOREIGN KEY(teacher_id) REFERENCES app_user(id),
 CONSTRAINT uq_teaching_assignment UNIQUE(course_class_id,teacher_id),
 INDEX ix_teaching_teacher(teacher_id,course_class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
