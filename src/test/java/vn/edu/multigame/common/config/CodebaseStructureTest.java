package vn.edu.multigame.common.config;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Frozen ownership/pure-engine boundaries without adding an architecture framework. */
class CodebaseStructureTest {
    private static final Set<String> FEATURES=Set.of("lesson","academic","achievements","leaderboard","admin","auth","common","questionbank","quiz","realtime","room","system","user");
    private List<Path> javaFiles(String root) throws Exception {
        try(var files=Files.walk(Path.of(root))) {return files.filter(p -> p.toString().endsWith(".java")).toList();}
    }
    private void matchingPackage(String root) throws Exception {
        var base=Path.of(root);
        for(var file:javaFiles(root)) {
            var relative=base.relativize(file);var source=Files.readString(file);
            var packageName="vn.edu.multigame"+(relative.getParent()==null?"":"."+relative.getParent().toString().replace('\\','.').replace('/','.'));
            var declaration=Pattern.compile("(?m)^package\\s+([^;]+);").matcher(source);
            assertThat(declaration.find()).as(file.toString()).isTrue();assertThat(declaration.group(1)).isEqualTo(packageName);
            if(relative.getNameCount()==1)assertThat(file.getFileName().toString()).isEqualTo("MultigameApplication.java");
            else assertThat(FEATURES).as(file.toString()).contains(relative.getName(0).toString());
        }
    }
    @Test void productionClassesStayInOwningFeaturesAndNoGlobalBusinessLayersReturn() throws Exception {
        matchingPackage("src/main/java/vn/edu/multigame");
        assertThat(Path.of("src/main/java/vn/edu/quiz")).doesNotExist();
        for(var feature:List.of("entity","repository","enums","service","controller","dto"))
            assertThat(Path.of("src/main/java/vn/edu/multigame",feature)).doesNotExist();
    }
    @Test void javaTestsMirrorFeaturesInsteadOfMixingInfrastructureIntoEngine() throws Exception {
        matchingPackage("src/test/java/vn/edu/multigame");
        assertThat(Path.of("src/test/java/vn/edu/quiz")).doesNotExist();

    }
    @Test void lessonScoringRemainsPure() throws Exception {
        for(String name:List.of("QuizScoring.java","FillBlankMatcher.java")) {
            var source=Files.readString(Path.of("src/main/java/vn/edu/multigame/lesson/service",name));
            assertThat(source).doesNotContain("org.springframework", "java.sql", "JdbcTemplate");
        }
        for(var file:javaFiles("src/main/java")) {
            var source=Files.readString(file);
            assertThat(source).as(file.toString()).doesNotContain("vn.edu.multigame.game.");
        }
    }
}
