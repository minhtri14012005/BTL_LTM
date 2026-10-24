package vn.edu.multigame.room.dto.response;

public record ClassRoomView(long id,long courseClassId,long subjectId,long academicYearId,String joinCode,boolean hasTeacher) {}
