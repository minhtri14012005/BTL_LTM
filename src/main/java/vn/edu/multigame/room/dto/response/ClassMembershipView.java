package vn.edu.multigame.room.dto.response;

public record ClassMembershipView(long id,long classRoomId,long courseClassId,long studentId,
        String hoTen,String maSinhVien,String status,long revision,long requestedAtMs,Long reviewedAtMs,Long reviewerId,
        String classCode,String className,String subjectName,String academicYearName,int requiredLessons,long subjectId,long academicYearId) {}
