package vn.edu.multigame.academic.entity;

import jakarta.persistence.*;
import vn.edu.multigame.common.util.IdentityEntity;

@Entity @Table(name="subject_offering")
public class SubjectOffering extends IdentityEntity {
    @Column(name="semester_id", nullable=false)
    private Long semesterId;
    public Long getSemesterId() { return semesterId; }
    public void setSemesterId(Long value) { semesterId=value; }
    @Column(name="subject_id", nullable=false)
    private Long subjectId;
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long value) { subjectId=value; }
}
