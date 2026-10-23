package vn.edu.multigame.academic.entity;

import jakarta.persistence.*;
import vn.edu.multigame.common.util.IdentityEntity;

@Entity @Table(name="course_class")
public class CourseClass extends IdentityEntity {
    @Column(name="offering_id", nullable=false)
    private Long offeringId;
    public Long getOfferingId() { return offeringId; }
    public void setOfferingId(Long value) { offeringId=value; }
    @Column(name="code", nullable=false, length=40)
    private String code;
    public String getCode() { return code; }
    public void setCode(String value) { code=value; }
    @Column(name="name", nullable=false, length=120)
    private String name;
    public String getName() { return name; }
    public void setName(String value) { name=value; }
    @Column(name="required_lessons", nullable=false)
    private Integer requiredLessons;
    public Integer getRequiredLessons() { return requiredLessons; }
    public void setRequiredLessons(Integer value) { requiredLessons=value; }
}
