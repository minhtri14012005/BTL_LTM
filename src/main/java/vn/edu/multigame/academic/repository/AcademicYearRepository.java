package vn.edu.multigame.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.multigame.academic.entity.AcademicYear;

public interface AcademicYearRepository extends JpaRepository<AcademicYear,Long> {
}
