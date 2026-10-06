package vn.edu.quiz.user.repository;

import vn.edu.quiz.user.entity.UserAccount;

import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<UserAccount> findByUsername(String username);
}
