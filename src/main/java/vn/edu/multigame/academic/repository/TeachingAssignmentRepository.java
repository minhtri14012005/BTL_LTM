package vn.edu.multigame.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.multigame.academic.entity.TeachingAssignment;

public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment,Long> {
    java.util.List<TeachingAssignment> findByCourseClassIdOrderByTeacherId(long courseClassId);
    boolean existsByCourseClassIdAndTeacherId(long courseClassId,long teacherId);
}
