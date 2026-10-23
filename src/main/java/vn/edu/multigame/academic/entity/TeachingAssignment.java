package vn.edu.multigame.academic.entity;

import jakarta.persistence.*;
import vn.edu.multigame.common.util.IdentityEntity;

@Entity @Table(name="teaching_assignment")
public class TeachingAssignment extends IdentityEntity {
    @Column(name="course_class_id", nullable=false)
    private Long courseClassId;
    public Long getCourseClassId() { return courseClassId; }
    public void setCourseClassId(Long value) { courseClassId=value; }
    @Column(name="teacher_id", nullable=false)
    private Long teacherId;
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long value) { teacherId=value; }
}
