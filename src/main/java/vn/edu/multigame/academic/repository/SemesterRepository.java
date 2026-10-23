package vn.edu.multigame.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.multigame.academic.entity.Semester;

public interface SemesterRepository extends JpaRepository<Semester,Long> {
}
