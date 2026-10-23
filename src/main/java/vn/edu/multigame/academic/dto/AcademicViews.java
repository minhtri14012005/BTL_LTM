package vn.edu.multigame.academic.dto;

import vn.edu.multigame.academic.entity.*;

public final class AcademicViews {
    private AcademicViews() {}
    public record Year(long id,String code,String name) {
        public static Year from(AcademicYear year) {return new Year(year.getId(),year.getCode(),year.getName());}
    }
    public record Semester(long id,long academicYearId,String code,String name) {
        public static Semester from(vn.edu.multigame.academic.entity.Semester semester) {
            return new Semester(semester.getId(),semester.getAcademicYearId(),semester.getCode(),semester.getName());
        }
    }
    public record Subject(long id,String code,String name) {
        public static Subject from(vn.edu.multigame.academic.entity.Subject subject) {return new Subject(subject.getId(),subject.getCode(),subject.getName());}
    }
    public record Offering(long id,long semesterId,long subjectId) {
        public static Offering from(SubjectOffering offering) {return new Offering(offering.getId(),offering.getSemesterId(),offering.getSubjectId());}
    }
}
