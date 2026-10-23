package vn.edu.multigame.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.multigame.academic.entity.Subject;

public interface SubjectRepository extends JpaRepository<Subject,Long> {
}
