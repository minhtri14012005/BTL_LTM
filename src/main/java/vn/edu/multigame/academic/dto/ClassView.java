package vn.edu.multigame.academic.dto;

import java.util.List;

public record ClassView(long id,long offeringId,long academicYearId,long semesterId,long subjectId,
        String code,String name,int requiredLessons,List<Long> teacherIds) {}
