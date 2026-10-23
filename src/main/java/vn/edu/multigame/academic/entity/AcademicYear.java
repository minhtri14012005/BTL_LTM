package vn.edu.multigame.academic.entity;

import jakarta.persistence.*;
import vn.edu.multigame.common.util.IdentityEntity;

@Entity @Table(name="academic_year")
public class AcademicYear extends IdentityEntity {
    @Column(name="code", nullable=false, length=40)
    private String code;
    public String getCode() { return code; }
    public void setCode(String value) { code=value; }
    @Column(name="name", nullable=false, length=120)
    private String name;
    public String getName() { return name; }
    public void setName(String value) { name=value; }
}
