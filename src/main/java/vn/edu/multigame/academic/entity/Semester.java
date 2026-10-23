package vn.edu.multigame.academic.entity;

import jakarta.persistence.*;
import vn.edu.multigame.common.util.IdentityEntity;

@Entity @Table(name="academic_semester")
public class Semester extends IdentityEntity {
    @Column(name="academic_year_id", nullable=false)
    private Long academicYearId;
    public Long getAcademicYearId() { return academicYearId; }
    public void setAcademicYearId(Long value) { academicYearId=value; }
    @Column(name="code", nullable=false, length=40)
    private String code;
    public String getCode() { return code; }
    public void setCode(String value) { code=value; }
    @Column(name="name", nullable=false, length=120)
    private String name;
    public String getName() { return name; }
    public void setName(String value) { name=value; }
}
