package vn.edu.quiz.quiz.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.quiz.auth.dto.request.RegisterRequest;
import vn.edu.quiz.auth.security.AuthPrincipal;
import vn.edu.quiz.auth.service.AuthService;
import vn.edu.quiz.quiz.dto.request.*;
import vn.edu.quiz.quiz.enums.*;
import vn.edu.quiz.quiz.repository.*;
import vn.edu.quiz.user.entity.UserAccount;
import vn.edu.quiz.user.repository.UserRepository;

/** Explicit opt-in demo bootstrap; never overwrites accounts/content or prints passwords. */
@Component
@Profile("mysql & sample-data")
public class SampleDataSeeder implements ApplicationRunner {
    public static final String TITLE = "Demo Quiz - Java Basics";
    private final UserRepository users;
    private final QuizRepository quizzes;
    private final QuestionRepository questions;
    private final AuthService accounts;
    private final QuizService content;
    private final PasswordEncoder passwords;
    private final String password;
    public SampleDataSeeder(UserRepository users, QuizRepository quizzes, QuestionRepository questions,
            AuthService accounts, QuizService content, PasswordEncoder passwords,
            @Value("${quiz.sample.password:}") String password) {
        this.users = users; this.quizzes = quizzes; this.questions = questions;
        this.accounts = accounts; this.content = content; this.passwords = passwords; this.password = password;
    }
    @Override @Transactional public void run(ApplicationArguments arguments) {
        if (password.length() < 8 || password.length() > 72) throw new IllegalStateException("Set QUIZ_DEMO_PASSWORD (8-72 characters) before sample-data startup");
        AuthService.validatePassword(password);
        UserAccount author = account("demo_author", "Demo Author");
        for (int i = 1; i <= 3; i++) account("demo_player" + i, "Demo Player " + i);
        var existing = quizzes.findByOwnerUserIdAndDeletedAtMsIsNull(author.getId()).stream().filter(q -> q.getTitle().equals(TITLE)).findFirst();
        if (existing.isPresent()) {
            if (existing.get().getVisibility() != Visibility.PUBLIC || questions.countByQuizIdAndDeletedAtMsIsNull(existing.get().getId()) != 10) {
                throw new IllegalStateException("Demo Quiz was modified; bootstrap will not overwrite it");
            }
            return;
        }
        AuthPrincipal principal = new AuthPrincipal(author); principal.eraseCredentials();
        var identity = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        content.create(identity, sampleQuiz());
    }
    private UserAccount account(String username, String displayName) {
        var found = users.findByUsername(username);
        if (found.isPresent()) {
            UserAccount user = found.get();
            if (user.getDeletedAtMs() != null || !user.getDisplayName().equals(displayName) || !passwords.matches(password, user.getPasswordHash())) {
                throw new IllegalStateException("Demo username already used; bootstrap will not overwrite the account");
            }
            return user;
        }
        long id = accounts.register(new RegisterRequest(username, displayName, password)).id();
        return users.findById(id).orElseThrow();
    }
    public static CreateQuizRequest sampleQuiz() {
        return new CreateQuizRequest(TITLE, Visibility.PUBLIC, List.of(
            question("Kiểu nào là primitive trong Java?", "int", "String", "List", "Integer", Option.A),
            question("JVM là viết tắt của gì?", "Java Variable Map", "Java Virtual Machine", "Joint Value Method", "Java Version Model", Option.B),
            question("Đặc tính của String trong Java?", "Luôn thay đổi tại chỗ", "Không có Unicode", "Immutable", "Chỉ chứa số", Option.C),
            question("Từ khóa dùng để nhập tên lớp từ package khác?", "include", "using", "require", "import", Option.D),
            question("Interface biểu diễn danh sách có thứ tự?", "Set", "List", "Map", "Runnable", Option.B),
            question("Collection ánh xạ key sang value?", "HashMap", "ArrayList", "HashSet", "ArrayDeque", Option.A),
            question("Phương thức thường dùng so sánh nội dung String?", "length", "hashCode", "equals", "substring", Option.C),
            question("Từ khóa cho lớp kế thừa lớp khác?", "implements", "inherits", "instanceof", "extends", Option.D),
            question("Cấu trúc dùng xử lý exception?", "switch/case", "try/catch", "for/each", "if/else", Option.B),
            question("Thành viên thuộc lớp thay vì từng instance dùng từ khóa nào?", "static", "volatile", "transient", "abstract", Option.A)
        ));
    }
    private static QuestionRequest question(String text, String a, String b, String c, String d, Option correct) {
        return new QuestionRequest(text, Map.of(Option.A,a,Option.B,b,Option.C,c,Option.D,d), correct, null);
    }
}
