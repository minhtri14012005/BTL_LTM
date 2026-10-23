package vn.edu.multigame.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.multigame.academic.entity.CourseClass;

public interface CourseClassRepository extends JpaRepository<CourseClass,Long> {
    @org.springframework.data.jpa.repository.Query("select c from CourseClass c where c.id in (select a.courseClassId from TeachingAssignment a where a.teacherId=:teacher) order by c.id")
    java.util.List<CourseClass> findAssigned(@org.springframework.data.repository.query.Param("teacher") long teacher);
}
