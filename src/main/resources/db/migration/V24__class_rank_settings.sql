CREATE TABLE class_rank_settings (
 course_class_id BIGINT PRIMARY KEY,revision BIGINT NOT NULL DEFAULT 0,ranges_json JSON NOT NULL,
 FOREIGN KEY(course_class_id) REFERENCES course_class(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE class_rank_share (
 code CHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,ranges_json JSON NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
