package vn.edu.multigame.questionbank.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.multigame.auth.service.AuthService;
import vn.edu.multigame.questionbank.repository.*;
import vn.edu.multigame.user.repository.UserRepository;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000")
@ActiveProfiles("mysql")
@Transactional
class SampleDataIT {
    @Autowired UserRepository users;
    @Autowired QuizRepository quizzes;
    @Autowired QuestionRepository questions;
    @Autowired AuthService accounts;
    @Autowired QuestionBankService content;
    @Autowired PasswordEncoder passwords;
    @Test void demoIsIdempotentAndUsesDifferentAuthorAndThreePlayers() {
        var seed=new SampleDataSeeder(users,quizzes,questions,accounts,content,passwords,"Test-demo-42");
        seed.run(null);seed.run(null);
        var author=users.findByUsername("demo_author").orElseThrow();
        for(int i=1;i<=3;i++) {
            var player=users.findByUsername("demo_player"+i).orElseThrow();
            assertThat(player.getId()).isNotEqualTo(author.getId());assertThat(passwords.matches("Test-demo-42",player.getPasswordHash())).isTrue();
        }
        var owned=quizzes.findByOwnerUserIdAndDeletedAtMsIsNull(author.getId());assertThat(owned).hasSize(1);
        assertThat(questions.countByQuizIdAndDeletedAtMsIsNull(owned.getFirst().getId())).isEqualTo(10);
    }
}
