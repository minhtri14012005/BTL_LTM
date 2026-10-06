package vn.edu.quiz.common.config;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Frozen ownership/pure-engine boundaries without adding an architecture framework. */
class CodebaseStructureTest {
    private static final Set<String> FEATURES=Set.of("auth","common","game","quiz","realtime","room","system","user");
    private List<Path> javaFiles(String root) throws Exception {
        try(var files=Files.walk(Path.of(root))) {return files.filter(p -> p.toString().endsWith(".java")).toList();}
    }
    private void matchingPackage(String root) throws Exception {
        var base=Path.of(root);
        for(var file:javaFiles(root)) {
            var relative=base.relativize(file);var source=Files.readString(file);
            var packageName="vn.edu.quiz"+(relative.getParent()==null?"":"."+relative.getParent().toString().replace('\\','.').replace('/','.'));
            var declaration=Pattern.compile("(?m)^package\\s+([^;]+);").matcher(source);
            assertThat(declaration.find()).as(file.toString()).isTrue();assertThat(declaration.group(1)).isEqualTo(packageName);
            if(relative.getNameCount()==1)assertThat(file.getFileName().toString()).isEqualTo("QuizApplication.java");
            else assertThat(FEATURES).as(file.toString()).contains(relative.getName(0).toString());
        }
    }
    @Test void productionClassesStayInOwningFeaturesAndNoGlobalBusinessLayersReturn() throws Exception {
        matchingPackage("src/main/java/vn/edu/quiz");
        for(var feature:List.of("entity","repository","enums","service","controller","dto"))
            assertThat(Path.of("src/main/java/vn/edu/quiz",feature)).doesNotExist();
    }
    @Test void javaTestsMirrorFeaturesInsteadOfMixingInfrastructureIntoEngine() throws Exception {
        matchingPackage("src/test/java/vn/edu/quiz");
        assertThat(javaFiles("src/test/java/vn/edu/quiz/game/engine")).allSatisfy(file ->
                assertThat(Files.readString(file)).doesNotContain("vn.edu.quiz.realtime", "org.springframework", "jakarta.persistence"));
    }
    @Test void scoringEngineRemainsPureAndCannotReadPersistenceNetworkOrClock() throws Exception {
        for(var file:javaFiles("src/main/java/vn/edu/quiz/game/engine")) {
            var source=Files.readString(file);var imports=Pattern.compile("(?m)^import\\s+(?:static\\s+)?([^;]+);").matcher(source);
            while(imports.find()) {
                var dependency=imports.group(1);
                assertThat(dependency.startsWith("java.util.") || dependency.startsWith("java.lang.")
                        || dependency.equals("vn.edu.quiz.game.dto.GameplayRulesSnapshot")
                        || dependency.startsWith("vn.edu.quiz.game.enums.")).as(file+": "+dependency).isTrue();
            }
            assertThat(source).doesNotContain("System.currentTimeMillis(","System.nanoTime(","Instant.now(","Random(","@Service","@Component");
        }
    }
}
